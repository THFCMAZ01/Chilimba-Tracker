package zm.zut;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    // LinkedHashMap preserves registration order for the rotating payout strategy.
    private static final Map<String, User> members = new LinkedHashMap<>();
    private static final SavingsGroup savingsGroup = new SavingsGroup(new RotationPayout());

    public static class NewUserRequest {
        public String id;
        public String name;
        public String phone;
        public String role;
    }

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));
        });

        app.exception(MissedContributionException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", e.getMessage()))
        );
        app.exception(IllegalArgumentException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", e.getMessage()))
        );
        app.exception(IllegalStateException.class, (e, ctx) ->
            ctx.status(409).json(Map.of("error", e.getMessage()))
        );

        app.get("/api/status", ctx -> ctx.result("Chilimba Tracker API Running"));

        app.get("/members", ctx -> ctx.json(members.values()));

        app.post("/members", ctx -> {
            NewUserRequest req = ctx.bodyAsClass(NewUserRequest.class);
            if (req.id == null || req.id.isBlank()
                    || req.name == null || req.name.isBlank()
                    || req.phone == null || req.phone.isBlank()
                    || req.role == null || req.role.isBlank()) {
                throw new IllegalArgumentException("Member ID, name, phone and role are required.");
            }
            if (members.containsKey(req.id)) {
                throw new IllegalArgumentException("A member with this ID is already registered.");
            }

            User newUser;
            if ("Treasurer".equalsIgnoreCase(req.role)) {
                newUser = new Treasurer(req.id.trim(), req.name.trim(), req.phone.trim(), LocalDate.now());
            } else if ("OrdinaryMember".equalsIgnoreCase(req.role)
                    || "Ordinary Member".equalsIgnoreCase(req.role)) {
                newUser = new OrdinaryMember(req.id.trim(), req.name.trim(), req.phone.trim(), LocalDate.now());
            } else {
                throw new IllegalArgumentException("Role must be Treasurer or OrdinaryMember.");
            }

            members.put(newUser.getId(), newUser);
            ctx.status(201).json(newUser);
        });

        app.get("/contributions", ctx -> ctx.json(savingsGroup.getContributions()));

        app.post("/contributions", ctx -> {
            Contribution payload = ctx.bodyAsClass(Contribution.class);
            LocalDate date = payload.getDate() != null ? payload.getDate() : LocalDate.now();
            Contribution newContribution =
                new Contribution(payload.getMemberId(), payload.getAmount(), date);

            savingsGroup.recordContribution(newContribution, members);
            ctx.status(201).json(newContribution);
        });

        app.get("/payouts", ctx -> ctx.json(savingsGroup.getPayouts()));

        app.post("/payouts", ctx -> {
            Payout payout = savingsGroup.triggerPayout(members);
            ctx.status(201).json(payout);
        });

        int port = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 7070;
        app.start(port);
    }
}

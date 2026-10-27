package zm.zut;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class Main {
    
    private static Map<String, User> members = new HashMap<>();
    
    // Initialize the orchestrator with the Rotation rule
    private static SavingsGroup savingsGroup = new SavingsGroup(new RotationPayout());

    public static class NewUserRequest {
        public String id;
        public String name;
        public String phone;
        public String role; 
    }

    public static void main(String[] args) {
        
        Javalin app = Javalin.create(config -> {
            // Serve static files from src/main/resources/public
            config.staticFiles.add("/public");
            
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));
        });

        // Exception Handling
        app.exception(MissedContributionException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        });
        
        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(404).json(Map.of("error", e.getMessage()));
        });

        // Endpoints
        app.get("/api/status", ctx -> ctx.result("Chilimba Tracker API Running"));

        app.get("/members", ctx -> ctx.json(members.values()));

        app.post("/members", ctx -> {
            NewUserRequest req = ctx.bodyAsClass(NewUserRequest.class);
            User newUser;
            if ("Treasurer".equalsIgnoreCase(req.role)) {
                newUser = new Treasurer(req.id, req.name, req.phone, LocalDate.now());
            } else {
                newUser = new OrdinaryMember(req.id, req.name, req.phone, LocalDate.now());
            }
            members.put(newUser.getId(), newUser);
            ctx.status(201).json(newUser);
        });

        app.get("/contributions", ctx -> ctx.json(savingsGroup.getContributions()));

        app.post("/contributions", ctx -> {
            Contribution payload = ctx.bodyAsClass(Contribution.class);
            LocalDate date = payload.getDate() != null ? payload.getDate() : LocalDate.now();
            Contribution newContribution = new Contribution(payload.getMemberId(), payload.getAmount(), date);
            
            savingsGroup.recordContribution(newContribution, members);
            ctx.status(201).json(newContribution);
        });

        app.get("/payouts", ctx -> ctx.json(savingsGroup.getPayouts()));

        app.post("/payouts", ctx -> {
            Payout payout = savingsGroup.triggerPayout(members, 5000.0);
            ctx.status(201).json(payout);
        });

        // Start server after configuring routes and static files
        app.start(7070);
    }
}
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

    public static class NewUserRequest {
        public String id;
        public String name;
        public String phone;
        public String role; 
    }

    public static void main(String[] args) {
        
        Javalin app = Javalin.create(config -> {
            // Javalin 6 requires .updateMapper() instead of a custom constructor
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));
        }).start(7070);

        app.get("/", ctx -> ctx.result("Chilimba Tracker API Running"));

        app.get("/members", ctx -> {
            ctx.json(members.values());
        });

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
    }
}
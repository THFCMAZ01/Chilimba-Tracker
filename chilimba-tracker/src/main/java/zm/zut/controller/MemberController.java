package zm.zut.controller;

import io.javalin.Javalin;
import zm.zut.MemberRepository;
import zm.zut.User;
import zm.zut.dto.NewUserRequest;

public class MemberController {

    private final MemberRepository memberRepository;

    public MemberController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public void registerRoutes(Javalin app) {

        // Retrieve all members.
        app.get("/members", ctx ->
            ctx.json(memberRepository.findAll())
        );

        // Register a new member.
        app.post("/members", ctx -> {

            NewUserRequest req =
                ctx.bodyAsClass(NewUserRequest.class);

            if (req.name == null || req.name.isBlank()
                    || req.phone == null || req.phone.isBlank()
                    || req.role == null || req.role.isBlank()) {

                throw new IllegalArgumentException(
                    "Name, phone and role are required."
                );
            }

            User newUser = memberRepository.registerMember(
                req.name,
                req.phone,
                req.role
            );

            ctx.status(201).json(newUser);
        });
    }
}

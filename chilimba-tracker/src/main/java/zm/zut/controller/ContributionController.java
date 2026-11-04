package zm.zut.controller;

import io.javalin.Javalin;
import zm.zut.Contribution;
import zm.zut.ContributionRepository;

import java.time.LocalDate;

public class ContributionController {

    private final ContributionRepository contributionRepository;

    public ContributionController(
            ContributionRepository contributionRepository) {
        this.contributionRepository = contributionRepository;
    }

    public void registerRoutes(Javalin app) {

        // Retrieve contributions for the current cycle.
        app.get("/contributions", ctx ->
            ctx.json(contributionRepository.findAllForCurrentCycle())
        );

        // Record a contribution.
        app.post("/contributions", ctx -> {

            Contribution payload =
                ctx.bodyAsClass(Contribution.class);

            LocalDate date = payload.getDate() != null
                ? payload.getDate()
                : LocalDate.now();

            Contribution savedContribution =
                contributionRepository.recordContribution(
                    payload.getMemberId(),
                    payload.getAmount(),
                    date
                );

            ctx.status(201).json(savedContribution);
        });
    }
}

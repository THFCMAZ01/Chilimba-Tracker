package zm.zut.controller;

import io.javalin.Javalin;
import zm.zut.Payout;
import zm.zut.PayoutRepository;

import java.time.LocalDate;

public class PayoutController {

    private final PayoutRepository payoutRepository;

    public PayoutController(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public void registerRoutes(Javalin app) {

        // Retrieve payout history.
        app.get("/payouts", ctx ->
            ctx.json(payoutRepository.findAll())
        );

        // Execute and persist a payout.
        app.post("/payouts", ctx -> {

            Payout payout =
                payoutRepository.executePayout(LocalDate.now());

            ctx.status(201).json(payout);
        });
    }
}

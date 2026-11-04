
package zm.zut;

import java.util.List;

public class RotationPayout implements PayoutRule {

    @Override
    public User determineNextRecipient(
            List<User> members,
            List<Payout> pastPayouts) {

        // A rotation cannot happen without registered members.
        if (members == null || members.isEmpty()) {
            return null;
        }

        // Treat missing payout history as an empty history.
        int totalPayouts = (pastPayouts == null)
                ? 0
                : pastPayouts.size();

        /*
         * The next recipient is determined by the number
         * of previous payouts.
         *
         * Members are selected in registration order.
         * Once everyone receives a payout, rotation starts again.
         */
        int nextRecipientIndex = totalPayouts % members.size();

        return members.get(nextRecipientIndex);
    }
}

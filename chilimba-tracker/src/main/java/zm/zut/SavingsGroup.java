package zm.zut;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SavingsGroup {
    private final List<Contribution> contributions = new ArrayList<>();
    private final List<Payout> payouts = new ArrayList<>();
    private final PayoutRule payoutRule;
    // Contributions before this index have already been included in a payout.
    private int paidContributionCount = 0;

    public SavingsGroup(PayoutRule payoutRule) {
        if (payoutRule == null) {
            throw new IllegalArgumentException("A payout rule is required.");
        }
        this.payoutRule = payoutRule;
    }

    public void recordContribution(Contribution contribution, Map<String, User> members) {
        if (contribution == null) {
            throw new IllegalArgumentException("Contribution is required.");
        }
        if (members == null || !members.containsKey(contribution.getMemberId())) {
            throw new IllegalArgumentException("Member ID does not exist.");
        }
        if (!Double.isFinite(contribution.getAmount()) || contribution.getAmount() <= 0) {
            throw new MissedContributionException("Contribution must be a positive, valid amount.");
        }
        if (contribution.getDate() == null) {
            throw new IllegalArgumentException("Contribution date is required.");
        }
        contributions.add(contribution);
    }

    /**
     * Pays the next member using the contributions recorded since the previous payout.
     * Every registered member must have contributed during the current cycle.
     */
    public Payout triggerPayout(Map<String, User> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalStateException("There are no members to receive a payout.");
        }

        List<Contribution> currentCycle = contributions.subList(paidContributionCount, contributions.size());
        if (currentCycle.isEmpty()) {
            throw new MissedContributionException("No contributions have been recorded for this payout cycle.");
        }

        Set<String> contributingMemberIds = new HashSet<>();
        double poolAmount = 0.0;
        for (Contribution contribution : currentCycle) {
            contributingMemberIds.add(contribution.getMemberId());
            poolAmount += contribution.getAmount();
        }

        if (!contributingMemberIds.containsAll(members.keySet())) {
            throw new MissedContributionException(
                "Every registered member must contribute before a payout can be made."
            );
        }
        if (!Double.isFinite(poolAmount) || poolAmount <= 0) {
            throw new IllegalStateException("The current contribution pool is invalid.");
        }

        List<User> memberList = new ArrayList<>(members.values());
        User nextRecipient = payoutRule.determineNextRecipient(memberList, payouts);
        if (nextRecipient == null) {
            throw new IllegalStateException("No eligible recipient found.");
        }

        Payout newPayout = new Payout(nextRecipient.getId(), poolAmount, LocalDate.now());
        payouts.add(newPayout);
        paidContributionCount = contributions.size();
        return newPayout;
    }

    // Return immutable snapshots so callers cannot bypass the business rules.
    public List<Contribution> getContributions() {
        return List.copyOf(contributions);
    }

    public List<Payout> getPayouts() {
        return List.copyOf(payouts);
    }
}

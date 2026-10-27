// SavingsGroup.java
package zm.zut;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SavingsGroup {
    private List<Contribution> contributions = new ArrayList<>();
    private List<Payout> payouts = new ArrayList<>();
    private PayoutRule payoutRule;

    public SavingsGroup(PayoutRule payoutRule) {
        this.payoutRule = payoutRule; // Injects the polymorphic rule
    }

    public void recordContribution(Contribution contribution, Map<String, User> members) {
        if (!members.containsKey(contribution.getMemberId())) {
            throw new IllegalArgumentException("Member ID does not exist.");
        }
        if (contribution.getAmount() <= 0) {
            throw new MissedContributionException("Contribution must be greater than zero.");
        }
        contributions.add(contribution);
    }

    public Payout triggerPayout(Map<String, User> members, double poolAmount) {
        List<User> memberList = new ArrayList<>(members.values());
        User nextRecipient = payoutRule.determineNextRecipient(memberList, payouts);
        
        if (nextRecipient == null) {
            throw new IllegalStateException("No eligible recipient found.");
        }
        
        Payout newPayout = new Payout(nextRecipient.getId(), poolAmount, LocalDate.now());
        payouts.add(newPayout);
        return newPayout;
    }

    public List<Contribution> getContributions() { return contributions; }
    public List<Payout> getPayouts() { return payouts; }
}
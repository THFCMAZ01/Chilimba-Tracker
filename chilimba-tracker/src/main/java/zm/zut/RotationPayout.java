// RotationPayout.java
package zm.zut;
import java.util.List;

public class RotationPayout implements PayoutRule {
    @Override
    public User determineNextRecipient(List<User> members, List<Payout> pastPayouts) {
        // Find the first member who has not received a payout yet
        for (User member : members) {
            boolean hasReceived = pastPayouts.stream()
                .anyMatch(p -> p.getMemberId().equals(member.getId()));
            
            if (!hasReceived) {
                return member;
            }
        }
        // If everyone has been paid, start over with the first member
        return members.isEmpty() ? null : members.get(0);
    }
}
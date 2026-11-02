package zm.zut;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class RotationPayout implements PayoutRule {
    @Override
    public User determineNextRecipient(List<User> members, List<Payout> pastPayouts) {
        if (members == null || members.isEmpty()) {
            return null;
        }

        Set<String> paidMemberIds = new HashSet<>();
        if (pastPayouts != null) {
            for (Payout payout : pastPayouts) {
                paidMemberIds.add(payout.getMemberId());
            }
        }

        // The supplied member list must preserve the group's agreed registration order.
        for (User member : members) {
            if (!paidMemberIds.contains(member.getId())) {
                return member;
            }
        }

        // Start a new rotation after every member has received one payout.
        return members.get(0);
    }
}

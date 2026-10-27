// PayoutRule.java
package zm.zut;
import java.util.List;

public interface PayoutRule {
    User determineNextRecipient(List<User> members, List<Payout> pastPayouts);
}
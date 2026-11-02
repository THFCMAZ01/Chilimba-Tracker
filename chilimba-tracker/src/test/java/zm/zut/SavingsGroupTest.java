package zm.zut;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class SavingsGroupTest {

    private Map<String, User> twoMembers() {
        Map<String, User> members = new LinkedHashMap<>();
        members.put("2410123", new Treasurer("2410123", "Joshua", "0979615785", LocalDate.now()));
        members.put("2410009", new OrdinaryMember("2410009", "Martin", "0970000000", LocalDate.now()));
        return members;
    }

    @Test
    public void recordsValidContribution() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        group.recordContribution(new Contribution("2410123", 1000.0, LocalDate.now()), members);

        assertEquals(1, group.getContributions().size());
        assertEquals(1000.0, group.getContributions().get(0).getAmount());
    }

    @Test
    public void rejectsContributionFromUnknownMember() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());

        assertThrows(IllegalArgumentException.class, () ->
            group.recordContribution(
                new Contribution("unknown", 1000.0, LocalDate.now()),
                twoMembers()
            )
        );
    }

    @Test
    public void rejectsZeroAndNegativeContributions() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        assertThrows(MissedContributionException.class, () ->
            group.recordContribution(new Contribution("2410123", 0.0, LocalDate.now()), members)
        );
        assertThrows(MissedContributionException.class, () ->
            group.recordContribution(new Contribution("2410123", -10.0, LocalDate.now()), members)
        );
    }

    @Test
    public void rejectsPayoutUntilEveryMemberHasContributed() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();
        group.recordContribution(new Contribution("2410123", 1000.0, LocalDate.now()), members);

        assertThrows(MissedContributionException.class, () -> group.triggerPayout(members));
    }

    @Test
    public void calculatesPayoutFromCurrentCycleAndRotatesRecipient() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        group.recordContribution(new Contribution("2410123", 1000.0, LocalDate.now()), members);
        group.recordContribution(new Contribution("2410009", 1500.0, LocalDate.now()), members);
        Payout first = group.triggerPayout(members);

        assertEquals("2410123", first.getMemberId());
        assertEquals(2500.0, first.getAmount());

        group.recordContribution(new Contribution("2410123", 500.0, LocalDate.now()), members);
        group.recordContribution(new Contribution("2410009", 500.0, LocalDate.now()), members);
        Payout second = group.triggerPayout(members);

        assertEquals("2410009", second.getMemberId());
        assertEquals(1000.0, second.getAmount());
    }

    @Test
    public void rejectsPayoutWhenThereAreNoMembers() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());

        assertThrows(IllegalStateException.class, () -> group.triggerPayout(Map.of()));
    }

    @Test
    public void returnedContributionListCannotBeModified() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        group.recordContribution(
            new Contribution("2410123", 1000.0, LocalDate.now()),
            twoMembers()
        );

        assertThrows(UnsupportedOperationException.class,
            () -> group.getContributions().clear());
    }
}

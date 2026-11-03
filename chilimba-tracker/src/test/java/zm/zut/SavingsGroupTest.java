package zm.zut;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class SavingsGroupTest {

    private Map<String, User> twoMembers() {
        Map<String, User> members = new LinkedHashMap<>();

        members.put(
            "2410123",
            new Treasurer(
                "2410123",
                "Joshua",
                "0979615785",
                LocalDate.now()
            )
        );

        members.put(
            "2410009",
            new OrdinaryMember(
                "2410009",
                "Martin",
                "0970000000",
                LocalDate.now()
            )
        );

        return members;
    }

    @Test
    public void recordsValidContribution() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertEquals(1, group.getContributions().size());
        assertEquals(
            new BigDecimal("1000.00"),
            group.getContributions().get(0).getAmount()
        );
    }

    @Test
    public void rejectsContributionFromUnknownMember() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());

        assertThrows(
            IllegalArgumentException.class,
            () -> group.recordContribution(
                new Contribution("unknown", new BigDecimal("1000.00"), LocalDate.now()),
                twoMembers()
            )
        );
    }

    @Test
    public void rejectsZeroAndNegativeContributions() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        assertThrows(
            MissedContributionException.class,
            () -> group.recordContribution(
                new Contribution("2410123", BigDecimal.ZERO, LocalDate.now()),
                members
            )
        );

        assertThrows(
            MissedContributionException.class,
            () -> group.recordContribution(
                new Contribution("2410123", new BigDecimal("-10.00"), LocalDate.now()),
                members
            )
        );
    }

    @Test
    public void rejectsPayoutUntilEveryMemberHasContributed() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertThrows(
            MissedContributionException.class,
            () -> group.triggerPayout(members)
        );
    }

    @Test
    public void calculatesPayoutFromCurrentCycleAndRotatesRecipient() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("1500.00"), LocalDate.now()),
            members
        );

        Payout first = group.triggerPayout(members);

        assertEquals("2410123", first.getMemberId());
        assertEquals(new BigDecimal("2500.00"), first.getAmount());

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("500.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("500.00"), LocalDate.now()),
            members
        );

        Payout second = group.triggerPayout(members);

        assertEquals("2410009", second.getMemberId());
        assertEquals(new BigDecimal("1000.00"), second.getAmount());
    }

    @Test
    public void rejectsPayoutWhenThereAreNoMembers() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());

        assertThrows(
            IllegalStateException.class,
            () -> group.triggerPayout(Map.of())
        );
    }

    @Test
    public void returnedContributionListCannotBeModified() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            twoMembers()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> group.getContributions().clear()
        );
    }

    @Test
    public void rotationContinuesCorrectlyAfterEveryMemberHasReceivedPayout() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = twoMembers();

        // First rotation: Joshua, then Martin.
        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertEquals(
            "2410123",
            group.triggerPayout(members).getMemberId()
        );

        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertEquals(
            "2410009",
            group.triggerPayout(members).getMemberId()
        );

        // Second rotation should start again with Joshua.
        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertEquals(
            "2410123",
            group.triggerPayout(members).getMemberId()
        );

        // The second rotation should then continue to Martin.
        group.recordContribution(
            new Contribution("2410123", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        group.recordContribution(
            new Contribution("2410009", new BigDecimal("1000.00"), LocalDate.now()),
            members
        );

        assertEquals(
            "2410009",
            group.triggerPayout(members).getMemberId()
        );
    }
}
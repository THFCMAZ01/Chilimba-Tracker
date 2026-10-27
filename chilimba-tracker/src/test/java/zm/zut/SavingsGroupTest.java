package zm.zut;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class SavingsGroupTest {

    @Test
    public void testRecordValidContribution() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = new HashMap<>();
        members.put("2410123", new Treasurer("2410123", "Joshua", "0979615785", LocalDate.now()));

        Contribution c = new Contribution("2410123", 1000.0, LocalDate.now());
        group.recordContribution(c, members);

        assertEquals(1, group.getContributions().size());
        assertEquals(1000.0, group.getContributions().get(0).getAmount());
    }

    @Test
    public void testMissedContributionThrowsException() {
        SavingsGroup group = new SavingsGroup(new RotationPayout());
        Map<String, User> members = new HashMap<>();
        members.put("2410123", new Treasurer("2410123", "Joshua", "0979615785", LocalDate.now()));

        // An amount of 0.0 triggers the MissedContributionException
        Contribution invalid = new Contribution("2410123", 0.0, LocalDate.now());

        assertThrows(MissedContributionException.class, () -> {
            group.recordContribution(invalid, members);
        });
    }
}
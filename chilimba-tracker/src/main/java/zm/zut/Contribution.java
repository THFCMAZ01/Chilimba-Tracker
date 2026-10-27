// Contribution.java
package zm.zut;
import java.time.LocalDate;

public class Contribution {
    private String memberId;
    private double amount;
    private LocalDate date;

    public Contribution() {} // Required for JSON parsing

    public Contribution(String memberId, double amount, LocalDate date) {
        this.memberId = memberId;
        this.amount = amount;
        this.date = date;
    }

    public String getMemberId() { return memberId; }
    public double getAmount() { return amount; }
    public LocalDate getDate() { return date; }
}
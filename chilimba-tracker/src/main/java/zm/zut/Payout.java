// Payout.java
package zm.zut;
import java.time.LocalDate;

public class Payout {
    private String memberId;
    private double amount;
    private LocalDate date;

    public Payout() {}

    public Payout(String memberId, double amount, LocalDate date) {
        this.memberId = memberId;
        this.amount = amount;
        this.date = date;
    }

    public String getMemberId() { return memberId; }
    public double getAmount() { return amount; }
    public LocalDate getDate() { return date; }
}

package zm.zut;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Contribution {
    private String memberId;
    private BigDecimal amount;
    private LocalDate date;

    public Contribution() {
        // Required for JSON parsing
    }

    public Contribution(String memberId, BigDecimal amount, LocalDate date) {
        this.memberId = memberId;
        this.amount = amount;
        this.date = date;
    }

    public String getMemberId() {
        return memberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }
}
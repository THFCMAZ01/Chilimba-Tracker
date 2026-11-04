
package zm.zut;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Payout {
    private String memberId;
    private BigDecimal amount;
    private LocalDate date;

    public Payout() {
    }

    public Payout(String memberId, BigDecimal amount, LocalDate date) {
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
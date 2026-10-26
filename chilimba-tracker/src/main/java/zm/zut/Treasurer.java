package zm.zut;

import java.time.LocalDate;

public class Treasurer extends User {
    
    public Treasurer(String id, String name, String phone, LocalDate joinDate) {
        super(id, name, phone, joinDate);
    }

    @Override
    public String getRoleName() {
        return "Treasurer";
    }

    @Override
    public boolean canManageFunds() {
        return true; // Treasurers can record contributions and payouts
    }
}
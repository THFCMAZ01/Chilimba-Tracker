package zm.zut;

import java.util.UUID;

public final class AuthenticatedUser {

    private final UUID userId;
    private final UUID memberId;
    private final String email;
    private final String memberCode;
    private final String name;
    private final String role;

    public AuthenticatedUser(
            UUID userId,
            UUID memberId,
            String email,
            String memberCode,
            String name,
            String role) {

        if (userId == null || memberId == null) {
            throw new IllegalArgumentException(
                "Account and member IDs are required."
            );
        }

        if (email == null || name == null || role == null) {
            throw new IllegalArgumentException(
                "Authenticated user details are incomplete."
            );
        }

        this.userId = userId;
        this.memberId = memberId;
        this.email = email;
        this.memberCode = memberCode;
        this.name = name;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getEmail() {
        return email;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isTreasurer() {
        return "TREASURER".equals(role);
    }

    public boolean canManageFunds() {
        return isTreasurer();
    }
}

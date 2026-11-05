package zm.zut;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;

public class AuthService {

    private static final String DUMMY_HASH =
        PasswordService.hashPassword(
            "DummyPasswordForTimingProtection"
                .toCharArray()
        );

    private final AuthRepository authRepository;

    public AuthService() {
        this(new AuthRepository());
    }

    AuthService(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public Optional<AuthenticatedUser> authenticate(
            String email,
            char[] password) throws SQLException {

        if (email == null || password == null
                || email.isBlank()
                || password.length == 0
                || password.length > 1024
                || email.length() > 254) {

            PasswordService.verifyPassword(
                password == null ? new char[0] : password,
                DUMMY_HASH
            );

            return Optional.empty();
        }

        String normalizedEmail = email.trim()
            .toLowerCase(Locale.ROOT);

        Optional<AuthRepository.LoginRecord> record =
            authRepository.findByEmail(normalizedEmail);

        if (record.isEmpty()) {
            PasswordService.verifyPassword(
                password, DUMMY_HASH
            );
            return Optional.empty();
        }

        AuthRepository.LoginRecord loginRecord = record.get();

        boolean passwordMatches =
            PasswordService.verifyPassword(
                password,
                loginRecord.passwordHash()
            );

        if (!passwordMatches) {
            return Optional.empty();
        }

        return Optional.of(loginRecord.user());
    }
}

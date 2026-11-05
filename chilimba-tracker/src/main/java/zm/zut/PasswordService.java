package zm.zut;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordService {

    private static final int ITERATIONS = 310_000;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordService() {
    }

    public static String hashPassword(char[] password) {
        if (password == null || password.length < 12) {
            throw new IllegalArgumentException(
                "Password must contain at least 12 characters."
            );
        }

        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        byte[] hash = deriveKey(password, salt, ITERATIONS);

        return "PBKDF2$"
            + ITERATIONS + "$"
            + Base64.getEncoder().encodeToString(salt) + "$"
            + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(
            char[] password,
            String storedHash) {

        if (password == null || storedHash == null) {
            return false;
        }

        try {
            String[] parts = storedHash.split("\\$");

            if (parts.length != 4 || !"PBKDF2".equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);

            // Reject malformed or unexpectedly expensive hash settings.
            if (iterations < 100_000 || iterations > 1_000_000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);

            if (salt.length != SALT_LENGTH
                    || expectedHash.length != KEY_LENGTH / 8) {
                return false;
            }

            byte[] actualHash = deriveKey(
                password, salt, iterations
            );

            return MessageDigest.isEqual(expectedHash, actualHash);

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] deriveKey(
            char[] password,
            byte[] salt,
            int iterations) {

        PBEKeySpec spec = new PBEKeySpec(
            password, salt, iterations, KEY_LENGTH
        );

        try {
            SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } catch (Exception e) {
            throw new IllegalStateException(
                "Password security service is unavailable.", e
            );

        } finally {
            spec.clearPassword();
        }
    }
}

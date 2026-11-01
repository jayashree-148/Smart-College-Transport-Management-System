package kalvi.transport.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/** Salted PBKDF2-HMAC-SHA256. Stored format:  iterations$salt$hash  (both Base64). */
public final class PasswordHasher {
    private static final int ITERATIONS = 120_000, KEY_BITS = 256, SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private PasswordHasher() { }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITERATIONS));
    }

    public static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            byte[] expected = Base64.getDecoder().decode(p[2]);
            byte[] actual = pbkdf2(password, Base64.getDecoder().decode(p[1]), Integer.parseInt(p[0]));
            return MessageDigest.isEqual(expected, actual);        // constant-time compare
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException(e);
        }
    }
}

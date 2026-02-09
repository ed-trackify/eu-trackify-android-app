package common;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class PasswordSecurityUtil {

    // For password hashing (client-side before sending to server)
    private static final String HASH_ALGORITHM = "SHA-256";

    // For AES encryption of stored passwords
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATION_COUNT = 10000;
    private static final int KEY_LENGTH = 256;

    // Device-specific key generation
    private static final String SALT_BASE = "EU_Tr@ck1fy_S@lt_2024";

    /**
     * Hash password using SHA-256 for transmission to server
     * This provides one-way hashing to avoid sending plain text
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            AppModel.ApplicationError(e, "PasswordSecurityUtil::hashPassword");
            // Fallback to plain text if hashing fails (should never happen)
            return password;
        }
    }

    /**
     * Hash password with salt for extra security
     * Used for critical operations like password change
     */
    public static String hashPasswordWithSalt(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            // Combine salt and hash for transmission
            String saltedHash = salt + ":" + bytesToHex(hashedBytes);
            return saltedHash;
        } catch (NoSuchAlgorithmException e) {
            AppModel.ApplicationError(e, "PasswordSecurityUtil::hashPasswordWithSalt");
            return hashPassword(password); // Fallback to regular hash
        }
    }

    /**
     * Generate a random salt for password hashing
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Encrypt password for local storage using AES
     * This is for auto-login feature - stores password securely on device
     */
    public static String encryptForStorage(String password, String deviceId) {
        try {
            // Generate a device-specific key
            String keySource = SALT_BASE + deviceId;
            SecretKey secretKey = generateSecretKey(keySource);

            // Generate IV
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[16];
            random.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encryptedBytes = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            // Return as Base64
            return Base64.encodeToString(combined, Base64.NO_WRAP);

        } catch (Exception e) {
            AppModel.ApplicationError(e, "PasswordSecurityUtil::encryptForStorage");
            // If encryption fails, return a marker that indicates encryption failed
            // Never store plain password
            return null;
        }
    }

    /**
     * Decrypt password from local storage
     */
    public static String decryptFromStorage(String encryptedPassword, String deviceId) {
        if (encryptedPassword == null || encryptedPassword.isEmpty()) {
            return null;
        }

        try {
            // Generate the same device-specific key
            String keySource = SALT_BASE + deviceId;
            SecretKey secretKey = generateSecretKey(keySource);

            // Decode from Base64
            byte[] combined = Base64.decode(encryptedPassword, Base64.NO_WRAP);

            // Extract IV and encrypted data
            byte[] iv = new byte[16];
            byte[] encryptedBytes = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            AppModel.ApplicationError(e, "PasswordSecurityUtil::decryptFromStorage");
            return null;
        }
    }

    /**
     * Generate a secret key from a password string
     */
    private static SecretKey generateSecretKey(String password) throws Exception {
        // Use a fixed salt for key generation (device-specific)
        byte[] salt = SALT_BASE.getBytes(StandardCharsets.UTF_8);

        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM);
        SecretKey tmp = factory.generateSecret(spec);

        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Convert byte array to hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Validate password strength
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }

        // Check for at least one digit
        boolean hasDigit = false;
        // Check for at least one letter
        boolean hasLetter = false;

        for (char c : password.toCharArray()) {
            if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (Character.isLetter(c)) {
                hasLetter = true;
            }
            if (hasDigit && hasLetter) {
                break;
            }
        }

        return hasDigit && hasLetter;
    }

    /**
     * Get password strength message
     */
    public static String getPasswordStrengthMessage(String password) {
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty";
        }

        if (password.length() < 6) {
            return "Password must be at least 6 characters";
        }

        if (!isPasswordStrong(password)) {
            return "Password should contain both letters and numbers";
        }

        if (password.length() >= 10) {
            return "Strong password";
        } else if (password.length() >= 8) {
            return "Good password";
        } else {
            return "Acceptable password";
        }
    }

    /**
     * Get unique device identifier for encryption
     * Uses Android ID as a device-specific identifier
     */
    public static String getDeviceId(android.content.Context context) {
        String androidId = android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        );

        // Fallback to a default if Android ID is not available
        if (androidId == null || androidId.isEmpty()) {
            androidId = "DEFAULT_DEVICE_ID";
        }

        return androidId;
    }

    /**
     * Clear sensitive data from memory
     * Call this after using passwords
     */
    public static void clearSensitiveData(char[] data) {
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                data[i] = '\0';
            }
        }
    }

    /**
     * Clear sensitive string data
     * Note: Strings are immutable in Java, so this has limited effect
     * Use char[] when possible for sensitive data
     */
    public static void clearSensitiveString(String data) {
        if (data != null) {
            // Best effort to clear string from memory
            data = null;
            System.gc();
        }
    }
}
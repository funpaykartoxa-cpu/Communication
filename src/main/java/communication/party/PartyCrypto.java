package communication.party;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Encrypts party traffic with a key derived from the party code. The backend
 * channel is a broadcast, so AES-GCM keeps the actual state readable only by
 * clients that know the same code.
 */
final class PartyCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private PartyCrypto() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static String generateCode() {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
    }

    static String encrypt(String code, String plaintext) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(code), "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] output = new byte[IV_LENGTH + encrypted.length];
        System.arraycopy(iv, 0, output, 0, IV_LENGTH);
        System.arraycopy(encrypted, 0, output, IV_LENGTH, encrypted.length);
        return Base64.getEncoder().encodeToString(output);
    }

    static String decrypt(String code, String encoded) throws Exception {
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Party packet is too short");
        }
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(data, 0, iv, 0, IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(code), "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] key(String code) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(("delta-party-v1:" + normalize(code)).getBytes(StandardCharsets.UTF_8));
    }
}

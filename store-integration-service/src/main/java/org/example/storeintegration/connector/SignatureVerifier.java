package org.example.storeintegration.connector;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

public final class SignatureVerifier {
    private SignatureVerifier() {}
    public static boolean base64HmacSha256(String secret, byte[] payload, String provided) {
        return constantTime(hmac(secret, payload), decodeBase64(provided));
    }
    public static boolean hexHmacSha256(String secret, String payload, String provided) {
        try { return constantTime(hmac(secret, payload.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(provided)); }
        catch (RuntimeException exception) { return false; }
    }
    public static String sha256(byte[] payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private static byte[] hmac(String secret, byte[] payload) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return mac.doFinal(payload); }
        catch (Exception exception) { throw new IllegalStateException("HMAC could not be calculated", exception); }
    }
    private static byte[] decodeBase64(String value) { try { return Base64.getDecoder().decode(value == null ? "" : value); } catch (RuntimeException exception) { return new byte[0]; } }
    private static boolean constantTime(byte[] expected, byte[] provided) { return MessageDigest.isEqual(expected, provided); }
}

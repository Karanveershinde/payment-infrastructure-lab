package com.payflow.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for HMAC-SHA256 signature calculation and constant-time verification.
 * Resistant to timing side-channel attacks.
 */
public final class HmacSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacSignatureUtil() {}

    /**
     * Calculates HMAC-SHA256 hex signature of payload using secret.
     */
    public static String calculateSignature(String secret, String payload) {
        if (secret == null || payload == null) {
            throw new IllegalArgumentException("Secret and payload must not be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    /**
     * Verifies that the given signature matches the expected HMAC-SHA256 of payload.
     * Uses MessageDigest.isEqual for constant-time comparison to prevent timing attacks.
     */
    public static boolean verifySignature(String secret, String payload, String signature) {
        if (secret == null || payload == null || signature == null) {
            return false;
        }
        String expectedSignature = calculateSignature(secret, payload);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Calculates HMAC-SHA256 hex signature of timestamp + "." + payload using secret.
     */
    public static String calculateSignature(String secret, String timestamp, String payload) {
        if (secret == null || timestamp == null || payload == null) {
            throw new IllegalArgumentException("Secret, timestamp, and payload must not be null");
        }
        return calculateSignature(secret, timestamp + "." + payload);
    }

    /**
     * Verifies that the given signature matches HMAC-SHA256 of timestamp + "." + payload.
     */
    public static boolean verifySignature(String secret, String timestamp, String payload, String signature) {
        if (secret == null || timestamp == null || payload == null || signature == null) {
            return false;
        }
        return verifySignature(secret, timestamp + "." + payload, signature);
    }
}

package com.infra.core.crypto.util;

import com.infra.core.crypto.exception.KeyParsingException;
import org.bouncycastle.util.encoders.Hex;

import java.security.Key;
import java.util.Base64;

/**
 * Static helpers for data encoding (Base64, Hex, PEM, DER).
 */
public final class CryptoEncoderUtils {

    private CryptoEncoderUtils() {
        // Private constructor to prevent instantiation
    }

    // --- Base64 ---

    public static String toBase64(byte[] data) {
        return data == null ? null : Base64.getEncoder().encodeToString(data);
    }

    public static byte[] fromBase64(String base64) {
        return base64 == null ? null : Base64.getDecoder().decode(base64);
    }

    public static String toBase64UrlSafe(byte[] data) {
        return data == null ? null : Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] fromBase64UrlSafe(String base64) {
        return base64 == null ? null : Base64.getUrlDecoder().decode(base64);
    }

    // --- Hex ---

    public static String toHex(byte[] data) {
        return data == null ? null : Hex.toHexString(data).toLowerCase();
    }

    public static byte[] fromHex(String hex) {
        try {
            return hex == null ? null : Hex.decode(hex);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Hex string", e);
        }
    }

    // --- PEM ---

    public static String toPem(String type, byte[] data) {
        if (data == null || type == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type.toUpperCase()).append("-----\n");
        sb.append(Base64.getMimeEncoder().encodeToString(data)).append("\n");
        sb.append("-----END ").append(type.toUpperCase()).append("-----\n");
        return sb.toString();
    }

    public static byte[] fromPem(String pem) {
        if (pem == null) return null;
        try {
            String cleanPem = pem
                    .replaceAll("-----BEGIN.*?-----", "")
                    .replaceAll("-----END.*?-----", "")
                    .replaceAll("\\s", "");
            return fromBase64(cleanPem);
        } catch (Exception e) {
            throw new KeyParsingException("Failed to parse PEM format", e);
        }
    }

    // --- DER ---

    public static byte[] toDer(Key key) {
        if (key == null) return null;
        return key.getEncoded();
    }

    // fromDer requires context (RSA vs SecretKey), so it will be implemented in Specific KeyParsers
}

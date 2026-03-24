package com.infra.core.crypto.mac.impl;

import com.infra.core.crypto.enums.MacAlgorithm;
import com.infra.core.crypto.exception.CryptographyException;
import com.infra.core.crypto.mac.HmacService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production implementation of {@link HmacService} backed by JCA {@link javax.crypto.Mac}.
 *
 * <h2>Thread Safety</h2>
 * <p>{@link Mac} instances are NOT thread-safe and must never be shared across threads.
 * A new {@link Mac} instance is obtained via {@link Mac#getInstance(String)} on every call.
 * No caching or ThreadLocal is used — correctness over micro-optimisation.</p>
 *
 * <h2>Key Handling</h2>
 * <p>The incoming {@link Key} is used to initialise the {@link Mac} engine directly.
 * If the key is a raw {@code byte[]} wrapped in a generic {@link Key}, a
 * {@link SecretKeySpec} bridge is created to feed it to the Mac API correctly.
 * This avoids callers needing to know JCA key wrapping details.</p>
 *
 * <h2>Timing Attack Prevention</h2>
 * <p>Verification uses {@link MessageDigest#isEqual(byte[], byte[])} for constant-time
 * comparison. This is intentional: {@link MessageDigest#isEqual} is part of the JCA API and
 * is specifically documented for this use case. {@link java.util.Arrays#equals} is explicitly
 * prohibited as it short-circuits on the first mismatch.</p>
 */
@Service
public class HmacServiceImpl implements HmacService {

    @Override
    public byte[] calculateMac(byte[] payload, Key macKey, MacAlgorithm algorithm) {
        if (payload == null) {
            throw new CryptographyException("Payload must not be null for MAC calculation");
        }
        if (macKey == null) {
            throw new CryptographyException("MAC key must not be null");
        }
        if (algorithm == null) {
            throw new CryptographyException("MacAlgorithm must not be null");
        }

        try {
            // New Mac instance per call — Mac is NOT thread-safe.
            Mac mac = Mac.getInstance(algorithm.getJcaName());
            mac.init(resolveKey(macKey, algorithm));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException e) {
            // All MacAlgorithm values map to guaranteed JVM algorithms — should not occur.
            throw new CryptographyException(
                    "HMAC algorithm not available: " + algorithm.name(), e);
        } catch (InvalidKeyException e) {
            // Key type or length is incompatible with the requested algorithm.
            throw new CryptographyException(
                    "Invalid MAC key for algorithm " + algorithm.name() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyMac(byte[] payload, byte[] expectedMac, Key macKey, MacAlgorithm algorithm) {
        if (expectedMac == null) {
            // Fail closed — a null expected MAC is a hard failure, not a false result.
            throw new CryptographyException("Expected MAC must not be null for verification");
        }

        byte[] actualMac = calculateMac(payload, macKey, algorithm);

        // CRITICAL: Constant-time comparison via MessageDigest.isEqual.
        // Arrays.equals() is explicitly forbidden here — it leaks length and position of
        // first mismatch, enabling timing-based side-channel attacks.
        return MessageDigest.isEqual(actualMac, expectedMac);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Normalises the incoming {@link Key} into a {@link SecretKeySpec} compatible
     * with the chosen HMAC algorithm.
     *
     * <p>If the key is already encoded (getEncoded() != null), a new {@link SecretKeySpec}
     * is built from the raw bytes, ensuring consistent Mac API compatibility regardless of
     * how the key was originally created (e.g., from a KMS response or directly from bytes).</p>
     */
    private SecretKeySpec resolveKey(Key macKey, MacAlgorithm algorithm) {
        byte[] keyBytes = macKey.getEncoded();
        if (keyBytes == null || keyBytes.length == 0) {
            throw new CryptographyException(
                    "MAC key does not expose encoded bytes — cannot use with algorithm " + algorithm.name());
        }
        return new SecretKeySpec(keyBytes, algorithm.getJcaName());
    }
}

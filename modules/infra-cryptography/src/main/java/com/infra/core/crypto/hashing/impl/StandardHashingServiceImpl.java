package com.infra.core.crypto.hashing.impl;

import com.infra.core.crypto.enums.HashAlgorithm;
import com.infra.core.crypto.exception.HashingFailureException;
import com.infra.core.crypto.hashing.StandardHashingService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Production implementation of {@link StandardHashingService} backed by JCA {@link MessageDigest}.
 *
 * <p><b>Thread Safety:</b> {@link MessageDigest} instances are NOT thread-safe and must NEVER be
 * shared as singletons. Each call to {@link #hash} or {@link #verify} instantiates a fresh
 * {@link MessageDigest} via {@link MessageDigest#getInstance(String)}, which is the correct
 * and required approach.</p>
 *
 * <p><b>Timing Attack Prevention:</b> Verification uses {@link MessageDigest#isEqual(byte[], byte[])}
 * which performs a constant-time comparison, preventing length-based timing leakage.</p>
 */
@Service
public class StandardHashingServiceImpl implements StandardHashingService {

    @Override
    public byte[] hash(byte[] input, HashAlgorithm algorithm) {
        if (input == null || input.length == 0) {
            throw new HashingFailureException("Input to hash must not be null or empty");
        }
        if (algorithm == null) {
            throw new HashingFailureException("HashAlgorithm must not be null");
        }

        try {
            // A new MessageDigest is created per-call — thread-safe by design, as per JCA rules.
            MessageDigest digest = MessageDigest.getInstance(algorithm.getAlgorithmName());
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            // Should never happen: all HashAlgorithm values map to algorithms guaranteed by the JVM.
            // Wrap and rethrow to prevent JCA internals leaking to callers.
            throw new HashingFailureException(
                    "Hashing algorithm not available: " + algorithm.name(), e);
        }
    }

    @Override
    public boolean verify(byte[] input, byte[] expectedHash, HashAlgorithm algorithm) {
        if (input == null || expectedHash == null) {
            // Fail closed — do not silently accept null as a match.
            throw new HashingFailureException("Input and expectedHash must not be null for verification");
        }

        byte[] actualHash = hash(input, algorithm);

        // CRITICAL: MessageDigest.isEqual performs constant-time comparison.
        // DO NOT use Arrays.equals() — it short-circuits on length or first mismatch,
        // leaking timing information that enables side-channel attacks.
        return MessageDigest.isEqual(actualHash, expectedHash);
    }
}

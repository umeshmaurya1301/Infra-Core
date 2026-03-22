package com.infra.core.crypto.hashing;

import com.infra.core.crypto.enums.HashAlgorithm;
import com.infra.core.crypto.exception.HashingFailureException;

/**
 * Contract for standard, one-way cryptographic hashing operations on raw byte arrays.
 *
 * <p>This service is intentionally scoped to general-purpose data hashing (e.g., fingerprinting
 * tokens, checksums). It is NOT suitable for password hashing — use {@code PasswordHashingService}
 * for that purpose.</p>
 */
public interface StandardHashingService {

    /**
     * Computes the cryptographic hash of the given input bytes using the specified algorithm.
     *
     * @param input     the raw bytes to hash; must not be null or empty.
     * @param algorithm the hashing algorithm to apply; must not be null.
     * @return the raw hash digest as a byte array.
     * @throws HashingFailureException if the hashing operation fails for any reason.
     */
    byte[] hash(byte[] input, HashAlgorithm algorithm);

    /**
     * Verifies that a given input hashes to the expected digest using constant-time comparison
     * to prevent timing-based side-channel attacks.
     *
     * @param input        the original input bytes to verify.
     * @param expectedHash the pre-computed hash to compare against.
     * @param algorithm    the hashing algorithm originally used to produce {@code expectedHash}.
     * @return {@code true} if the hashes match; {@code false} otherwise.
     * @throws HashingFailureException if the hashing operation itself fails.
     */
    boolean verify(byte[] input, byte[] expectedHash, HashAlgorithm algorithm);
}

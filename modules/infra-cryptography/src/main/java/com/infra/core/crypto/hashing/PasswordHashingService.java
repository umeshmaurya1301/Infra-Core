package com.infra.core.crypto.hashing;

import com.infra.core.crypto.enums.PasswordHashAlgorithm;
import com.infra.core.crypto.exception.HashingFailureException;

/**
 * Contract for credential-safe, adaptive password hashing.
 *
 * <p>Implementations MUST use a memory-hard algorithm (Argon2id) to provide
 * GPU/ASIC resistance. Outputs are standardised PHC (Password Hashing Competition)
 * format strings that embed the salt and algorithm parameters inline, making
 * verification self-contained and migration-friendly.</p>
 *
 * <p>Callers must supply passwords as {@code char[]} and are responsible for
 * zeroing the array after this call returns. Implementations will also
 * attempt to wipe the array internally as a defence-in-depth measure.</p>
 */
public interface PasswordHashingService {

    /**
     * Hashes a raw password using the configured algorithm and parameters.
     *
     * <p>The returned string is in PHC format and contains everything needed
     * to verify the password later (algorithm id, parameters, salt, hash).</p>
     *
     * @param password the plaintext password as a mutable char array; must not be null or empty.
     * @return the encoded PHC-format hash string.
     * @throws HashingFailureException if hashing fails.
     */
    String hashPassword(char[] password);

    /**
     * Verifies a plaintext password against a stored PHC-format encoded hash.
     *
     * @param password    the plaintext password to verify; must not be null.
     * @param encodedHash the stored PHC hash string produced by {@link #hashPassword}.
     * @return {@code true} if the password matches; {@code false} otherwise.
     * @throws HashingFailureException if verification fails unexpectedly (not a simple mismatch).
     */
    boolean verifyPassword(char[] password, String encodedHash);

    /**
     * Returns the algorithm this implementation is backed by.
     * Used by auto-configuration for conditional bean wiring.
     */
    PasswordHashAlgorithm getAlgorithm();
}

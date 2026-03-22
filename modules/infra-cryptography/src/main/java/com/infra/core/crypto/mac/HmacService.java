package com.infra.core.crypto.mac;

import com.infra.core.crypto.enums.MacAlgorithm;
import com.infra.core.crypto.exception.CryptographyException;

import java.security.Key;

/**
 * Contract for HMAC-based Message Authentication Code (MAC) operations.
 *
 * <p>This service is strictly scoped to keyed authentication codes. It must NOT be confused
 * with general-purpose hashing ({@code StandardHashingService}), which is keyless.
 * MAC operations provide both integrity and authenticity guarantees.</p>
 *
 * <p>Verification MUST use constant-time comparison to prevent timing-based attacks.</p>
 */
public interface HmacService {

    /**
     * Computes an HMAC over the given payload using the specified secret key and algorithm.
     *
     * @param payload   the raw bytes to authenticate; must not be null.
     * @param macKey    the secret key to use; must be compatible with the chosen algorithm.
     * @param algorithm the HMAC algorithm to apply; must not be null.
     * @return the raw MAC digest as a byte array.
     * @throws CryptographyException if computation fails (e.g., invalid key, unsupported algorithm).
     */
    byte[] calculateMac(byte[] payload, Key macKey, MacAlgorithm algorithm);

    /**
     * Verifies that the given payload produces the expected MAC, using constant-time comparison.
     *
     * @param payload     the raw bytes that were originally authenticated.
     * @param expectedMac the previously computed MAC to compare against.
     * @param macKey      the same secret key that produced {@code expectedMac}.
     * @param algorithm   the same algorithm that produced {@code expectedMac}.
     * @return {@code true} if the MACs match; {@code false} otherwise.
     * @throws CryptographyException if the HMAC computation itself fails.
     */
    boolean verifyMac(byte[] payload, byte[] expectedMac, Key macKey, MacAlgorithm algorithm);
}

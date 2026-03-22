package com.infra.core.crypto.symmetric;

/**
 * Contract for symmetric encryption and decryption operations.
 *
 * <p>All implementations must produce self-describing, forward-compatible byte payloads
 * that embed a versioned Key ID (KID) header. This enables seamless key rotation — the
 * decryption path reads the KID from the payload and resolves the correct historical key
 * without any external state.</p>
 *
 * <h2>Output Payload Structure</h2>
 * <pre>
 * ┌──────────────┬────────────┬───────────────┬────────────────────────────────────┐
 * │ Version (1B) │ KID Len(1B)│ KID (variable)│ Algorithm-Specific Payload         │
 * └──────────────┴────────────┴───────────────┴────────────────────────────────────┘
 * </pre>
 *
 * <p>Implementations define what the "Algorithm-Specific Payload" contains (e.g., IV + Ciphertext
 * for AES-GCM, or IV + Ciphertext + HMAC for AES-CBC-HMAC).</p>
 */
public interface SymmetricEncryptionService {

    /**
     * Encrypts {@code payload} with optional Associated Data (AAD) for authentication.
     *
     * <p>The returned byte array contains a versioned, self-describing binary structure
     * embedding the KID, algorithm-specific IV, ciphertext, and any authentication tags.
     * The AAD is authenticated but NOT encrypted — it is not included in the output.</p>
     *
     * @param payload the plaintext bytes to encrypt; must not be null or empty.
     * @param aad     the Additional Authenticated Data to bind to this ciphertext;
     *                must be the SAME value on decryption, or decryption will fail.
     *                Use {@code new byte[0]} for no binding context.
     * @return the encrypted, self-describing binary payload.
     * @throws com.infra.core.crypto.exception.EncryptionFailureException on any failure.
     */
    byte[] encrypt(byte[] payload, byte[] aad);

    /**
     * Convenience overload that encrypts without binding any AAD context.
     * Equivalent to calling {@code encrypt(payload, new byte[0])}.
     *
     * @param payload the plaintext bytes to encrypt; must not be null or empty.
     * @return the encrypted, self-describing binary payload.
     */
    default byte[] encrypt(byte[] payload) {
        return encrypt(payload, new byte[0]);
    }

    /**
     * Decrypts a payload produced by {@link #encrypt(byte[], byte[])}.
     *
     * <p>The KID is parsed from the payload header, used to resolve the original key,
     * and the ciphertext is decrypted with authentication verification. If AAD was used
     * during encryption, the IDENTICAL AAD must be provided here, or decryption will fail.</p>
     *
     * @param payload the encrypted binary payload as produced by this service's encrypt method.
     * @param aad     the same AAD bytes that were used during encryption.
     * @return the original plaintext bytes.
     * @throws com.infra.core.crypto.exception.DecryptionFailureException on any failure,
     *         including authentication tag mismatch, tampered ciphertext, or unknown KID.
     */
    byte[] decrypt(byte[] payload, byte[] aad);

    /**
     * Convenience overload that decrypts a payload that was encrypted without AAD.
     * Equivalent to calling {@code decrypt(payload, new byte[0])}.
     *
     * @param payload the encrypted binary payload.
     * @return the original plaintext bytes.
     */
    default byte[] decrypt(byte[] payload) {
        return decrypt(payload, new byte[0]);
    }
}

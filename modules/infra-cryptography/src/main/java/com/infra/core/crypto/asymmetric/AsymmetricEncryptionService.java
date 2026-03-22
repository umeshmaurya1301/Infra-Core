package com.infra.core.crypto.asymmetric;

import com.infra.core.crypto.enums.RsaPadding;

/**
 * Contract for asymmetric encryption and decryption using public/private key pairs.
 *
 * <p>All implementations produce self-describing binary payloads with an embedded KID header
 * to support seamless key rotation:</p>
 * <pre>
 * ┌──────────────┬────────────┬───────────────┬────────────────────┐
 * │ Version (1B) │ KID Len(1B)│ KID (variable)│ Ciphertext (rest)  │
 * └──────────────┴────────────┴───────────────┴────────────────────┘
 * </pre>
 *
 * <p><b>Important scope:</b> RSA encryption is suitable only for small payloads (typically
 * a symmetric session key). For large data, use hybrid encryption: encrypt data with AES-GCM,
 * then encrypt the AES key with RSA.</p>
 */
public interface AsymmetricEncryptionService {

    /**
     * Encrypts {@code payload} using the active public key and the specified RSA padding.
     *
     * <p>Preemptive bounds checking is performed against the payload size limit for the
     * given padding scheme and key modulus size. If the payload exceeds the maximum,
     * an {@link com.infra.core.crypto.exception.EncryptionFailureException} is thrown
     * BEFORE any cipher is instantiated.</p>
     *
     * @param payload the plaintext bytes to encrypt; must be within RSA block size limits.
     * @param padding the RSA padding scheme to use; must not be null.
     * @return the encrypted, self-describing binary payload with KID header.
     * @throws com.infra.core.crypto.exception.EncryptionFailureException on failure.
     */
    byte[] encrypt(byte[] payload, RsaPadding padding);

    /**
     * Decrypts a payload produced by {@link #encrypt(byte[], RsaPadding)}.
     *
     * <p>The KID is parsed from the header, the corresponding private key is resolved,
     * and the ciphertext is decrypted. The same padding scheme used during encryption must
     * be provided.</p>
     *
     * @param payload the encrypted binary payload as produced by {@link #encrypt}.
     * @param padding the RSA padding scheme that was used during encryption.
     * @return the original plaintext bytes.
     * @throws com.infra.core.crypto.exception.DecryptionFailureException on any failure.
     */
    byte[] decrypt(byte[] payload, RsaPadding padding);
}

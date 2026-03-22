package com.infra.core.crypto.asymmetric;

/**
 * Contract for asymmetric digital signature operations.
 *
 * <p>Implementations produce self-describing binary signature payloads with an embedded KID
 * header, enabling the correct historical public key to be retrieved for verification even
 * after key rotation:</p>
 * <pre>
 * ┌──────────────┬────────────┬───────────────┬────────────────────┐
 * │ Version (1B) │ KID Len(1B)│ KID (variable)│ Signature (rest)   │
 * └──────────────┴────────────┴───────────────┴────────────────────┘
 * </pre>
 *
 * <p>The {@code payload} passed to {@link #sign} and {@link #verify} is the original message
 * to be signed — NOT the binary signature payload. This keeps the signing and verification
 * APIs symmetric and clear.</p>
 */
public interface DigitalSignatureService {

    /**
     * Signs the {@code payload} with the active private key and returns a self-describing
     * binary signature payload embedding the KID.
     *
     * @param payload the original message bytes to sign; must not be null or empty.
     * @return binary signature payload: {@code [Version][KID_LEN][KID][RawSignature]}.
     * @throws com.infra.core.crypto.exception.CryptographyException on any signing failure.
     */
    byte[] sign(byte[] payload);

    /**
     * Verifies a signature payload produced by {@link #sign} against the original message.
     *
     * <p>The KID is extracted from the {@code signaturePayload} header, the corresponding
     * public key is resolved (including historical/rotated keys), and the raw signature
     * is verified against {@code payload}.</p>
     *
     * @param payload          the original message bytes that were signed.
     * @param signaturePayload the binary signature payload as produced by {@link #sign}.
     * @return {@code true} if the signature is valid; {@code false} otherwise.
     * @throws com.infra.core.crypto.exception.CryptographyException on structural parsing failure
     *         or algorithm errors. A simple signature mismatch returns {@code false}, not an exception.
     */
    boolean verify(byte[] payload, byte[] signaturePayload);
}

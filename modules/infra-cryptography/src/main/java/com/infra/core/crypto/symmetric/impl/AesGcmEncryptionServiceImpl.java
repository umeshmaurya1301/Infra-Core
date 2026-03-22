package com.infra.core.crypto.symmetric.impl;

import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.keys.SymmetricKeyMaterial;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import com.infra.core.crypto.symmetric.SymmetricEncryptionService;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Production-grade AES-GCM symmetric encryption service.
 *
 * <h2>Algorithm</h2>
 * <p>AES/GCM/NoPadding — an Authenticated Encryption with Associated Data (AEAD) mode.
 * GCM provides both confidentiality AND integrity/authenticity in a single pass.
 * No separate HMAC step is required, unlike AES-CBC.</p>
 *
 * <h2>Binary Output Layout</h2>
 * <p>The {@code encrypt} method produces, and {@code decrypt} consumes, the following exact
 * byte structure. All multi-byte values are big-endian (Java/network byte order default).</p>
 * <pre>
 * ┌──────────────┬────────────┬────────────────┬────────────┬──────────────────────────────┐
 * │ Version (1B) │ KID Len(1B)│ KID (0-255 B) │  IV (12B)  │ Ciphertext + GCM Tag (rest)  │
 * └──────────────┴────────────┴────────────────┴────────────┴──────────────────────────────┘
 * Offsets: 0       1           2              2+kidLen     2+kidLen+12    (to end)
 * </pre>
 *
 * <ul>
 *   <li><b>Version Byte (0x01):</b> Reserved for future format evolution without breaking parsers.</li>
 *   <li><b>KID Length (1 byte, unsigned):</b> Max 255. Enables key resolution on decryption.</li>
 *   <li><b>KID Bytes (UTF-8):</b> The Key ID from the active {@link SymmetricKeyMaterial}.</li>
 *   <li><b>IV (12 bytes):</b> Randomly generated per encryption. GCM standard IV length.</li>
 *   <li><b>Ciphertext + Tag:</b> Raw output of {@link Cipher#doFinal}. GCM tag is 128 bits (16 bytes)
 *       appended automatically by the JCA provider.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link Cipher} is NOT thread-safe. A new instance is obtained via {@link Cipher#getInstance}
 * on every encrypt/decrypt call — no caching, no {@code ThreadLocal}. {@link SecureRandom}
 * IS thread-safe and is intentionally shared as a static final singleton.</p>
 *
 * <h2>Key Resolution & Rotation</h2>
 * <p>Encryption always fetches the ACTIVE key from the {@link SymmetricKeyProvider}.
 * Decryption reads the KID from the payload header and fetches the HISTORICAL key by ID,
 * enabling seamless decryption of ciphertexts that were encrypted with rotated-out keys.</p>
 *
 * <h2>Fail Closed Guarantee</h2>
 * <p>Any GCM authentication tag mismatch ({@link AEADBadTagException}) immediately
 * throws a {@link DecryptionFailureException} with a generic message that does NOT expose
 * whether the failure was a tag mismatch, padding issue, or IV corruption — preventing
 * adaptive chosen-ciphertext attacks.</p>
 */
@Service
public class AesGcmEncryptionServiceImpl implements SymmetricEncryptionService {

    // --------------------------------------------------------------------------------------------
    // Constants
    // --------------------------------------------------------------------------------------------

    /** AES-GCM transformation string. Intentionally NOT a public constant — only used internally. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Current payload format version. Bump this if the binary layout ever changes. */
    private static final byte FORMAT_VERSION = 0x01;

    /** GCM IV length: 12 bytes is required by NIST SP 800-38D for maximum security/performance. */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /** GCM authentication tag size in BITS. 128 bits (16 bytes) is the maximum and is required. */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /** Default key resolution context used when callers don't specify one. */
    private static final String DEFAULT_KEY_CONTEXT = "default";

    /**
     * SecureRandom is thread-safe — a single shared instance is correct and efficient here.
     * Avoids the cost of reseeding entropy for every call.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // --------------------------------------------------------------------------------------------
    // Dependencies
    // --------------------------------------------------------------------------------------------

    private final SymmetricKeyProvider keyProvider;

    public AesGcmEncryptionServiceImpl(SymmetricKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    // --------------------------------------------------------------------------------------------
    // Public API
    // --------------------------------------------------------------------------------------------

    @Override
    public byte[] encrypt(byte[] payload, byte[] aad) {
        validatePayload(payload, "Plaintext payload must not be null or empty");
        byte[] effectiveAad = resolveAad(aad);

        // Step 1: Fetch the current active key from the provider.
        SymmetricKeyMaterial keyMaterial = keyProvider.getActiveKey(DEFAULT_KEY_CONTEXT);

        // Step 2: Encode and validate the KID.
        byte[] kidBytes = encodeKid(keyMaterial.getKid());

        // Step 3: Generate a cryptographically random 12-byte IV for this operation.
        byte[] iv = generateIv();

        // Step 4: Perform AES-GCM encryption.
        byte[] ciphertextWithTag = performEncryption(payload, effectiveAad, keyMaterial, iv);

        // Step 5: Compose the versioned binary output payload.
        return assemblePayload(kidBytes, iv, ciphertextWithTag);
    }

    @Override
    public byte[] decrypt(byte[] payload, byte[] aad) {
        validatePayload(payload, "Encrypted payload must not be null or empty");
        byte[] effectiveAad = resolveAad(aad);

        // Wrap in a ByteBuffer for safe, bounds-checked sequential reads.
        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Step 1: Read and validate the version byte.
        readAndValidateVersion(buffer);

        // Step 2: Read the KID.
        String kid = readKid(buffer);

        // Step 3: Resolve the key corresponding to this KID. May be a historical (rotated) key.
        SymmetricKeyMaterial keyMaterial = keyProvider.getKeyById(kid);

        // Step 4: Read the 12-byte IV.
        byte[] iv = readIv(buffer);

        // Step 5: Read the remaining bytes — this is the ciphertext + GCM tag.
        byte[] ciphertextWithTag = readRemainingBytes(buffer);

        // Step 6: Perform AES-GCM decryption and authentication.
        return performDecryption(ciphertextWithTag, effectiveAad, keyMaterial, iv);
    }

    // --------------------------------------------------------------------------------------------
    // Encryption internals
    // --------------------------------------------------------------------------------------------

    /**
     * Encodes the KID to UTF-8 bytes and asserts it fits within the 1-byte length prefix.
     */
    private byte[] encodeKid(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new EncryptionFailureException("Key ID (KID) from KeyProvider must not be null or blank");
        }
        byte[] kidBytes = kid.getBytes(StandardCharsets.UTF_8);
        if (kidBytes.length > 255) {
            throw new EncryptionFailureException(
                    "Key ID (KID) exceeds maximum 255 bytes when UTF-8 encoded. Length: " + kidBytes.length);
        }
        return kidBytes;
    }

    /**
     * Generates a secure, 12-byte random IV for this encryption operation.
     */
    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * Initialises a fresh AES-GCM Cipher in ENCRYPT_MODE, sets the AAD, and encrypts.
     *
     * <p>A new {@link Cipher} instance is created per call — Cipher is NOT thread-safe.</p>
     */
    private byte[] performEncryption(byte[] plaintext, byte[] aad,
                                     SymmetricKeyMaterial keyMaterial, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keyMaterial.getEncryptionKey(), parameterSpec);

            // AAD is authenticated but NOT encrypted — it is not included in the output.
            cipher.updateAAD(aad);

            return cipher.doFinal(plaintext);

        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            // JVM does not have AES/GCM — this should never happen on a compliant JVM.
            throw new EncryptionFailureException("AES-GCM transformation not available", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new EncryptionFailureException("Invalid key or IV for AES-GCM encryption", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionFailureException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Composes the final binary payload from its constituent parts using a {@link ByteBuffer}.
     *
     * <pre>
     * Layout: [VERSION(1)] [KID_LEN(1)] [KID(variable)] [IV(12)] [CIPHERTEXT+TAG(rest)]
     * </pre>
     */
    private byte[] assemblePayload(byte[] kidBytes, byte[] iv, byte[] ciphertextWithTag) {
        int totalLength = 1                     // VERSION byte
                + 1                             // KID_LEN byte
                + kidBytes.length               // KID content
                + GCM_IV_LENGTH_BYTES           // IV
                + ciphertextWithTag.length;     // Ciphertext + GCM tag

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);

        buffer.put(FORMAT_VERSION);                     // Byte 0: Version
        buffer.put((byte) kidBytes.length);             // Byte 1: KID length (safe: asserted <= 255)
        buffer.put(kidBytes);                           // Bytes 2 to 2+kidLen: KID content
        buffer.put(iv);                                 // Next 12 bytes: IV
        buffer.put(ciphertextWithTag);                  // Remaining: Ciphertext + GCM tag

        return buffer.array();
    }

    // --------------------------------------------------------------------------------------------
    // Decryption internals
    // --------------------------------------------------------------------------------------------

    /**
     * Reads and validates the format version byte.
     * Fails closed on unknown versions — a forward-compatibility guard.
     */
    private void readAndValidateVersion(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new DecryptionFailureException("Encrypted payload is empty or malformed");
        }
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new DecryptionFailureException(
                    "Unsupported payload version: 0x" + String.format("%02X", version)
                            + ". Expected: 0x" + String.format("%02X", FORMAT_VERSION));
        }
    }

    /**
     * Reads the 1-byte KID length and then reads that many bytes as a UTF-8 KID string.
     */
    private String readKid(ByteBuffer buffer) {
        if (buffer.remaining() < 1) {
            throw new DecryptionFailureException("Payload too short: missing KID length byte");
        }
        // Read as unsigned byte: & 0xFF converts signed Java byte (-128..127) to unsigned (0..255)
        int kidLength = buffer.get() & 0xFF;

        if (kidLength == 0) {
            throw new DecryptionFailureException("Payload contains an empty KID — cannot resolve key");
        }
        if (buffer.remaining() < kidLength) {
            throw new DecryptionFailureException(
                    "Payload too short: expected " + kidLength + " KID bytes, got " + buffer.remaining());
        }

        byte[] kidBytes = new byte[kidLength];
        buffer.get(kidBytes);
        return new String(kidBytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads exactly {@value #GCM_IV_LENGTH_BYTES} bytes as the IV.
     */
    private byte[] readIv(ByteBuffer buffer) {
        if (buffer.remaining() < GCM_IV_LENGTH_BYTES) {
            throw new DecryptionFailureException(
                    "Payload too short: expected " + GCM_IV_LENGTH_BYTES + "-byte IV, got " + buffer.remaining());
        }
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        buffer.get(iv);
        return iv;
    }

    /**
     * Reads all bytes remaining in the buffer — these are the ciphertext + GCM tag.
     * The GCM tag (16 bytes) is appended by the JCA provider and consumed by it on decrypt.
     */
    private byte[] readRemainingBytes(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new DecryptionFailureException(
                    "Payload too short: no ciphertext bytes after parsing header fields");
        }
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        return ciphertext;
    }

    /**
     * Initialises a fresh AES-GCM Cipher in DECRYPT_MODE, sets the AAD, and decrypts.
     *
     * <p>If the GCM tag does NOT match (i.e., ciphertext or AAD was tampered), the JCA provider
     * throws {@link AEADBadTagException} (a subclass of {@link BadPaddingException}).
     * This is caught and wrapped in {@link DecryptionFailureException} with a generic message to
     * prevent leaking information about WHICH part of the payload was tampered.</p>
     */
    private byte[] performDecryption(byte[] ciphertextWithTag, byte[] aad,
                                     SymmetricKeyMaterial keyMaterial, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.DECRYPT_MODE, keyMaterial.getEncryptionKey(), parameterSpec);

            // AAD must be the same bytes that were authenticated during encryption.
            cipher.updateAAD(aad);

            return cipher.doFinal(ciphertextWithTag);

        } catch (AEADBadTagException e) {
            // GCM tag mismatch — ciphertext, IV, or AAD was tampered.
            // Generic message: do NOT reveal whether it was tag, IV, or AAD that failed.
            throw new DecryptionFailureException(
                    "Decryption failed: authentication tag verification failed", e);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new DecryptionFailureException("AES-GCM transformation not available", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new DecryptionFailureException("Invalid key or IV for AES-GCM decryption", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // Catches any other padding/block-size error beyond AEADBadTagException.
            throw new DecryptionFailureException("AES-GCM decryption failed", e);
        }
    }

    // --------------------------------------------------------------------------------------------
    // Shared helpers
    // --------------------------------------------------------------------------------------------

    private void validatePayload(byte[] payload, String errorMessage) {
        if (payload == null || payload.length == 0) {
            throw new EncryptionFailureException(errorMessage);
        }
    }

    /**
     * Normalises a potentially null AAD input to an empty byte array.
     * This ensures the Cipher.updateAAD call is always well-defined.
     */
    private byte[] resolveAad(byte[] aad) {
        return (aad != null) ? aad : new byte[0];
    }
}

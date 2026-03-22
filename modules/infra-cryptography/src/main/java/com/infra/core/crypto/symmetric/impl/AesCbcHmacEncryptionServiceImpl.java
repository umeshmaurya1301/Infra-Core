package com.infra.core.crypto.symmetric.impl;

import com.infra.core.crypto.enums.MacAlgorithm;
import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.keys.SymmetricKeyMaterial;
import com.infra.core.crypto.mac.HmacService;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import com.infra.core.crypto.symmetric.SymmetricEncryptionService;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-CBC + HMAC-SHA256 symmetric encryption service using strict Encrypt-then-MAC (EtM).
 *
 * <h2>Purpose</h2>
 * <p>This service exists solely for backward compatibility with legacy systems or integrations
 * that do not support AES-GCM. For ALL new work, {@link AesGcmEncryptionServiceImpl} is the
 * primary and recommended service.</p>
 *
 * <h2>Why EtM (Encrypt-then-MAC) Is Non-Negotiable</h2>
 * <p>AES-CBC with PKCS5 padding is vulnerable to <b>Padding Oracle Attacks</b>. In a padding
 * oracle attack, an adversary submits modified ciphertexts and observes whether the server
 * responds with a "padding error" vs. a "MAC error" (or different response times). By
 * iterating, the attacker can decrypt the entire ciphertext byte-by-byte without the key.</p>
 *
 * <p>EtM prevents this by NEVER initialising the CBC decryption cipher until the HMAC has been
 * verified. If the ciphertext has been tampered, the HMAC will mismatch, and we throw a
 * {@link DecryptionFailureException} immediately — the {@link Cipher} is never instantiated,
 * and no padding oracle information is ever generated.</p>
 *
 * <h2>Strict Key Separation</h2>
 * <p>Two distinct keys are required:</p>
 * <ul>
 *   <li>{@code SymmetricKeyMaterial.getEncryptionKey()} — used ONLY for AES-CBC encrypt/decrypt</li>
 *   <li>{@code SymmetricKeyMaterial.getMacKey()} — used ONLY for HMAC-SHA256 authenticate/verify</li>
 * </ul>
 * <p>Using the same key for both encryption and MAC would break the IND-CCA2 security proof
 * of the EtM construction. This is enforced by the {@link SymmetricKeyMaterial} data model.</p>
 *
 * <h2>Binary Output Layout</h2>
 * <pre>
 * ┌──────────┬──────────┬────────────┬──────────┬──────────────────────┬──────────────┐
 * │ Ver (1B) │ KLen(1B) │ KID (var.) │ IV (16B) │ Ciphertext (var.)    │ HMAC (32B)   │
 * └──────────┴──────────┴────────────┴──────────┴──────────────────────┴──────────────┘
 *  ╰──────────────── authenticated payload ──────────────────────────╯  ╰── appended ─╯
 *
 * The HMAC is computed over [Version + KID Length + KID + IV + Ciphertext].
 * It is then appended at the very end of the output.
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link Cipher} is NOT thread-safe. A new instance is created per operation.
 * {@link SecureRandom} IS thread-safe and is shared as a static singleton.</p>
 */
@Service
public class AesCbcHmacEncryptionServiceImpl implements SymmetricEncryptionService {

    // --------------------------------------------------------------------------------------------
    // Constants
    // --------------------------------------------------------------------------------------------

    /** AES-CBC transformation string — contained here, never exposed. */
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /** Current binary format version. */
    private static final byte FORMAT_VERSION = 0x01;

    /** AES-CBC IV length: 16 bytes = AES block size. */
    private static final int CBC_IV_LENGTH_BYTES = 16;

    /** HMAC-SHA256 output length: 32 bytes (256 bits). */
    private static final int HMAC_LENGTH_BYTES = 32;

    /** HMAC algorithm used for EtM authentication — strictly HmacSHA256. */
    private static final MacAlgorithm MAC_ALGORITHM = MacAlgorithm.HMAC_SHA256;

    /**
     * Minimum payload size for decryption:
     * Version(1) + KID_LEN(1) + min KID(1) + IV(16) + min ciphertext(16 = one AES block) + HMAC(32) = 67
     * But a more conservative baseline without asserting KID content:
     * Version(1) + KID_LEN(1) + IV(16) + HMAC(32) = 50
     */
    private static final int MIN_PAYLOAD_BYTES = 1 + 1 + CBC_IV_LENGTH_BYTES + HMAC_LENGTH_BYTES;

    /** Default key resolution context. */
    private static final String DEFAULT_KEY_CONTEXT = "default";

    /** SecureRandom is thread-safe — shared singleton is correct. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // --------------------------------------------------------------------------------------------
    // Dependencies
    // --------------------------------------------------------------------------------------------

    private final SymmetricKeyProvider keyProvider;
    private final HmacService hmacService;

    public AesCbcHmacEncryptionServiceImpl(SymmetricKeyProvider keyProvider, HmacService hmacService) {
        this.keyProvider = keyProvider;
        this.hmacService = hmacService;
    }

    // --------------------------------------------------------------------------------------------
    // Public API
    // --------------------------------------------------------------------------------------------

    /**
     * Encrypts using AES-CBC, then authenticates with HMAC-SHA256 (EtM).
     *
     * <p><b>Note:</b> The {@code aad} parameter is NOT used by AES-CBC (it has no native AAD support
     * like GCM). However, the interface contract requires it. For this implementation, AAD is
     * silently ignored — authentication is provided by the HMAC over the entire
     * authenticated payload. If you need AAD binding, use the AES-GCM implementation.</p>
     */
    @Override
    public byte[] encrypt(byte[] payload, byte[] aad) {
        if (payload == null || payload.length == 0) {
            throw new EncryptionFailureException("Plaintext payload must not be null or empty");
        }

        // Step 1: Fetch the current active key (two separate keys: encryption + MAC).
        SymmetricKeyMaterial keyMaterial = keyProvider.getActiveKey(DEFAULT_KEY_CONTEXT);
        validateKeyMaterial(keyMaterial);

        // Step 2: Encode and validate the KID.
        byte[] kidBytes = encodeKid(keyMaterial.getKid());

        // Step 3: Generate a cryptographically random 16-byte IV.
        byte[] iv = generateIv();

        // Step 4: Encrypt plaintext with AES-CBC.
        byte[] ciphertext = performEncryption(payload, keyMaterial, iv);

        // Step 5: Compose the authenticated payload: [Version + KID_LEN + KID + IV + Ciphertext]
        byte[] authenticatedPayload = assembleAuthenticatedPayload(kidBytes, iv, ciphertext);

        // Step 6: Calculate HMAC over the full authenticated payload using the SEPARATE MAC key.
        byte[] hmac = hmacService.calculateMac(authenticatedPayload, keyMaterial.getMacKey(), MAC_ALGORITHM);

        // Step 7: Final output = authenticatedPayload + HMAC appended at the end.
        return appendHmac(authenticatedPayload, hmac);
    }

    /**
     * Decrypts using strict Encrypt-then-MAC verification.
     *
     * <p><b>CRITICAL:</b> The HMAC is verified BEFORE the Cipher is ever instantiated.
     * If the HMAC does not match, a {@link DecryptionFailureException} is thrown immediately,
     * and the CBC decryption path is NEVER executed. This is what prevents Padding Oracle attacks.</p>
     */
    @Override
    public byte[] decrypt(byte[] payload, byte[] aad) {
        if (payload == null || payload.length == 0) {
            throw new DecryptionFailureException("Encrypted payload must not be null or empty");
        }
        if (payload.length < MIN_PAYLOAD_BYTES) {
            throw new DecryptionFailureException(
                    "Encrypted payload too short: expected at least " + MIN_PAYLOAD_BYTES
                            + " bytes, got " + payload.length);
        }

        // ================================================================
        // Step 1: Split payload into [authenticatedPayload] and [attachedMac]
        // The HMAC is ALWAYS the last 32 bytes.
        // ================================================================
        byte[] attachedMac = Arrays.copyOfRange(payload, payload.length - HMAC_LENGTH_BYTES, payload.length);
        byte[] authenticatedPayload = Arrays.copyOfRange(payload, 0, payload.length - HMAC_LENGTH_BYTES);

        // ================================================================
        // Step 2: Parse version + KID from the authenticated payload to resolve the key.
        // We need the key BEFORE verifying the MAC (to get the MAC key).
        // ================================================================
        ByteBuffer headerBuffer = ByteBuffer.wrap(authenticatedPayload);

        // Read version
        if (!headerBuffer.hasRemaining()) {
            throw new DecryptionFailureException("Authenticated payload is empty after splitting HMAC");
        }
        byte version = headerBuffer.get();
        if (version != FORMAT_VERSION) {
            throw new DecryptionFailureException(
                    "Unsupported payload version: 0x" + String.format("%02X", version));
        }

        // Read KID
        if (!headerBuffer.hasRemaining()) {
            throw new DecryptionFailureException("Payload too short: missing KID length byte");
        }
        int kidLength = headerBuffer.get() & 0xFF; // unsigned
        if (kidLength == 0) {
            throw new DecryptionFailureException("Payload contains an empty KID — cannot resolve key");
        }
        if (headerBuffer.remaining() < kidLength) {
            throw new DecryptionFailureException(
                    "Payload too short: expected " + kidLength + " KID bytes, got " + headerBuffer.remaining());
        }
        byte[] kidBytes = new byte[kidLength];
        headerBuffer.get(kidBytes);
        String kid = new String(kidBytes, StandardCharsets.UTF_8);

        // Resolve the key material (may be a historical/rotated key).
        SymmetricKeyMaterial keyMaterial = keyProvider.getKeyById(kid);
        validateKeyMaterial(keyMaterial);

        // ================================================================
        // Step 3: VERIFY HMAC — THE MOST CRITICAL STEP.
        // If this fails, we NEVER touch the Cipher. No padding oracle is possible.
        // ================================================================
        boolean macValid = hmacService.verifyMac(
                authenticatedPayload, attachedMac, keyMaterial.getMacKey(), MAC_ALGORITHM);

        if (!macValid) {
            throw new DecryptionFailureException(
                    "HMAC verification failed — payload has been tampered or key is incorrect");
        }

        // ================================================================
        // Step 4: MAC verified. NOW it is safe to parse IV + Ciphertext and decrypt.
        // ================================================================

        // Read 16-byte IV
        if (headerBuffer.remaining() < CBC_IV_LENGTH_BYTES) {
            throw new DecryptionFailureException(
                    "Payload too short: expected " + CBC_IV_LENGTH_BYTES + "-byte IV");
        }
        byte[] iv = new byte[CBC_IV_LENGTH_BYTES];
        headerBuffer.get(iv);

        // Read remaining bytes as ciphertext
        if (!headerBuffer.hasRemaining()) {
            throw new DecryptionFailureException("Payload too short: no ciphertext bytes after IV");
        }
        byte[] ciphertext = new byte[headerBuffer.remaining()];
        headerBuffer.get(ciphertext);

        // Step 5: Decrypt with AES-CBC.
        return performDecryption(ciphertext, keyMaterial, iv);
    }

    // --------------------------------------------------------------------------------------------
    // Encryption internals
    // --------------------------------------------------------------------------------------------

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

    private byte[] generateIv() {
        byte[] iv = new byte[CBC_IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * Encrypts plaintext using AES-CBC. New Cipher instance per call — thread-safe by design.
     */
    private byte[] performEncryption(byte[] plaintext, SymmetricKeyMaterial keyMaterial, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keyMaterial.getEncryptionKey(), ivSpec);
            return cipher.doFinal(plaintext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new EncryptionFailureException("AES-CBC transformation not available", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new EncryptionFailureException("Invalid key or IV for AES-CBC encryption", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new EncryptionFailureException("AES-CBC encryption failed", e);
        }
    }

    /**
     * Composes the authenticated payload: [Version(1) + KID_LEN(1) + KID(K) + IV(16) + Ciphertext(C)]
     * This is the exact byte sequence that the HMAC is computed over.
     */
    private byte[] assembleAuthenticatedPayload(byte[] kidBytes, byte[] iv, byte[] ciphertext) {
        int length = 1 + 1 + kidBytes.length + CBC_IV_LENGTH_BYTES + ciphertext.length;
        ByteBuffer buffer = ByteBuffer.allocate(length);

        buffer.put(FORMAT_VERSION);
        buffer.put((byte) kidBytes.length);
        buffer.put(kidBytes);
        buffer.put(iv);
        buffer.put(ciphertext);

        return buffer.array();
    }

    /**
     * Appends the HMAC to the authenticated payload to produce the final output.
     */
    private byte[] appendHmac(byte[] authenticatedPayload, byte[] hmac) {
        byte[] result = new byte[authenticatedPayload.length + hmac.length];
        System.arraycopy(authenticatedPayload, 0, result, 0, authenticatedPayload.length);
        System.arraycopy(hmac, 0, result, authenticatedPayload.length, hmac.length);
        return result;
    }

    // --------------------------------------------------------------------------------------------
    // Decryption internals
    // --------------------------------------------------------------------------------------------

    /**
     * Decrypts ciphertext using AES-CBC. This method is ONLY called AFTER the HMAC has been
     * successfully verified, guaranteeing that the ciphertext has not been tampered.
     *
     * <p>Any {@link BadPaddingException} at this point indicates either a bug in our code
     * or key corruption — NOT an attack. We still wrap it generically.</p>
     */
    private byte[] performDecryption(byte[] ciphertext, SymmetricKeyMaterial keyMaterial, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keyMaterial.getEncryptionKey(), ivSpec);
            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new DecryptionFailureException("AES-CBC transformation not available", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new DecryptionFailureException("Invalid key or IV for AES-CBC decryption", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // After HMAC verification, a padding error indicates key corruption, not an attack.
            // Generic message: no oracle information is ever exposed.
            throw new DecryptionFailureException("AES-CBC decryption failed", e);
        }
    }

    // --------------------------------------------------------------------------------------------
    // Validation helpers
    // --------------------------------------------------------------------------------------------

    /**
     * Validates that the key material has BOTH an encryption key AND a MAC key.
     * EtM requires strict key separation — a missing MAC key is a configuration error.
     */
    private void validateKeyMaterial(SymmetricKeyMaterial keyMaterial) {
        if (keyMaterial == null) {
            throw new EncryptionFailureException("KeyProvider returned null SymmetricKeyMaterial");
        }
        if (keyMaterial.getEncryptionKey() == null) {
            throw new EncryptionFailureException("SymmetricKeyMaterial has a null encryption key");
        }
        if (keyMaterial.getMacKey() == null) {
            throw new EncryptionFailureException(
                    "SymmetricKeyMaterial has a null MAC key — AES-CBC-HMAC (EtM) requires "
                            + "a separate MAC key. Ensure the KeyProvider supplies both keys.");
        }
    }
}

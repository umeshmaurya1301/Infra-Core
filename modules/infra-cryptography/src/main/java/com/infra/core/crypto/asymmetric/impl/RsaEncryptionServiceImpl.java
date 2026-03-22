package com.infra.core.crypto.asymmetric.impl;

import com.infra.core.crypto.asymmetric.AsymmetricEncryptionService;
import com.infra.core.crypto.enums.RsaPadding;
import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.keys.AsymmetricKeyMaterial;
import com.infra.core.crypto.provider.AsymmetricKeyProvider;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;

/**
 * RSA encryption and decryption service.
 *
 * <h2>Scope Limitation</h2>
 * <p>RSA is only suitable for small payloads — typically used to encrypt a symmetric session key.
 * For large data, use hybrid encryption: AES-GCM for the data, RSA for the AES key.
 * This is enforced by preemptive payload size checks.</p>
 *
 * <h2>RSA Block Size Math</h2>
 * <p>All block size calculations are driven by {@link RsaPadding#maxPlaintextBytes(int)} using the
 * runtime key modulus size. No magic numbers exist in this class.</p>
 * <pre>
 * OAEP-SHA256: maxPlaintext = keySizeBytes - 66 (2 × 32-byte hash + 2)
 * OAEP-SHA512: maxPlaintext = keySizeBytes - 130 (2 × 64-byte hash + 2)
 * PKCS1_v1.5:  maxPlaintext = keySizeBytes - 11 (fixed overhead)
 *
 * RSA-2048 (256B): OAEP-SHA256 → 190B, OAEP-SHA512 → 126B, PKCS1 → 245B
 * RSA-3072 (384B): OAEP-SHA256 → 318B, OAEP-SHA512 → 254B, PKCS1 → 373B
 * RSA-4096 (512B): OAEP-SHA256 → 446B, OAEP-SHA512 → 382B, PKCS1 → 501B
 * </pre>
 *
 * <h2>Binary Output Layout</h2>
 * <pre>
 * ┌──────────┬──────────┬────────────┬─────────────────────┐
 * │ Ver (1B) │ KLen(1B) │ KID (var.) │ Ciphertext (rest)   │
 * └──────────┴──────────┴────────────┴─────────────────────┘
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link Cipher} is NOT thread-safe. A new instance is created per encrypt/decrypt call.</p>
 */
@Service
public class RsaEncryptionServiceImpl implements AsymmetricEncryptionService {

    private static final byte FORMAT_VERSION = 0x01;
    private static final String DEFAULT_KEY_CONTEXT = "default";

    private final AsymmetricKeyProvider keyProvider;

    public RsaEncryptionServiceImpl(AsymmetricKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public byte[] encrypt(byte[] payload, RsaPadding padding) {
        if (payload == null || payload.length == 0) {
            throw new EncryptionFailureException("Plaintext payload must not be null or empty");
        }
        if (padding == null) {
            throw new EncryptionFailureException("RsaPadding must not be null");
        }

        AsymmetricKeyMaterial keyMaterial = keyProvider.getActiveKey(DEFAULT_KEY_CONTEXT);

        // Extract the key modulus size at runtime to perform preemptive bounds checking.
        // Casting to RSAPublicKey is safe: we expect RSA keys from the provider for this service.
        int keySizeBytes = rsaKeySizeBytes(keyMaterial);
        int maxAllowed = padding.maxPlaintextBytes(keySizeBytes);

        if (payload.length > maxAllowed) {
            throw new EncryptionFailureException(
                    String.format(
                            "Payload too large for RSA encryption with %s on a %d-bit key: "
                                    + "payload=%d bytes, max=%d bytes. "
                                    + "Use hybrid encryption (AES-GCM + RSA) for larger payloads.",
                            padding.name(),
                            keySizeBytes * 8,
                            payload.length,
                            maxAllowed));
        }

        byte[] kidBytes = encodeKid(keyMaterial.getKid());
        byte[] ciphertext = performRsaEncryption(payload, keyMaterial, padding);
        return assemblePayload(kidBytes, ciphertext);
    }

    @Override
    public byte[] decrypt(byte[] payload, RsaPadding padding) {
        if (payload == null || payload.length == 0) {
            throw new DecryptionFailureException("Encrypted payload must not be null or empty");
        }
        if (padding == null) {
            throw new DecryptionFailureException("RsaPadding must not be null");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Read and validate version
        if (!buffer.hasRemaining()) {
            throw new DecryptionFailureException("Payload is empty");
        }
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new DecryptionFailureException(
                    "Unsupported payload version: 0x" + String.format("%02X", version));
        }

        // Read KID
        if (!buffer.hasRemaining()) {
            throw new DecryptionFailureException("Payload too short: missing KID length");
        }
        int kidLength = buffer.get() & 0xFF;  // unsigned
        if (kidLength == 0) {
            throw new DecryptionFailureException("Payload has empty KID — cannot resolve key");
        }
        if (buffer.remaining() < kidLength) {
            throw new DecryptionFailureException(
                    "Payload too short: expected " + kidLength + " KID bytes, got " + buffer.remaining());
        }
        byte[] kidBytes = new byte[kidLength];
        buffer.get(kidBytes);
        String kid = new String(kidBytes, StandardCharsets.UTF_8);

        // Resolve key — may be a historical/rotated key pair
        AsymmetricKeyMaterial keyMaterial = keyProvider.getKeyById(kid);

        // Read remaining bytes as ciphertext
        if (!buffer.hasRemaining()) {
            throw new DecryptionFailureException("Payload too short: no ciphertext after header");
        }
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        return performRsaDecryption(ciphertext, keyMaterial, padding);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private byte[] performRsaEncryption(byte[] plaintext, AsymmetricKeyMaterial km, RsaPadding padding) {
        try {
            Cipher cipher = Cipher.getInstance(padding.getTransformation());
            cipher.init(Cipher.ENCRYPT_MODE, km.getPublicKey());
            return cipher.doFinal(plaintext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new EncryptionFailureException("RSA transformation not available: " + padding.name(), e);
        } catch (InvalidKeyException e) {
            throw new EncryptionFailureException("Invalid RSA public key for encryption", e);
        } catch (IllegalBlockSizeException e) {
            // Should never occur: we do preemptive size check above.
            throw new EncryptionFailureException(
                    "RSA payload exceeds block size — preemptive size check missed this case", e);
        } catch (BadPaddingException e) {
            throw new EncryptionFailureException("RSA encryption failed during padding", e);
        }
    }

    private byte[] performRsaDecryption(byte[] ciphertext, AsymmetricKeyMaterial km, RsaPadding padding) {
        try {
            Cipher cipher = Cipher.getInstance(padding.getTransformation());
            cipher.init(Cipher.DECRYPT_MODE, km.getPrivateKey());
            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new DecryptionFailureException("RSA transformation not available: " + padding.name(), e);
        } catch (InvalidKeyException e) {
            throw new DecryptionFailureException("Invalid RSA private key for decryption", e);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            // Generic message — do not reveal whether it was a raw padding error or ciphertext issue.
            throw new DecryptionFailureException("RSA decryption failed", e);
        }
    }

    private byte[] encodeKid(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new EncryptionFailureException("KID from AsymmetricKeyProvider must not be null or blank");
        }
        byte[] kidBytes = kid.getBytes(StandardCharsets.UTF_8);
        if (kidBytes.length > 255) {
            throw new EncryptionFailureException("KID exceeds 255 bytes UTF-8 limit. Length: " + kidBytes.length);
        }
        return kidBytes;
    }

    private byte[] assemblePayload(byte[] kidBytes, byte[] ciphertext) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + kidBytes.length + ciphertext.length);
        buffer.put(FORMAT_VERSION);
        buffer.put((byte) kidBytes.length);
        buffer.put(kidBytes);
        buffer.put(ciphertext);
        return buffer.array();
    }

    /**
     * Extracts the RSA modulus size in bytes from the key material's public key.
     * Fails fast if the public key is not an RSA key (incompatible with this service).
     */
    private int rsaKeySizeBytes(AsymmetricKeyMaterial km) {
        if (!(km.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
            throw new EncryptionFailureException(
                    "AsymmetricKeyMaterial does not contain an RSA public key. "
                            + "RsaEncryptionServiceImpl requires RSA keys.");
        }
        return rsaPublicKey.getModulus().bitLength() / 8;
    }
}

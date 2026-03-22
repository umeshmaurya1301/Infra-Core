package com.infra.core.crypto.asymmetric.impl;

import com.infra.core.crypto.asymmetric.DigitalSignatureService;
import com.infra.core.crypto.exception.CryptographyException;
import com.infra.core.crypto.keys.AsymmetricKeyMaterial;
import com.infra.core.crypto.provider.AsymmetricKeyProvider;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

/**
 * Ed25519 digital signature service.
 *
 * <h2>Algorithm: Ed25519 (Edwards-curve Digital Signature Algorithm)</h2>
 * <p>Ed25519 is part of the EdDSA family (Edwards-curve DSA) defined in RFC 8032.
 * It provides several advantages over RSA-PSS:</p>
 * <ul>
 *   <li><b>Performance:</b> Significantly faster signing and verification than RSA.</li>
 *   <li><b>Key size:</b> 32-byte keys vs. 256-512 byte RSA keys.</li>
 *   <li><b>Security:</b> Deterministic — no random nonce generation during signing means
 *       no catastrophic nonce-reuse vulnerabilities (unlike ECDSA).</li>
 *   <li><b>Side-channel resistance:</b> Designed with constant-time implementation in mind.</li>
 * </ul>
 * <p>Ed25519 is natively supported in Java 15+ (JEP 339) without requiring BouncyCastle.</p>
 *
 * <h2>Binary Output Layout (sign())</h2>
 * <pre>
 * ┌──────────┬──────────┬────────────┬──────────────────────┐
 * │ Ver (1B) │ KLen(1B) │ KID (var.) │ Raw Signature (64B)  │
 * └──────────┴──────────┴────────────┴──────────────────────┘
 * </pre>
 * <p>Ed25519 raw signature length is always exactly 64 bytes.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link java.security.Signature} is NOT thread-safe. A new instance is created per operation.</p>
 */
@Service
public class EdDsaDigitalSignatureServiceImpl implements DigitalSignatureService {

    /** Ed25519 JCA algorithm string — contained strictly within this class. */
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    private static final byte FORMAT_VERSION = 0x01;
    private static final String DEFAULT_KEY_CONTEXT = "default";

    private final AsymmetricKeyProvider keyProvider;

    public EdDsaDigitalSignatureServiceImpl(AsymmetricKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public byte[] sign(byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw new CryptographyException("Payload to sign must not be null or empty");
        }

        AsymmetricKeyMaterial keyMaterial = keyProvider.getActiveKey(DEFAULT_KEY_CONTEXT);
        byte[] kidBytes = encodeKid(keyMaterial.getKid());
        byte[] rawSignature = computeSignature(payload, keyMaterial);
        return assembleSignaturePayload(kidBytes, rawSignature);
    }

    @Override
    public boolean verify(byte[] payload, byte[] signaturePayload) {
        if (payload == null || payload.length == 0) {
            throw new CryptographyException("Payload to verify must not be null or empty");
        }
        if (signaturePayload == null || signaturePayload.length == 0) {
            throw new CryptographyException("Signature payload must not be null or empty");
        }

        ByteBuffer buffer = ByteBuffer.wrap(signaturePayload);
        ParsedSignaturePayload parsed = parseSignaturePayload(buffer);
        AsymmetricKeyMaterial keyMaterial = keyProvider.getKeyById(parsed.kid());
        return verifySignature(payload, parsed.rawSignature(), keyMaterial);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private byte[] computeSignature(byte[] payload, AsymmetricKeyMaterial km) {
        try {
            // New Signature per call — Signature is NOT thread-safe.
            java.security.Signature sig = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initSign(km.getPrivateKey());
            sig.update(payload);
            return sig.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptographyException(
                    "Ed25519 algorithm not available. Requires Java 15+ (JEP 339).", e);
        } catch (InvalidKeyException e) {
            throw new CryptographyException("Invalid Ed25519 private key for signing", e);
        } catch (SignatureException e) {
            throw new CryptographyException("Ed25519 signing failed", e);
        }
    }

    private boolean verifySignature(byte[] payload, byte[] rawSignature, AsymmetricKeyMaterial km) {
        try {
            java.security.Signature sig = java.security.Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(km.getPublicKey());
            sig.update(payload);
            return sig.verify(rawSignature);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptographyException(
                    "Ed25519 algorithm not available. Requires Java 15+ (JEP 339).", e);
        } catch (InvalidKeyException e) {
            throw new CryptographyException("Invalid Ed25519 public key for verification", e);
        } catch (SignatureException e) {
            // A structurally malformed Ed25519 signature — treat as invalid, not an exception.
            return false;
        }
    }

    private byte[] encodeKid(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new CryptographyException("KID from AsymmetricKeyProvider must not be null or blank");
        }
        byte[] kidBytes = kid.getBytes(StandardCharsets.UTF_8);
        if (kidBytes.length > 255) {
            throw new CryptographyException("KID exceeds 255 bytes UTF-8 limit. Length: " + kidBytes.length);
        }
        return kidBytes;
    }

    private byte[] assembleSignaturePayload(byte[] kidBytes, byte[] rawSignature) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + kidBytes.length + rawSignature.length);
        buffer.put(FORMAT_VERSION);
        buffer.put((byte) kidBytes.length);
        buffer.put(kidBytes);
        buffer.put(rawSignature);
        return buffer.array();
    }

    private ParsedSignaturePayload parseSignaturePayload(ByteBuffer buffer) {
        // Version
        if (!buffer.hasRemaining()) {
            throw new CryptographyException("Signature payload is empty");
        }
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new CryptographyException(
                    "Unsupported signature payload version: 0x" + String.format("%02X", version));
        }

        // KID length
        if (!buffer.hasRemaining()) {
            throw new CryptographyException("Signature payload too short: missing KID length");
        }
        int kidLength = buffer.get() & 0xFF;
        if (kidLength == 0) {
            throw new CryptographyException("Signature payload has empty KID — cannot resolve key");
        }
        if (buffer.remaining() < kidLength) {
            throw new CryptographyException(
                    "Signature payload too short: expected " + kidLength + " KID bytes");
        }

        // KID bytes
        byte[] kidBytes = new byte[kidLength];
        buffer.get(kidBytes);
        String kid = new String(kidBytes, StandardCharsets.UTF_8);

        // Raw signature (rest) — Ed25519 raw signature is always 64 bytes
        if (!buffer.hasRemaining()) {
            throw new CryptographyException("Signature payload too short: no signature bytes after header");
        }
        byte[] rawSignature = new byte[buffer.remaining()];
        buffer.get(rawSignature);

        return new ParsedSignaturePayload(kid, rawSignature);
    }

    private record ParsedSignaturePayload(String kid, byte[] rawSignature) {}
}

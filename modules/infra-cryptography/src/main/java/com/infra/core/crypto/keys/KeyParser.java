package com.infra.core.crypto.keys;

import com.infra.core.crypto.enums.AsymmetricAlgorithm;
import com.infra.core.crypto.enums.MacAlgorithm;
import com.infra.core.crypto.enums.SymmetricAlgorithm;
import com.infra.core.crypto.exception.KeyParsingException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Utility for parsing keys from raw bytes and PEM strings safely.
 * Strict casting and algorithm validation are enforced.
 */
public final class KeyParser {

    private KeyParser() {
        // Prevent instantiation
    }

    /**
     * Reconstructs a SecretKey from raw bytes.
     */
    public static SecretKey parseSymmetricKey(byte[] rawKey, SymmetricAlgorithm algorithm) {
        if (rawKey == null || algorithm == null) {
            throw new KeyParsingException("Raw key and algorithm must not be null");
        }
        // Basic length validation according to common requirements can be added
        if (rawKey.length == 0) {
            throw new KeyParsingException("Symmetric raw key is empty");
        }
        // Extract base algorithm name (e.g., "AES" from "AES")
        return new SecretKeySpec(rawKey, algorithm.name().split("_")[0]);
    }

    /**
     * Reconstructs a MAC SecretKey from raw bytes.
     */
    public static SecretKey parseMacKey(byte[] rawKey, MacAlgorithm algorithm) {
        if (rawKey == null || algorithm == null) {
            throw new KeyParsingException("Raw key and algorithm must not be null");
        }
        if (rawKey.length == 0) {
            throw new KeyParsingException("MAC raw key is empty");
        }
        return new SecretKeySpec(rawKey, algorithm.getJcaName());
    }

    // -------------------------------------------------------------------------
    // Asymmetric DER Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses an X.509 encoded byte array into a PublicKey.
     */
    public static PublicKey parsePublicKey(byte[] x509Der, AsymmetricAlgorithm algorithm) {
        if (x509Der == null || x509Der.length == 0) {
            throw new KeyParsingException("X.509 DER bytes must not be null or empty");
        }
        return generatePublicKey(new X509EncodedKeySpec(x509Der), algorithm);
    }

    /**
     * Parses a PKCS#8 encoded byte array into a PrivateKey.
     */
    public static PrivateKey parsePrivateKey(byte[] pkcs8Der, AsymmetricAlgorithm algorithm) {
        if (pkcs8Der == null || pkcs8Der.length == 0) {
            throw new KeyParsingException("PKCS#8 DER bytes must not be null or empty");
        }
        return generatePrivateKey(new PKCS8EncodedKeySpec(pkcs8Der), algorithm);
    }

    // -------------------------------------------------------------------------
    // Asymmetric PEM Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a PEM-encoded string into a PublicKey.
     * Expects standard X.509 format (e.g. "BEGIN PUBLIC KEY").
     */
    public static PublicKey parsePublicKeyPem(String pem, AsymmetricAlgorithm algorithm) {
        if (pem == null || pem.isBlank()) {
            throw new KeyParsingException("PEM string must not be null or blank");
        }
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = reader.readPemObject();
            if (pemObject == null) {
                throw new KeyParsingException("Invalid PEM format: could not read object");
            }
            if (!pemObject.getType().contains("PUBLIC KEY")) {
                throw new KeyParsingException("Expected PUBLIC KEY, but got: " + pemObject.getType());
            }
            return generatePublicKey(new X509EncodedKeySpec(pemObject.getContent()), algorithm);
        } catch (IOException e) {
            throw new KeyParsingException("Failed to parse PEM string", e);
        }
    }

    /**
     * Parses a PEM-encoded string into a PrivateKey.
     * Expects PKCS#8 format (e.g. "BEGIN PRIVATE KEY" or "BEGIN RSA PRIVATE KEY").
     */
    public static PrivateKey parsePrivateKeyPem(String pem, AsymmetricAlgorithm algorithm) {
        if (pem == null || pem.isBlank()) {
            throw new KeyParsingException("PEM string must not be null or blank");
        }
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = reader.readPemObject();
            if (pemObject == null) {
                throw new KeyParsingException("Invalid PEM format: could not read object");
            }
            if (!pemObject.getType().contains("PRIVATE KEY")) {
                throw new KeyParsingException("Expected PRIVATE KEY, but got: " + pemObject.getType());
            }
            return generatePrivateKey(new PKCS8EncodedKeySpec(pemObject.getContent()), algorithm);
        } catch (IOException e) {
            throw new KeyParsingException("Failed to parse PEM string", e);
        }
    }

    // -------------------------------------------------------------------------
    // KeyFactory Helpers
    // -------------------------------------------------------------------------

    private static PublicKey generatePublicKey(X509EncodedKeySpec spec, AsymmetricAlgorithm algorithm) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getJcaName());
            PublicKey key = keyFactory.generatePublic(spec);
            validateAlgorithm(key.getAlgorithm(), algorithm);
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new KeyParsingException("Algorithm not supported: " + algorithm.getJcaName(), e);
        } catch (InvalidKeySpecException e) {
            throw new KeyParsingException("Invalid X.509 public key specification", e);
        }
    }

    private static PrivateKey generatePrivateKey(PKCS8EncodedKeySpec spec, AsymmetricAlgorithm algorithm) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getJcaName());
            PrivateKey key = keyFactory.generatePrivate(spec);
            validateAlgorithm(key.getAlgorithm(), algorithm);
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new KeyParsingException("Algorithm not supported: " + algorithm.getJcaName(), e);
        } catch (InvalidKeySpecException e) {
            throw new KeyParsingException("Invalid PKCS#8 private key specification", e);
        }
    }

    /**
     * Strict validation: if we requested an RSA key but got an EC key (perhaps the user passed the wrong Enum),
     * fail immediately to prevent type mismatch downstream.
     */
    private static void validateAlgorithm(String parsedAlgorithm, AsymmetricAlgorithm expectedAlgorithm) {
        // Some providers yield "RSA" for RSASSA-PSS, some yield "RSASSA-PSS".
        // Ed25519 yields "Ed25519" or "EdDSA".
        // We do a loose containment or exact match.
        String expectedJcaName = expectedAlgorithm.getJcaName();
        if ("RSASSA-PSS".equalsIgnoreCase(expectedJcaName) && "RSA".equalsIgnoreCase(parsedAlgorithm)) {
            return; // Permitted compatibility.
        }
        if (!parsedAlgorithm.equalsIgnoreCase(expectedJcaName) && !expectedJcaName.contains(parsedAlgorithm)) {
            throw new KeyParsingException(
                    String.format("Key algorithm mismatch. Expected: %s, Found inside parsed key: %s",
                            expectedJcaName, parsedAlgorithm));
        }
    }
}

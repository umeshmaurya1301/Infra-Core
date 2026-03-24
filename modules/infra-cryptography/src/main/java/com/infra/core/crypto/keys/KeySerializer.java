package com.infra.core.crypto.keys;

import com.infra.core.crypto.exception.KeyParsingException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Utility for serializing keys to raw bytes (DER) and PEM strings safely.
 * Strict encoding structure is enforced.
 */
public final class KeySerializer {

    private KeySerializer() {
        // Prevent instantiation
    }

    /**
     * Serializes a Symmetric/SecretKey or MacKey to raw bytes.
     */
    public static byte[] serializeSymmetricKey(SecretKey key) {
        if (key == null) {
            throw new KeyParsingException("Cannot serialize a null symmetric key");
        }
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new KeyParsingException("Symmetric key cannot be encoded (possibly a hardware-backed key)");
        }
        return encoded;
    }

    /**
     * Serializes a PublicKey to an X.509 DER byte array.
     */
    public static byte[] serializePublicKey(PublicKey key) {
        if (key == null) {
            throw new KeyParsingException("Cannot serialize a null public key");
        }
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new KeyParsingException("Public key cannot be encoded");
        }
        return encoded;
    }

    /**
     * Serializes a PrivateKey to a PKCS#8 DER byte array.
     */
    public static byte[] serializePrivateKey(PrivateKey key) {
        if (key == null) {
            throw new KeyParsingException("Cannot serialize a null private key");
        }
        byte[] encoded = key.getEncoded();
        if (encoded == null) {
            throw new KeyParsingException("Private key cannot be encoded (possibly a hardware-backed or restricted key)");
        }
        return encoded;
    }

    /**
     * Serializes a PublicKey to a standard X.509 PEM string.
     */
    public static String serializePublicKeyPem(PublicKey key) {
        return serializePem("PUBLIC KEY", serializePublicKey(key));
    }

    /**
     * Serializes a PrivateKey to a standard PKCS#8 PEM string.
     */
    public static String serializePrivateKeyPem(PrivateKey key) {
        return serializePem("PRIVATE KEY", serializePrivateKey(key));
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static String serializePem(String type, byte[] content) {
        try (StringWriter sw = new StringWriter();
             PemWriter pw = new PemWriter(sw)) {
            pw.writeObject(new PemObject(type, content));
            pw.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new KeyParsingException("Failed to serialize to PEM format", e);
        }
    }
}

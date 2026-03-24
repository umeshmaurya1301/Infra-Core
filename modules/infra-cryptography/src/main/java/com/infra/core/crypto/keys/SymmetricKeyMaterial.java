package com.infra.core.crypto.keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Material model for Symmetric key operations.
 * Includes a primary SecretKey (for encryption or MAC) and an optional MAC key for EtM patterns.
 */
public class SymmetricKeyMaterial {
    private final String kid;
    private final SecretKey secretKey;
    private final SecretKey macKey;

    public SymmetricKeyMaterial(String kid, SecretKey secretKey, SecretKey macKey) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("KID must be non-null and non-blank");
        }
        if (kid.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("KID must be <= 255 bytes when UTF-8 encoded");
        }
        if (secretKey == null) {
            throw new IllegalArgumentException("Primary secretKey must not be null");
        }
        this.kid = kid;
        this.secretKey = secretKey;
        this.macKey = macKey;
    }

    public String getKid() {
        return kid;
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    /**
     * @return the encryption key (maps to the primary secret key).
     */
    public SecretKey getEncryptionKey() {
        return secretKey;
    }

    public SecretKey getMacKey() {
        return macKey;
    }
}

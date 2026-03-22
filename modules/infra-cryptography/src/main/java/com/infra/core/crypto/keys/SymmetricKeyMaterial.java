package com.infra.core.crypto.keys;

import javax.crypto.SecretKey;

/**
 * Material model for Symmetric key operations.
 * Includes both Encryption and Authentication keys for EtM patterns.
 */
public class SymmetricKeyMaterial {
    private final String kid;
    private final SecretKey encryptionKey;
    private final SecretKey macKey; // Used for CBC-HMAC EtM if applicable

    public SymmetricKeyMaterial(String kid, SecretKey encryptionKey, SecretKey macKey) {
        if (kid == null || kid.getBytes().length > 255) {
            throw new IllegalArgumentException("KID must be non-null and <= 255 bytes");
        }
        this.kid = kid;
        this.encryptionKey = encryptionKey;
        this.macKey = macKey;
    }

    public String getKid() {
        return kid;
    }

    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    public SecretKey getMacKey() {
        return macKey;
    }
}

package com.infra.core.crypto.keys;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Material model for Asymmetric key operations.
 * Includes explicit Public and Private Key pairings.
 */
public class AsymmetricKeyMaterial {
    private final String kid;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    public AsymmetricKeyMaterial(String kid, PublicKey publicKey, PrivateKey privateKey) {
        if (kid == null || kid.getBytes().length > 255) {
            throw new IllegalArgumentException("KID must be non-null and <= 255 bytes");
        }
        this.kid = kid;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public String getKid() {
        return kid;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}

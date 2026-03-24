package com.infra.core.crypto.keys;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Material model for Asymmetric key operations.
 * Keys are optional, as some providers may only have a PublicKey (for verifying/encrypting)
 * or only a PrivateKey (for signing/decrypting).
 */
public class AsymmetricKeyMaterial {
    private final String kid;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    public AsymmetricKeyMaterial(String kid, PublicKey publicKey, PrivateKey privateKey) {
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("KID must be non-null and non-blank");
        }
        if (kid.getBytes(StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("KID must be <= 255 bytes when UTF-8 encoded");
        }
        if (publicKey == null && privateKey == null) {
            throw new IllegalArgumentException("At least one of publicKey or privateKey must be provided");
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

package com.infra.core.crypto;

import com.infra.core.crypto.keys.AsymmetricKeyMaterial;
import com.infra.core.crypto.keys.SymmetricKeyMaterial;
import com.infra.core.crypto.provider.AsymmetricKeyProvider;
import com.infra.core.crypto.provider.SymmetricKeyProvider;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * In-memory key provider stubs for unit tests.
 * Uses deterministic key material generated once per JVM — no mocking framework required.
 */
public final class TestKeyProviders {

    private static final String TEST_KID = "test-key-001";

    // ── Symmetric (AES-256) ──────────────────────────────────────────────────
    private static final SecretKey AES_ENCRYPTION_KEY;
    private static final SecretKey AES_MAC_KEY;

    // ── Asymmetric (RSA-2048) ────────────────────────────────────────────────
    private static final KeyPair RSA_KEY_PAIR;

    static {
        try {
            KeyGenerator aesGen = KeyGenerator.getInstance("AES");
            aesGen.init(256);
            AES_ENCRYPTION_KEY = aesGen.generateKey();
            AES_MAC_KEY = aesGen.generateKey();

            KeyPairGenerator rsaGen = KeyPairGenerator.getInstance("RSA");
            rsaGen.initialize(2048);
            RSA_KEY_PAIR = rsaGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError("JVM missing AES or RSA provider: " + e.getMessage());
        }
    }

    private TestKeyProviders() { /* utility class */ }

    // ─────────────────────────────────────────────────────────────────────────
    // Public factories
    // ─────────────────────────────────────────────────────────────────────────

    public static String testKid() {
        return TEST_KID;
    }

    public static SecretKey aesEncryptionKey() {
        return AES_ENCRYPTION_KEY;
    }

    public static SecretKey aesMacKey() {
        return AES_MAC_KEY;
    }

    public static KeyPair rsaKeyPair() {
        return RSA_KEY_PAIR;
    }

    /**
     * Returns a SymmetricKeyProvider backed by a single AES-256 key pair (encryption + MAC).
     * Both {@code getActiveKey} and {@code getKeyById} resolve to the same material.
     */
    public static SymmetricKeyProvider symmetricProvider() {
        SymmetricKeyMaterial material = new SymmetricKeyMaterial(TEST_KID, AES_ENCRYPTION_KEY, AES_MAC_KEY);
        return new SymmetricKeyProvider() {
            @Override
            public SymmetricKeyMaterial getActiveKey(String context) {
                return material;
            }

            @Override
            public SymmetricKeyMaterial getKeyById(String keyId) {
                if (!TEST_KID.equals(keyId)) {
                    throw new com.infra.core.crypto.exception.KeyResolutionException(
                            "Unknown KID in test provider: " + keyId);
                }
                return material;
            }
        };
    }

    /**
     * Returns a SymmetricKeyProvider with only an encryption key (no MAC key).
     * Suitable for AES-GCM tests where MAC key is not required.
     */
    public static SymmetricKeyProvider symmetricProviderGcmOnly() {
        SymmetricKeyMaterial material = new SymmetricKeyMaterial(TEST_KID, AES_ENCRYPTION_KEY, null);
        return new SymmetricKeyProvider() {
            @Override
            public SymmetricKeyMaterial getActiveKey(String context) {
                return material;
            }

            @Override
            public SymmetricKeyMaterial getKeyById(String keyId) {
                if (!TEST_KID.equals(keyId)) {
                    throw new com.infra.core.crypto.exception.KeyResolutionException(
                            "Unknown KID in test provider: " + keyId);
                }
                return material;
            }
        };
    }

    /**
     * Returns an AsymmetricKeyProvider backed by a single RSA-2048 key pair.
     */
    public static AsymmetricKeyProvider rsaProvider() {
        AsymmetricKeyMaterial material = new AsymmetricKeyMaterial(
                TEST_KID, RSA_KEY_PAIR.getPublic(), RSA_KEY_PAIR.getPrivate());
        return new AsymmetricKeyProvider() {
            @Override
            public AsymmetricKeyMaterial getActiveKey(String context) {
                return material;
            }

            @Override
            public AsymmetricKeyMaterial getKeyById(String keyId) {
                if (!TEST_KID.equals(keyId)) {
                    throw new com.infra.core.crypto.exception.KeyResolutionException(
                            "Unknown KID in test provider: " + keyId);
                }
                return material;
            }
        };
    }
}

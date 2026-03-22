package com.infra.core.crypto.enums;

public enum RsaPadding {

    /**
     * OAEP with SHA-256 and MGF1 with SHA-256 (Recommended).
     * Overhead: 2 * SHA-256 hash length (32 bytes each) + 2 = 66 bytes.
     */
    OAEP_SHA256("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", 66),

    /**
     * OAEP with SHA-512 and MGF1 with SHA-512.
     * Overhead: 2 * SHA-512 hash length (64 bytes each) + 2 = 130 bytes.
     */
    OAEP_SHA512("RSA/ECB/OAEPWithSHA-512AndMGF1Padding", 130),

    /**
     * PKCS#1 v1.5 padding. Legacy only — vulnerable to Bleichenbacher attacks.
     * Overhead: 11 bytes (fixed).
     * @deprecated Use OAEP padding for all new integrations.
     */
    @Deprecated
    PKCS1_V1_5("RSA/ECB/PKCS1Padding", 11);

    /**
     * The JCA transformation string. Kept inside the enum — never used as a raw string externally.
     */
    private final String transformation;

    /**
     * Total bytes consumed by this padding scheme per RSA block.
     * Formula: maxPlaintext = keySizeBytes - overheadBytes
     */
    private final int overheadBytes;

    RsaPadding(String transformation, int overheadBytes) {
        this.transformation = transformation;
        this.overheadBytes = overheadBytes;
    }

    public String getTransformation() {
        return transformation;
    }

    /**
     * Computes the maximum number of plaintext bytes this padding allows for a given key size.
     *
     * @param keySizeBytes the RSA modulus size in bytes (e.g. 256 for RSA-2048).
     * @return maximum plaintext byte count.
     */
    public int maxPlaintextBytes(int keySizeBytes) {
        return keySizeBytes - overheadBytes;
    }
}

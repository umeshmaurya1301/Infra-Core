package com.infra.core.crypto.enums;

public enum SymmetricAlgorithm {
    AES_256_GCM("AES/GCM/NoPadding", 32),
    AES_256_CBC("AES/CBC/PKCS5Padding", 32);

    private final String transformation;
    private final int keySizeMaturityBytes;

    SymmetricAlgorithm(String transformation, int keySizeMaturityBytes) {
        this.transformation = transformation;
        this.keySizeMaturityBytes = keySizeMaturityBytes;
    }

    public String getTransformation() {
        return transformation;
    }

    public int getKeySizeMaturityBytes() {
        return keySizeMaturityBytes;
    }
}

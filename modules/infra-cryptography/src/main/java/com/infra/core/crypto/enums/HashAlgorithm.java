package com.infra.core.crypto.enums;

public enum HashAlgorithm {
    SHA_256("SHA-256"),
    SHA_512("SHA-512"),
    SHA3_256("SHA3-256"),
    SHA3_512("SHA3-512");

    private final String algorithmName;

    HashAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}

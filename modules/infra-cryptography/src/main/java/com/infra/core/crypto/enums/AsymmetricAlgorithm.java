package com.infra.core.crypto.enums;

public enum AsymmetricAlgorithm {
    RSA("RSA"),
    ED25519("Ed25519");

    private final String algorithmName;

    AsymmetricAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}

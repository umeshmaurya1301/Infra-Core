package com.infra.core.crypto.enums;

public enum MacAlgorithm {
    HMAC_SHA256("HmacSHA256"),
    HMAC_SHA512("HmacSHA512");

    private final String algorithmName;

    MacAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}

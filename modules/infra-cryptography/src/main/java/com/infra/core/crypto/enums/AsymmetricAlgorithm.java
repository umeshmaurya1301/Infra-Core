package com.infra.core.crypto.enums;


public enum AsymmetricAlgorithm {
    RSA("RSA"),
    ED25519("Ed25519");

    private final String jcaName;

    AsymmetricAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    /** @return JCA algorithm name for use with {@link java.security.KeyFactory#getInstance(String)}. */
    public String getJcaName() {
        return jcaName;
    }

    /** @deprecated Use {@link #getJcaName()} instead. */
    @Deprecated
    public String getAlgorithmName() {
        return jcaName;
    }
}

package com.infra.core.crypto.enums;

public enum MacAlgorithm {
    HMAC_SHA256("HmacSHA256"),
    HMAC_SHA512("HmacSHA512");

    private final String jcaName;

    MacAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    /** @return JCA algorithm name for use with {@link javax.crypto.Mac#getInstance(String)}. */
    public String getJcaName() {
        return jcaName;
    }

    /** @deprecated Use {@link #getJcaName()} instead. */
    @Deprecated
    public String getAlgorithmName() {
        return jcaName;
    }
}

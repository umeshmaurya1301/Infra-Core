package com.infra.core.crypto.enums;

public enum RsaPadding {
    /**
     * OAEP with SHA-256 and MGF1 with SHA-256 padding (Recommended).
     */
    OAEP_SHA256("OAEPWithSHA-256AndMGF1Padding"),

    /**
     * OAEP with SHA-512 and MGF1 with SHA-512 padding.
     */
    OAEP_SHA512("OAEPWithSHA-512AndMGF1Padding"),

    /**
     * PKCS#1 v1.5 padding.
     * @deprecated Use OAEP padding for enhanced security against padding oracle attacks.
     */
    @Deprecated
    PKCS1_V1_5("PKCS1Padding");

    private final String paddingName;

    RsaPadding(String paddingName) {
        this.paddingName = paddingName;
    }

    public String getPaddingName() {
        return paddingName;
    }
}

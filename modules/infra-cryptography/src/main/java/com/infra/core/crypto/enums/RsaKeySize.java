package com.infra.core.crypto.enums;

public enum RsaKeySize {
    RSA_2048(2048),
    RSA_3072(3072),
    RSA_4096(4096);

    private final int size;

    RsaKeySize(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}

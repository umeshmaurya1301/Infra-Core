package com.infra.core.crypto.config;

import com.infra.core.crypto.enums.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "infra.crypto")
public class InfraCryptoProperties {

    private final HashingProperties hashing = new HashingProperties();
    private final PasswordProperties password = new PasswordProperties();
    private final SymmetricProperties symmetric = new SymmetricProperties();
    private final AsymmetricProperties asymmetric = new AsymmetricProperties();

    public HashingProperties getHashing() {
        return hashing;
    }

    public PasswordProperties getPassword() {
        return password;
    }

    public SymmetricProperties getSymmetric() {
        return symmetric;
    }

    public AsymmetricProperties getAsymmetric() {
        return asymmetric;
    }

    public static class HashingProperties {
        private HashAlgorithm defaultAlgorithm = HashAlgorithm.SHA_256;

        public HashAlgorithm getDefaultAlgorithm() {
            return defaultAlgorithm;
        }

        public void setDefaultAlgorithm(HashAlgorithm defaultAlgorithm) {
            this.defaultAlgorithm = defaultAlgorithm;
        }
    }

    public static class PasswordProperties {
        private PasswordHashAlgorithm algorithm = PasswordHashAlgorithm.ARGON2ID;
        private int saltLengthBytes = 16;
        private int hashLengthBytes = 32;
        private int memoryKb = 65536; // 64MB
        private int iterations = 3;
        private int parallelism = 1;

        public PasswordHashAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(PasswordHashAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        public int getSaltLengthBytes() {
            return saltLengthBytes;
        }

        public void setSaltLengthBytes(int saltLengthBytes) {
            this.saltLengthBytes = saltLengthBytes;
        }

        public int getHashLengthBytes() {
            return hashLengthBytes;
        }

        public void setHashLengthBytes(int hashLengthBytes) {
            this.hashLengthBytes = hashLengthBytes;
        }

        public int getMemoryKb() {
            return memoryKb;
        }

        public void setMemoryKb(int memoryKb) {
            this.memoryKb = memoryKb;
        }

        public int getIterations() {
            return iterations;
        }

        public void setIterations(int iterations) {
            this.iterations = iterations;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }
    }

    public static class SymmetricProperties {
        private SymmetricAlgorithm defaultAlgorithm = SymmetricAlgorithm.AES_256_GCM;
        private IvStrategy defaultIvStrategy = IvStrategy.RANDOM;

        public SymmetricAlgorithm getDefaultAlgorithm() {
            return defaultAlgorithm;
        }

        public void setDefaultAlgorithm(SymmetricAlgorithm defaultAlgorithm) {
            this.defaultAlgorithm = defaultAlgorithm;
        }

        public IvStrategy getDefaultIvStrategy() {
            return defaultIvStrategy;
        }

        public void setDefaultIvStrategy(IvStrategy defaultIvStrategy) {
            this.defaultIvStrategy = defaultIvStrategy;
        }
    }

    public static class AsymmetricProperties {
        private AsymmetricAlgorithm defaultAlgorithm = AsymmetricAlgorithm.RSA;
        private RsaPadding defaultRsaPadding = RsaPadding.OAEP_SHA256;
        private RsaKeySize defaultRsaKeySize = RsaKeySize.RSA_2048;

        public AsymmetricAlgorithm getDefaultAlgorithm() {
            return defaultAlgorithm;
        }

        public void setDefaultAlgorithm(AsymmetricAlgorithm defaultAlgorithm) {
            this.defaultAlgorithm = defaultAlgorithm;
        }

        public RsaPadding getDefaultRsaPadding() {
            return defaultRsaPadding;
        }

        public void setDefaultRsaPadding(RsaPadding defaultRsaPadding) {
            this.defaultRsaPadding = defaultRsaPadding;
        }

        public RsaKeySize getDefaultRsaKeySize() {
            return defaultRsaKeySize;
        }

        public void setDefaultRsaKeySize(RsaKeySize defaultRsaKeySize) {
            this.defaultRsaKeySize = defaultRsaKeySize;
        }
    }
}

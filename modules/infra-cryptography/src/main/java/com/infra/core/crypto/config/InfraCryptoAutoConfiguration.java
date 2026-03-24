package com.infra.core.crypto.config;

import com.infra.core.crypto.asymmetric.AsymmetricEncryptionService;
import com.infra.core.crypto.asymmetric.DigitalSignatureService;
import com.infra.core.crypto.asymmetric.impl.EdDsaDigitalSignatureServiceImpl;
import com.infra.core.crypto.asymmetric.impl.RsaDigitalSignatureServiceImpl;
import com.infra.core.crypto.asymmetric.impl.RsaEncryptionServiceImpl;
import com.infra.core.crypto.hashing.PasswordHashingService;
import com.infra.core.crypto.hashing.StandardHashingService;
import com.infra.core.crypto.hashing.impl.Argon2PasswordHashingServiceImpl;
import com.infra.core.crypto.hashing.impl.StandardHashingServiceImpl;
import com.infra.core.crypto.mac.HmacService;
import com.infra.core.crypto.mac.impl.HmacServiceImpl;
import com.infra.core.crypto.provider.AsymmetricKeyProvider;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import com.infra.core.crypto.symmetric.SymmetricEncryptionService;
import com.infra.core.crypto.symmetric.impl.AesCbcHmacEncryptionServiceImpl;
import com.infra.core.crypto.symmetric.impl.AesGcmEncryptionServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot Auto-Configuration for the infra-cryptography library.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>No Global Provider Mutation:</b> {@code Security.addProvider(new BouncyCastleProvider())}
 *       is strictly forbidden. BouncyCastle is invoked only through its direct APIs internally
 *       (e.g., {@code Argon2BytesGenerator}, {@code PemWriter}) — never by registering it as a
 *       global JCA provider. This prevents JVM-wide side effects in multi-module applications.</li>
 *   <li><b>Fail-Safe Defaults:</b> Every bean is guarded with {@link ConditionalOnMissingBean}
 *       so consuming applications can {@code @Bean} override any implementation without disabling
 *       the entire auto-config.</li>
 *   <li><b>Property Toggles:</b> Legacy or non-default features (like AES-CBC) are guarded
 *       with {@link ConditionalOnProperty} so operators can disable them via {@code application.yml}.</li>
 * </ul>
 *
 * <h2>Required Beans (User-Provided)</h2>
 * <p>This auto-configuration intentionally does NOT provide default key provider beans.
 * The consuming application MUST register:</p>
 * <ul>
 *   <li>A {@code SymmetricKeyProvider} bean — loaded from their KMS, Vault, or local config.</li>
 *   <li>An {@code AsymmetricKeyProvider} bean — loaded from their certificate store or KMS.</li>
 * </ul>
 * <p>If neither is provided and a service that depends on them is requested, Spring will
 * throw a {@code NoSuchBeanDefinitionException} at startup — a deliberate fail-closed design.</p>
 *
 * <h2>application.yml Reference</h2>
 * <pre>{@code
 * infra:
 *   crypto:
 *     hashing:
 *       default-algorithm: SHA_256         # SHA_256, SHA_512, SHA3_256, SHA3_512
 *     password:
 *       memory-kb: 65536                   # Argon2id memory cost (64MB default)
 *       iterations: 3
 *       parallelism: 1
 *     symmetric:
 *       default-algorithm: AES_256_GCM
 *       cbc:
 *         enabled: true                    # Set to false to disable legacy AES-CBC support
 *     asymmetric:
 *       default-algorithm: RSA
 *       default-rsa-padding: OAEP_SHA256
 *       default-rsa-key-size: RSA_2048
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(InfraCryptoProperties.class)
public class InfraCryptoAutoConfiguration {

    // =========================================================================
    // Phase 2: Hashing & MAC Services
    // =========================================================================

    /**
     * Standard hashing service (SHA-256/SHA-512/SHA-3).
     * No key provider required — purely algorithmic.
     */
    @Bean
    @ConditionalOnMissingBean(StandardHashingService.class)
    public StandardHashingService standardHashingService() {
        return new StandardHashingServiceImpl();
    }

    /**
     * Argon2id password hashing service.
     * Parameters are driven by {@link InfraCryptoProperties.PasswordProperties} via the constructor.
     *
     * <p>Security note: Argon2id is the only algorithm this service supports by design.
     * bcrypt and PBKDF2 are not offered.</p>
     */
    @Bean
    @ConditionalOnMissingBean(PasswordHashingService.class)
    public PasswordHashingService passwordHashingService() {
        return new Argon2PasswordHashingServiceImpl();
    }

    /**
     * HMAC service (HMAC-SHA256 / HMAC-SHA512).
     * No key provider required — key is passed per-call.
     */
    @Bean
    @ConditionalOnMissingBean(HmacService.class)
    public HmacService hmacService() {
        return new HmacServiceImpl();
    }

    // =========================================================================
    // Phase 3: Symmetric Encryption — AES-GCM (Primary)
    // =========================================================================

    /**
     * AES-GCM encryption service — the recommended symmetric service.
     *
     * <p>Requires a {@link SymmetricKeyProvider} bean to be present in the application context.
     * If none is provided, Spring will fail at startup — this is intentional.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "aesGcmEncryptionService")
    public SymmetricEncryptionService aesGcmEncryptionService(SymmetricKeyProvider symmetricKeyProvider) {
        return new AesGcmEncryptionServiceImpl(symmetricKeyProvider);
    }

    // =========================================================================
    // Phase 4: Symmetric Encryption — AES-CBC + HMAC (Legacy Support)
    // =========================================================================

    /**
     * AES-CBC + HMAC-SHA256 (Encrypt-then-MAC) legacy encryption service.
     *
     * <p>This bean is conditional on {@code infra.crypto.symmetric.cbc.enabled=true} (the default).
     * Set {@code infra.crypto.symmetric.cbc.enabled=false} in {@code application.yml} to disable
     * this legacy cipher suite entirely. When disabled, no AES-CBC cipher can be instantiated,
     * preventing accidental use in new code.</p>
     *
     * <p>Requires both a {@link SymmetricKeyProvider} and a {@link HmacService} bean.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "aesCbcHmacEncryptionService")
    @ConditionalOnProperty(
            prefix = "infra.crypto.symmetric.cbc",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true  // Default: legacy support is ON unless explicitly disabled
    )
    public SymmetricEncryptionService aesCbcHmacEncryptionService(
            SymmetricKeyProvider symmetricKeyProvider,
            HmacService hmacService) {
        return new AesCbcHmacEncryptionServiceImpl(symmetricKeyProvider, hmacService);
    }

    // =========================================================================
    // Phase 5: Asymmetric Encryption & Digital Signatures
    // =========================================================================

    /**
     * RSA encryption service using OAEP padding.
     * Requires an {@link AsymmetricKeyProvider} bean.
     */
    @Bean
    @ConditionalOnMissingBean(AsymmetricEncryptionService.class)
    public AsymmetricEncryptionService rsaEncryptionService(AsymmetricKeyProvider asymmetricKeyProvider) {
        return new RsaEncryptionServiceImpl(asymmetricKeyProvider);
    }

    /**
     * RSA-PSS digital signature service (using RSASSA-PSS).
     *
     * <p>This is the default {@link DigitalSignatureService} bean. If you need EdDSA
     * as the primary, register your own bean annotated with {@code @Primary} and
     * this auto-config will back off.</p>
     *
     * <p>Requires an {@link AsymmetricKeyProvider} bean.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "rsaDigitalSignatureService")
    public DigitalSignatureService rsaDigitalSignatureService(AsymmetricKeyProvider asymmetricKeyProvider) {
        return new RsaDigitalSignatureServiceImpl(asymmetricKeyProvider);
    }

    /**
     * Ed25519 digital signature service.
     *
     * <p>Provided as a secondary {@link DigitalSignatureService} bean alongside the RSA-PSS bean.
     * Note: because both beans implement the same type, callers must qualify which one they inject
     * using {@code @Qualifier("edDsaDigitalSignatureService")} or switch their injection to
     * by-name. See README for usage guidance.</p>
     *
     * <p>Requires Java 15+ (JEP 339) — available natively on the project's JDK 21 baseline.</p>
     * <p>Requires an {@link AsymmetricKeyProvider} bean.</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "edDsaDigitalSignatureService")
    public DigitalSignatureService edDsaDigitalSignatureService(AsymmetricKeyProvider asymmetricKeyProvider) {
        return new EdDsaDigitalSignatureServiceImpl(asymmetricKeyProvider);
    }
}

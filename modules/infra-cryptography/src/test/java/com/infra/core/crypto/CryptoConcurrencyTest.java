package com.infra.core.crypto;

import com.infra.core.crypto.enums.HashAlgorithm;
import com.infra.core.crypto.hashing.impl.StandardHashingServiceImpl;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import com.infra.core.crypto.symmetric.impl.AesGcmEncryptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thread safety concurrency tests for JCA-backed crypto services.
 *
 * <p>Proves that {@link AesGcmEncryptionServiceImpl} and {@link StandardHashingServiceImpl}
 * are safe under parallel load by using {@link IntStream#parallel()} to drive 1000
 * concurrent operations. Each thread gets a unique plaintext, encrypts it, decrypts it,
 * and verifies the round-trip.</p>
 *
 * <p>The test FAILS if:</p>
 * <ul>
 *   <li>Any exception is thrown (cross-thread Cipher state leakage)</li>
 *   <li>Any decryption does not recover the original plaintext (data corruption)</li>
 *   <li>Any hash verification returns false (MessageDigest collision)</li>
 * </ul>
 *
 * <p>This validates the design decision of creating new {@code Cipher} / {@code MessageDigest}
 * instances per-call instead of using ThreadLocal or pooling.</p>
 */
@DisplayName("Crypto Concurrency — Thread Safety Validation")
class CryptoConcurrencyTest {

    private AesGcmEncryptionServiceImpl aesGcmService;
    private StandardHashingServiceImpl hashingService;

    private static final int ITERATION_COUNT = 1000;
    private static final byte[] AAD = "concurrency-test-context".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        SymmetricKeyProvider provider = TestKeyProviders.symmetricProviderGcmOnly();
        aesGcmService = new AesGcmEncryptionServiceImpl(provider);
        hashingService = new StandardHashingServiceImpl();
    }

    // =========================================================================
    // AES-GCM Concurrency
    // =========================================================================

    @Test
    @DisplayName("1000 parallel AES-GCM encrypt/decrypt cycles — zero failures, all recover original plaintext")
    void aesGcm_parallelEncryptDecrypt_noCollisions() {
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        IntStream.range(0, ITERATION_COUNT).parallel().forEach(i -> {
            try {
                // Unique plaintext per iteration to detect cross-thread data leakage
                byte[] plaintext = ("Iteration-" + i + "-payload-data").getBytes(StandardCharsets.UTF_8);

                byte[] encrypted = aesGcmService.encrypt(plaintext, AAD);
                byte[] decrypted = aesGcmService.decrypt(encrypted, AAD);

                if (!java.util.Arrays.equals(plaintext, decrypted)) {
                    failures.add("Iteration " + i + ": decrypted bytes do not match original plaintext");
                } else {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                failures.add("Iteration " + i + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        });

        assertThat(failures)
                .as("AES-GCM parallel failures — these indicate thread safety violations")
                .isEmpty();
        assertThat(successCount.get()).isEqualTo(ITERATION_COUNT);
    }

    // =========================================================================
    // StandardHashingService Concurrency
    // =========================================================================

    @Test
    @DisplayName("1000 parallel SHA-256 hash/verify cycles — zero failures, all verify true")
    void hashing_parallelHashVerify_noCollisions() {
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        IntStream.range(0, ITERATION_COUNT).parallel().forEach(i -> {
            try {
                byte[] input = ("Hash-input-iteration-" + i).getBytes(StandardCharsets.UTF_8);

                byte[] hash = hashingService.hash(input, HashAlgorithm.SHA_256);
                boolean verified = hashingService.verify(input, hash, HashAlgorithm.SHA_256);

                if (!verified) {
                    failures.add("Iteration " + i + ": hash verification returned false");
                } else {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                failures.add("Iteration " + i + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        });

        assertThat(failures)
                .as("Hashing parallel failures — these indicate MessageDigest thread safety violations")
                .isEmpty();
        assertThat(successCount.get()).isEqualTo(ITERATION_COUNT);
    }

    // =========================================================================
    // Mixed Workload — Both Services Under Simultaneous Load
    // =========================================================================

    @Test
    @DisplayName("1000 parallel mixed (AES-GCM + SHA-256) operations — zero failures")
    void mixedWorkload_parallelEncryptAndHash_noCollisions() {
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        IntStream.range(0, ITERATION_COUNT).parallel().forEach(i -> {
            try {
                byte[] data = ("Mixed-workload-" + i).getBytes(StandardCharsets.UTF_8);

                // AES-GCM round-trip
                byte[] encrypted = aesGcmService.encrypt(data, AAD);
                byte[] decrypted = aesGcmService.decrypt(encrypted, AAD);
                if (!java.util.Arrays.equals(data, decrypted)) {
                    failures.add("Iteration " + i + " (AES-GCM): plaintext mismatch");
                    return;
                }

                // SHA-256 round-trip
                byte[] hash = hashingService.hash(data, HashAlgorithm.SHA_256);
                if (!hashingService.verify(data, hash, HashAlgorithm.SHA_256)) {
                    failures.add("Iteration " + i + " (SHA-256): verification failed");
                    return;
                }

                successCount.incrementAndGet();
            } catch (Exception e) {
                failures.add("Iteration " + i + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
            }
        });

        assertThat(failures)
                .as("Mixed-workload parallel failures — thread safety violation")
                .isEmpty();
        assertThat(successCount.get()).isEqualTo(ITERATION_COUNT);
    }
}

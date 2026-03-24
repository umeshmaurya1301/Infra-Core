package com.infra.core.crypto.asymmetric.impl;

import com.infra.core.crypto.TestKeyProviders;
import com.infra.core.crypto.enums.RsaPadding;
import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.provider.AsymmetricKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for {@link RsaEncryptionServiceImpl}.
 *
 * <p>Focus areas:
 * <ol>
 *   <li>Round-trip correctness with OAEP-SHA256</li>
 *   <li>Preemptive payload size bounds checking — no {@code IllegalBlockSizeException} leaks</li>
 *   <li>Null/empty input guards</li>
 *   <li>Boundary payload sizes (exact max, max+1)</li>
 * </ol>
 *
 * <p>Uses a real RSA-2048 key pair — no mocking of JCA crypto operations.</p>
 */
@DisplayName("RSA Encryption Service — Security Tests")
class RsaEncryptionServiceTest {

    private RsaEncryptionServiceImpl service;

    @BeforeEach
    void setUp() {
        AsymmetricKeyProvider provider = TestKeyProviders.rsaProvider();
        service = new RsaEncryptionServiceImpl(provider);
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Encrypt → Decrypt round-trip with OAEP-SHA256 on small payload")
        void encryptDecryptRoundTrip_oaepSha256() {
            byte[] plaintext = "AES-session-key-material-32bytes!".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext, RsaPadding.OAEP_SHA256);
            byte[] decrypted = service.decrypt(encrypted, RsaPadding.OAEP_SHA256);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("Encrypt with max-allowed payload size (190 bytes for RSA-2048 + OAEP-SHA256)")
        void encryptExactMaxPayload_succeeds() {
            // RSA-2048 = 256 bytes modulus. OAEP-SHA256 overhead = 66. Max = 256 - 66 = 190 bytes.
            byte[] maxPayload = new byte[190];
            java.util.Arrays.fill(maxPayload, (byte) 0x42);

            byte[] encrypted = service.encrypt(maxPayload, RsaPadding.OAEP_SHA256);
            byte[] decrypted = service.decrypt(encrypted, RsaPadding.OAEP_SHA256);

            assertThat(decrypted).isEqualTo(maxPayload);
        }
    }

    // =========================================================================
    // Payload Size Bounds — Preemptive Rejection
    // =========================================================================

    @Nested
    @DisplayName("Payload Size Bounds")
    class PayloadSizeBounds {

        @Test
        @DisplayName("Payload 1 byte over max (191 bytes) throws EncryptionFailureException with 'Payload too large'")
        void payloadOneByteOverMax_throwsEncryptionFailure() {
            // RSA-2048 + OAEP-SHA256 → max 190 bytes. 191 must fail preemptively.
            byte[] oversizedPayload = new byte[191];
            java.util.Arrays.fill(oversizedPayload, (byte) 0xAA);

            assertThatThrownBy(() -> service.encrypt(oversizedPayload, RsaPadding.OAEP_SHA256))
                    .isInstanceOf(EncryptionFailureException.class)
                    .hasMessageContaining("Payload too large");
        }

        @Test
        @DisplayName("Large payload (1 KB) throws EncryptionFailureException preemptively")
        void largePayload_throwsEncryptionFailure() {
            byte[] largePayload = new byte[1024];

            assertThatThrownBy(() -> service.encrypt(largePayload, RsaPadding.OAEP_SHA256))
                    .isInstanceOf(EncryptionFailureException.class)
                    .hasMessageContaining("Payload too large");
        }

        @Test
        @DisplayName("OAEP-SHA512 has smaller max (126 bytes on RSA-2048) — 127 bytes must fail")
        void oaepSha512_payloadOverMax_throwsEncryptionFailure() {
            // RSA-2048 = 256 bytes. OAEP-SHA512 overhead = 130. Max = 126 bytes.
            byte[] oversized = new byte[127];

            assertThatThrownBy(() -> service.encrypt(oversized, RsaPadding.OAEP_SHA512))
                    .isInstanceOf(EncryptionFailureException.class)
                    .hasMessageContaining("Payload too large");
        }
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("encrypt(null, padding) throws EncryptionFailureException")
        void encryptNullPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(null, RsaPadding.OAEP_SHA256))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("encrypt(payload, null) throws EncryptionFailureException")
        void encryptNullPadding_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt("test".getBytes(), null))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("encrypt(empty, padding) throws EncryptionFailureException")
        void encryptEmptyPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(new byte[0], RsaPadding.OAEP_SHA256))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(null, padding) throws DecryptionFailureException")
        void decryptNullPayload_throwsDecryptionFailure() {
            assertThatThrownBy(() -> service.decrypt(null, RsaPadding.OAEP_SHA256))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(empty, padding) throws DecryptionFailureException")
        void decryptEmptyPayload_throwsDecryptionFailure() {
            assertThatThrownBy(() -> service.decrypt(new byte[0], RsaPadding.OAEP_SHA256))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(payload, null) throws DecryptionFailureException")
        void decryptNullPadding_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt("test".getBytes(), RsaPadding.OAEP_SHA256);
            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class);
        }
    }

    // =========================================================================
    // Ciphertext Tampering
    // =========================================================================

    @Nested
    @DisplayName("Tamper: Ciphertext Corruption")
    class CiphertextTampering {

        @Test
        @DisplayName("Flipping a byte in RSA ciphertext throws DecryptionFailureException")
        void flipByteInCiphertext_throwsDecryptionFailure() {
            byte[] plaintext = "secret-data".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = service.encrypt(plaintext, RsaPadding.OAEP_SHA256);

            // Ciphertext starts after: Version(1) + KID_LEN(1) + KID("test-key-001" = 12)
            int ctStart = 1 + 1 + TestKeyProviders.testKid().getBytes(StandardCharsets.UTF_8).length;
            encrypted[ctStart + 10] ^= 0xFF;

            assertThatThrownBy(() -> service.decrypt(encrypted, RsaPadding.OAEP_SHA256))
                    .isInstanceOf(DecryptionFailureException.class);
        }
    }
}

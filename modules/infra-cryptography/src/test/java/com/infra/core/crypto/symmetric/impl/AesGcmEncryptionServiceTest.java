package com.infra.core.crypto.symmetric.impl;

import com.infra.core.crypto.TestKeyProviders;
import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial security tests for {@link AesGcmEncryptionServiceImpl}.
 *
 * <p>Focus areas:
 * <ol>
 *   <li>Round-trip correctness with AAD</li>
 *   <li>GCM tag detection on bit-flipped ciphertext</li>
 *   <li>AAD mismatch detection</li>
 *   <li>Binary header corruption (version byte, KID length byte)</li>
 *   <li>Null/empty payload guards</li>
 *   <li>Truncated payload handling</li>
 * </ol>
 */
@DisplayName("AES-GCM Encryption Service — Security Tests")
class AesGcmEncryptionServiceTest {

    private AesGcmEncryptionServiceImpl service;

    private static final byte[] PLAINTEXT = "Sensitive financial data: account=98765".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAD = "transaction-context:wire-transfer-001".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        SymmetricKeyProvider provider = TestKeyProviders.symmetricProviderGcmOnly();
        service = new AesGcmEncryptionServiceImpl(provider);
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Encrypt → Decrypt round-trip with AAD produces original plaintext")
        void encryptDecryptRoundTrip_withAad() {
            byte[] ciphertext = service.encrypt(PLAINTEXT, AAD);
            byte[] decrypted = service.decrypt(ciphertext, AAD);

            assertThat(decrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("Encrypt → Decrypt round-trip with null AAD (defaults to empty)")
        void encryptDecryptRoundTrip_nullAad() {
            byte[] ciphertext = service.encrypt(PLAINTEXT, null);
            byte[] decrypted = service.decrypt(ciphertext, null);

            assertThat(decrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("Two encryptions of same plaintext produce different ciphertexts (IV uniqueness)")
        void twoEncryptionsProduceDifferentCiphertexts() {
            byte[] ct1 = service.encrypt(PLAINTEXT, AAD);
            byte[] ct2 = service.encrypt(PLAINTEXT, AAD);

            assertThat(ct1).isNotEqualTo(ct2);
        }
    }

    // =========================================================================
    // Ciphertext / Tag Tampering
    // =========================================================================

    @Nested
    @DisplayName("Tamper: Ciphertext/Tag Byte Flip")
    class CiphertextTampering {

        @Test
        @DisplayName("Flipping a single byte in ciphertext+tag region causes DecryptionFailureException")
        void flipByteInCiphertextRegion_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            // The ciphertext+tag starts after: Version(1) + KID_LEN(1) + KID(N) + IV(12)
            // KID = "test-key-001" → 12 bytes UTF-8
            // Offset = 1 + 1 + 12 + 12 = 26
            int ciphertextStart = 1 + 1 + TestKeyProviders.testKid().getBytes(StandardCharsets.UTF_8).length + 12;
            assertThat(encrypted.length).isGreaterThan(ciphertextStart);

            // Flip one bit in the first ciphertext byte
            encrypted[ciphertextStart] ^= 0x01;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("Flipping the last byte (inside GCM tag) causes DecryptionFailureException")
        void flipLastByte_insideGcmTag_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            // Last 16 bytes are the GCM tag (appended by JCA)
            encrypted[encrypted.length - 1] ^= 0xFF;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class);
        }
    }

    // =========================================================================
    // AAD Mismatch
    // =========================================================================

    @Nested
    @DisplayName("Tamper: AAD Mismatch")
    class AadMismatch {

        @Test
        @DisplayName("Decrypting with different AAD than encrypt-time triggers DecryptionFailureException")
        void differentAadOnDecrypt_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);
            byte[] wrongAad = "TAMPERED-aad-context".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> service.decrypt(encrypted, wrongAad))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("Decrypting with null AAD when encrypted with non-null AAD triggers failure")
        void nullAadOnDecryptWhenEncryptedWithAad_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class);
        }
    }

    // =========================================================================
    // Binary Header Corruption
    // =========================================================================

    @Nested
    @DisplayName("Tamper: Binary Header Corruption")
    class HeaderCorruption {

        @Test
        @DisplayName("Corrupted version byte (0xFF) throws DecryptionFailureException with version message")
        void corruptVersionByte_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            // Byte 0 = Version. Valid is 0x01. Set to 0xFF.
            encrypted[0] = (byte) 0xFF;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("Unsupported payload version");
        }

        @Test
        @DisplayName("Zero version byte throws DecryptionFailureException")
        void zeroVersionByte_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);
            encrypted[0] = 0x00;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("Unsupported payload version");
        }

        @Test
        @DisplayName("KID length byte set to 0xFF causes payload-too-short DecryptionFailureException")
        void corruptKidLengthByte_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            // Byte 1 = KID length. Setting to 0xFF (255) will exceed actual payload.
            encrypted[1] = (byte) 0xFF;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("KID length byte set to 0x00 causes empty-KID DecryptionFailureException")
        void zeroKidLengthByte_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, AAD);

            // Byte 1 = KID length. Setting to 0 → "Payload contains an empty KID"
            encrypted[1] = 0x00;

            assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("empty KID");
        }
    }

    // =========================================================================
    // Input Validation Guards
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("encrypt(null, aad) throws EncryptionFailureException")
        void encryptNullPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(null, AAD))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("encrypt(empty, aad) throws EncryptionFailureException")
        void encryptEmptyPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(new byte[0], AAD))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(null, aad) throws EncryptionFailureException")
        void decryptNullPayload_throwsException() {
            // decrypt validator reuses validatePayload which throws EncryptionFailureException
            assertThatThrownBy(() -> service.decrypt(null, AAD))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(empty, aad) throws EncryptionFailureException")
        void decryptEmptyPayload_throwsException() {
            assertThatThrownBy(() -> service.decrypt(new byte[0], AAD))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("Truncated 5-byte payload causes DecryptionFailureException during header parsing")
        void truncatedPayload_throwsDecryptionFailure() {
            // 5 bytes: version(1) + kidLen(1) + partial KID. Not enough for full header.
            byte[] truncated = new byte[]{0x01, 0x05, 0x41, 0x42, 0x43};

            assertThatThrownBy(() -> service.decrypt(truncated, AAD))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("Single-byte payload causes DecryptionFailureException")
        void singleBytePayload_throwsDecryptionFailure() {
            byte[] tiny = new byte[]{0x01};

            assertThatThrownBy(() -> service.decrypt(tiny, AAD))
                    .isInstanceOf(DecryptionFailureException.class);
        }
    }
}

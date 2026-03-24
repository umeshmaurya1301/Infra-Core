package com.infra.core.crypto.symmetric.impl;

import com.infra.core.crypto.TestKeyProviders;
import com.infra.core.crypto.exception.DecryptionFailureException;
import com.infra.core.crypto.exception.EncryptionFailureException;
import com.infra.core.crypto.mac.HmacService;
import com.infra.core.crypto.mac.impl.HmacServiceImpl;
import com.infra.core.crypto.provider.SymmetricKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial security tests for {@link AesCbcHmacEncryptionServiceImpl} (Encrypt-then-MAC).
 *
 * <p>Primary goal: prove that the Cipher is NEVER initialised when the HMAC has been tampered,
 * thereby eliminating any Padding Oracle attack surface.</p>
 *
 * <p>All tests use real {@link HmacServiceImpl} — no mocking of cryptographic operations.</p>
 */
@DisplayName("AES-CBC-HMAC (EtM) Encryption Service — Security Tests")
class AesCbcHmacEncryptionServiceTest {

    private AesCbcHmacEncryptionServiceImpl service;

    private static final byte[] PLAINTEXT = "PCI-DSS card data: 4111-1111-1111-1111".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        SymmetricKeyProvider provider = TestKeyProviders.symmetricProvider();
        HmacService hmacService = new HmacServiceImpl();
        service = new AesCbcHmacEncryptionServiceImpl(provider, hmacService);
    }

    // =========================================================================
    // Happy Path
    // =========================================================================

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("Encrypt → Decrypt round-trip with real EtM produces original plaintext")
        void encryptDecryptRoundTrip() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);
            byte[] decrypted = service.decrypt(encrypted, null);

            assertThat(decrypted).isEqualTo(PLAINTEXT);
        }

        @Test
        @DisplayName("Two encryptions of same plaintext produce different outputs (IV uniqueness)")
        void twoEncryptionsProduceDifferentOutputs() {
            byte[] ct1 = service.encrypt(PLAINTEXT, null);
            byte[] ct2 = service.encrypt(PLAINTEXT, null);

            assertThat(ct1).isNotEqualTo(ct2);
        }
    }

    // =========================================================================
    // HMAC Tampering — proves Padding Oracle is impossible
    // =========================================================================

    @Nested
    @DisplayName("Tamper: HMAC Manipulation (Padding Oracle Proof)")
    class HmacTampering {

        @Test
        @DisplayName("Flipping one byte in trailing 32-byte HMAC throws DecryptionFailureException — Cipher never touched")
        void flipByteInHmac_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);

            // HMAC occupies the last 32 bytes. Flip one bit in the first HMAC byte.
            int hmacStart = encrypted.length - 32;
            encrypted[hmacStart] ^= 0x01;

            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("HMAC verification failed");
        }

        @Test
        @DisplayName("Zeroing entire 32-byte HMAC throws DecryptionFailureException")
        void zeroEntireHmac_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);
            int hmacStart = encrypted.length - 32;

            for (int i = hmacStart; i < encrypted.length; i++) {
                encrypted[i] = 0x00;
            }

            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("HMAC verification failed");
        }
    }

    // =========================================================================
    // Ciphertext Tampering — proves MAC-first verification
    // =========================================================================

    @Nested
    @DisplayName("Tamper: Ciphertext Body (MAC-First Proof)")
    class CiphertextTampering {

        @Test
        @DisplayName("Flipping byte in ciphertext region triggers HMAC mismatch, not padding error")
        void flipByteInCiphertext_causesHmacFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);

            // Ciphertext starts after: Version(1) + KID_LEN(1) + KID(12) + IV(16) = offset 30
            int ciphertextStart = 1 + 1 + TestKeyProviders.testKid().getBytes(StandardCharsets.UTF_8).length + 16;
            // Ensure we're flipping within the ciphertext, before the HMAC
            int hmacStart = encrypted.length - 32;
            assertThat(ciphertextStart).isLessThan(hmacStart);

            encrypted[ciphertextStart] ^= 0xFF;

            // CRITICAL ASSERTION: The error message must indicate HMAC failure,
            // NOT a padding/decryption error. This proves the Cipher was never initialized.
            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("HMAC verification failed");
        }
    }

    // =========================================================================
    // Version / Header Corruption
    // =========================================================================

    @Nested
    @DisplayName("Tamper: Header Corruption")
    class HeaderCorruption {

        @Test
        @DisplayName("Corrupted version byte causes DecryptionFailureException before HMAC check")
        void corruptVersionByte_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);
            encrypted[0] = (byte) 0xFF;

            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("Unsupported payload version");
        }

        @Test
        @DisplayName("Zero KID length byte causes DecryptionFailureException")
        void zeroKidLength_throwsDecryptionFailure() {
            byte[] encrypted = service.encrypt(PLAINTEXT, null);
            encrypted[1] = 0x00;

            assertThatThrownBy(() -> service.decrypt(encrypted, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("empty KID");
        }
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidation {

        @Test
        @DisplayName("encrypt(null) throws EncryptionFailureException")
        void encryptNullPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(null, null))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("encrypt(empty) throws EncryptionFailureException")
        void encryptEmptyPayload_throwsEncryptionFailure() {
            assertThatThrownBy(() -> service.encrypt(new byte[0], null))
                    .isInstanceOf(EncryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(null) throws DecryptionFailureException")
        void decryptNullPayload_throwsDecryptionFailure() {
            assertThatThrownBy(() -> service.decrypt(null, null))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("decrypt(empty) throws DecryptionFailureException")
        void decryptEmptyPayload_throwsDecryptionFailure() {
            assertThatThrownBy(() -> service.decrypt(new byte[0], null))
                    .isInstanceOf(DecryptionFailureException.class);
        }

        @Test
        @DisplayName("Payload shorter than MIN_PAYLOAD_BYTES throws DecryptionFailureException")
        void tooShortPayload_throwsDecryptionFailure() {
            // MIN_PAYLOAD_BYTES = 1 + 1 + 16 + 32 = 50
            byte[] tooShort = new byte[49];
            tooShort[0] = 0x01; // valid version so we pass version check first

            assertThatThrownBy(() -> service.decrypt(tooShort, null))
                    .isInstanceOf(DecryptionFailureException.class)
                    .hasMessageContaining("too short");
        }
    }
}

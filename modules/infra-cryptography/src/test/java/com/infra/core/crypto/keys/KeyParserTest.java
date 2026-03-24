package com.infra.core.crypto.keys;

import com.infra.core.crypto.enums.AsymmetricAlgorithm;
import com.infra.core.crypto.enums.MacAlgorithm;
import com.infra.core.crypto.enums.SymmetricAlgorithm;
import com.infra.core.crypto.exception.KeyParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for {@link KeyParser}.
 *
 * <p>Primary adversarial focus:</p>
 * <ul>
 *   <li>Garbage DER bytes must throw {@link KeyParsingException}, not a JCA internal exception</li>
 *   <li>Null/empty inputs must fail fast</li>
 *   <li>Malformed PEM strings must not crash</li>
 *   <li>Valid round-trips prove correctness baseline</li>
 * </ul>
 */
@DisplayName("KeyParser — Security & Parsing Tests")
class KeyParserTest {

    // =========================================================================
    // Symmetric Key Parsing
    // =========================================================================

    @Nested
    @DisplayName("Symmetric Key Parsing")
    class SymmetricKeyParsing {

        @Test
        @DisplayName("parseSymmetricKey with valid 32-byte AES key produces correct algorithm")
        void validAesKey_producesCorrectAlgorithm() throws Exception {
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256);
            SecretKey original = gen.generateKey();

            SecretKey parsed = KeyParser.parseSymmetricKey(original.getEncoded(), SymmetricAlgorithm.AES_256_GCM);

            assertThat(parsed.getAlgorithm()).isEqualTo("AES");
            assertThat(parsed.getEncoded()).isEqualTo(original.getEncoded());
        }

        @Test
        @DisplayName("parseSymmetricKey with null key throws KeyParsingException")
        void nullKey_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parseSymmetricKey(null, SymmetricAlgorithm.AES_256_GCM))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("parseSymmetricKey with null algorithm throws KeyParsingException")
        void nullAlgorithm_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parseSymmetricKey(new byte[32], null))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("parseSymmetricKey with empty bytes throws KeyParsingException")
        void emptyKey_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parseSymmetricKey(new byte[0], SymmetricAlgorithm.AES_256_GCM))
                    .isInstanceOf(KeyParsingException.class);
        }
    }

    // =========================================================================
    // MAC Key Parsing
    // =========================================================================

    @Nested
    @DisplayName("MAC Key Parsing")
    class MacKeyParsing {

        @Test
        @DisplayName("parseMacKey with valid bytes produces correct HMAC algorithm")
        void validMacKey_producesCorrectAlgorithm() {
            byte[] rawKey = new byte[32];
            new SecureRandom().nextBytes(rawKey);

            SecretKey parsed = KeyParser.parseMacKey(rawKey, MacAlgorithm.HMAC_SHA256);

            assertThat(parsed.getAlgorithm()).isEqualTo("HmacSHA256");
            assertThat(parsed.getEncoded()).isEqualTo(rawKey);
        }

        @Test
        @DisplayName("parseMacKey with null key throws KeyParsingException")
        void nullKey_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parseMacKey(null, MacAlgorithm.HMAC_SHA256))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("parseMacKey with empty bytes throws KeyParsingException")
        void emptyKey_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parseMacKey(new byte[0], MacAlgorithm.HMAC_SHA256))
                    .isInstanceOf(KeyParsingException.class);
        }
    }

    // =========================================================================
    // Asymmetric Public Key Parsing (DER)
    // =========================================================================

    @Nested
    @DisplayName("Public Key DER Parsing")
    class PublicKeyDerParsing {

        @Test
        @DisplayName("parsePublicKey round-trip with valid RSA-2048 X.509 DER")
        void validRsaPublicKey_roundTrips() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();

            PublicKey parsed = KeyParser.parsePublicKey(kp.getPublic().getEncoded(), AsymmetricAlgorithm.RSA);

            assertThat(parsed.getAlgorithm()).isEqualTo("RSA");
            assertThat(parsed.getEncoded()).isEqualTo(kp.getPublic().getEncoded());
        }

        @Test
        @DisplayName("Garbage DER bytes throw KeyParsingException — no JCA leak")
        void garbageDerBytes_throwsKeyParsingException() {
            byte[] garbage = new byte[128];
            new SecureRandom().nextBytes(garbage);

            assertThatThrownBy(() -> KeyParser.parsePublicKey(garbage, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Null DER bytes throw KeyParsingException")
        void nullDer_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePublicKey(null, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Empty DER bytes throw KeyParsingException")
        void emptyDer_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePublicKey(new byte[0], AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }
    }

    // =========================================================================
    // Asymmetric Private Key Parsing (DER)
    // =========================================================================

    @Nested
    @DisplayName("Private Key DER Parsing")
    class PrivateKeyDerParsing {

        @Test
        @DisplayName("parsePrivateKey round-trip with valid RSA-2048 PKCS#8 DER")
        void validRsaPrivateKey_roundTrips() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();

            PrivateKey parsed = KeyParser.parsePrivateKey(kp.getPrivate().getEncoded(), AsymmetricAlgorithm.RSA);

            assertThat(parsed.getAlgorithm()).isEqualTo("RSA");
            assertThat(parsed.getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
        }

        @Test
        @DisplayName("Garbage PKCS#8 bytes throw KeyParsingException")
        void garbagePkcs8_throwsKeyParsingException() {
            byte[] garbage = new byte[256];
            new SecureRandom().nextBytes(garbage);

            assertThatThrownBy(() -> KeyParser.parsePrivateKey(garbage, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Null PKCS#8 bytes throw KeyParsingException")
        void nullPkcs8_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePrivateKey(null, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Empty PKCS#8 bytes throw KeyParsingException")
        void emptyPkcs8_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePrivateKey(new byte[0], AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }
    }

    // =========================================================================
    // PEM Parsing — Adversarial Strings
    // =========================================================================

    @Nested
    @DisplayName("PEM Parsing — Adversarial")
    class PemParsing {

        @Test
        @DisplayName("Null PEM string throws KeyParsingException")
        void nullPem_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePublicKeyPem(null, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Blank PEM string throws KeyParsingException")
        void blankPem_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePublicKeyPem("   ", AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("Random text (no PEM markers) throws KeyParsingException")
        void randomTextNoPemMarkers_throwsKeyParsingException() {
            String notPem = "This is definitely not a PEM encoded key !!@#$%^&*()";

            assertThatThrownBy(() -> KeyParser.parsePublicKeyPem(notPem, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("PEM with PRIVATE KEY header fed to parsePublicKeyPem throws KeyParsingException")
        void privateKeyPemFedToPublicParser_throwsKeyParsingException() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();

            // Build a PRIVATE KEY PEM manually
            String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                    + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----";

            assertThatThrownBy(() -> KeyParser.parsePublicKeyPem(privatePem, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class)
                    .hasMessageContaining("Expected PUBLIC KEY");
        }

        @Test
        @DisplayName("Null PEM for private key throws KeyParsingException")
        void nullPrivatePem_throwsKeyParsingException() {
            assertThatThrownBy(() -> KeyParser.parsePrivateKeyPem(null, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }

        @Test
        @DisplayName("PEM with PUBLIC KEY header fed to parsePrivateKeyPem throws KeyParsingException")
        void publicKeyPemFedToPrivateParser_throwsKeyParsingException() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();

            String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                    + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";

            assertThatThrownBy(() -> KeyParser.parsePrivateKeyPem(publicPem, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class)
                    .hasMessageContaining("Expected PRIVATE KEY");
        }

        @Test
        @DisplayName("PEM with mangled Base64 content throws KeyParsingException")
        void mangledBase64InPem_throwsKeyParsingException() {
            String mangledPem = "-----BEGIN PUBLIC KEY-----\n"
                    + "THIS_IS_NOT_VALID_BASE64_!!!@#$%\n"
                    + "-----END PUBLIC KEY-----";

            assertThatThrownBy(() -> KeyParser.parsePublicKeyPem(mangledPem, AsymmetricAlgorithm.RSA))
                    .isInstanceOf(KeyParsingException.class);
        }
    }
}

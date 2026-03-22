# Crypto Library Implementation Plan (implementation.md) - v2

## 🧠 Context
This document outlines the architecture and execution strategy for the **Infra Crypto Library**, a highly secure, fintech-grade Spring Boot starter. It prioritizes "Secure by Default" paradigms, failing closed, strict cryptographic separation, and forward compatibility for key rotation. The design prevents insecure deviations by abstracting the raw JCA (Java Cryptography Architecture) primitives through strongly typed, enum-driven interfaces.

---

## ⚠️ Non-Negotiable Rules

1. **NO raw algorithm strings anywhere**
    * JCA strings (e.g., `"AES/GCM/NoPadding"`) must be completely hidden inside internal service implementations and selected exclusively via typed enums.
2. **NO leaking JCA exceptions**
    * Standard exceptions like `NoSuchAlgorithmException` or `InvalidKeyException` must be caught and wrapped in a domain-specific `CryptographyException` to prevent leaking internal security state.
3. **Secure defaults must always exist**
    * There is NO fallback to insecure crypto (e.g., CBC without MAC, or ECB).
4. **Configuration must be strongly typed and validated**
    * Use `@ConfigurationProperties` paired with `jakarta.validation` annotations (e.g., `@Min`, `@NotBlank`).
5. **FAIL CLOSED on tampering**
    * Any validation failure, MAC mismatch, or malformed ciphertext immediately results in an explicit runtime exception.
6. **JCA Primitives are strictly NOT Thread-Safe**
    * `Cipher`, `Mac`, `MessageDigest`, and `Signature` instances must **never** be shared as singletons. They must be instantiated per-operation or managed via `ThreadLocal`.
7. **Forward Compatibility (Key Rotation is Mandatory)**
    * All ciphertexts MUST include a Key Identifier (KID) or version byte header to allow seamless decryption after keys are rotated.
8. **Library must be plug-and-play**
    * Works securely via DI right after adding the dependency, requiring configuration only to override defaults.
9. **❗Clarification First Rule (VERY IMPORTANT)**
    * If there is **ANY ambiguity, missing requirement, or unclear design decision**:
        * ❌ DO NOT assume
        * ❌ DO NOT start implementation
        * ✅ FIRST ask a clear question
        * ✅ WAIT for confirmation

---

## 🧱 Module Structure & Responsibilities

`com.infra.core.crypto`
* `config` – Configuration property bindings & auto-configuration.
* `enums` – Strictly typed representations of supported algorithms.
* `exception` – Hierarchy of crypto-specific runtime exceptions.
* `util` – Static helpers for data encoding (Base64, Hex, PEM).
* `provider` – Interfaces for dynamic key resolution and KMS integration (`KeyProvider`).
* `hashing` – One-way cryptographic hashing and password hashing.
* `mac` – Message Authentication Codes (HMAC).
* `symmetric` – AES encryption pipelines (GCM & CBC-HMAC).
* `asymmetric` – RSA and EdDSA implementations (Encryption & Signatures).
* `keys` – Parsing and formatting key materials.

---

# 🚀 IMPLEMENTATION PLAN (PHASE-WISE)

## ✅ PHASE 1 — FOUNDATION LAYER
**Goal:** Establish type safety, error handling, key resolution contracts, and configuration bindings before cryptographic operations are built.

### 1.1 Enums
Define strict capabilities to eliminate free-form strings.
* `HashAlgorithm`: `SHA_256`, `SHA_512`, `SHA3_256`, `SHA3_512`
* `PasswordHashAlgorithm`: `ARGON2ID` (Disallow bcrypt/scrypt to ensure modern security hardness).
* `MacAlgorithm`: `HMAC_SHA256`, `HMAC_SHA512`
* `SymmetricAlgorithm`: `AES_256_GCM`, `AES_256_CBC`
* `IvStrategy`: `RANDOM`, `PROVIDED` (No `FIXED` except internally for unit tests).
* `AsymmetricAlgorithm`: `RSA`, `ED25519`
* `RsaPadding`: `OAEP_SHA256` (Default), `OAEP_SHA512`, `PKCS1_V1_5` (Strictly marked deprecated/legacy).
* `RsaKeySize`: `RSA_2048`, `RSA_3072`, `RSA_4096`

### 1.2 Exceptions
Base `CryptographyException extends RuntimeException`.
Derived Exceptions:
* `EncryptionFailureException`
* `DecryptionFailureException` (Masks padding errors vs MAC errors to prevent padding oracle attacks).
* `HashingFailureException`
* `KeyResolutionException`
* `KeyParsingException`
* `InvalidConfigurationException`

### 1.3 Configuration Properties
Use `@ConfigurationProperties(prefix = "infra.crypto")` heavily validated via Jakarta annotations.
* `InfraCryptoProperties` (Root wrapper)
* `HashingProperties` (e.g., default algorithm)
* `PasswordProperties` (Argon2 tunables: iterations, memory validation)
* `SymmetricProperties` (e.g., default IV strategy)
* `AsymmetricProperties` (e.g., default RSA padding)
* **⚠️ Security Constraint:** Properties must configure *behavior*, not store raw key materials. Keys should be resolved dynamically.

### 1.4 Encoding Utilities (`CryptoEncoderUtils`)
`final class` with `private` constructor.
Methods must facilitate conversion between `byte[]` and `String`:
* `toBase64`, `fromBase64` / `toBase64UrlSafe`, `fromBase64UrlSafe`
* `toHex`, `fromHex` (lower-case by default)
* `toPem(String type, byte[] data)`, `fromPem(String pem)`
* `toDer(Key key)`, `fromDer(byte[] der)`

### 1.5 Key Resolution Providers
Define the contract for fetching keys to decouple storage from usage.
* Segregate into `SymmetricKeyProvider` (returning `SymmetricKeyMaterial`) and `AsymmetricKeyProvider` (returning `AsymmetricKeyMaterial`) for strict type safety.
* Methods: `getActiveKey(String context)` and `getKeyById(String keyId)`.
* Implement `PropertiesKeyProvider` versions for local testing/defaults, paving the way for future Vault integrations.

---

## ✅ PHASE 2 — HASHING + PASSWORD + MAC
**Goal:** Implement one-way cryptographic operations and authentication codes.

### 2.1 Hashing (`StandardHashingServiceImpl`)
* **Scope:** Standard byte-level hashing using `MessageDigest`.
* **Support:** SHA-256, SHA-512, SHA3-256, SHA3-512.
* **API:** `byte[] hash(byte[] input, HashAlgorithm algorithm)`, `boolean verify(byte[] input, byte[] expectedHash, HashAlgorithm algorithm)`
* **Constraint:** Verification MUST use constant-time equality checks (`MessageDigest.isEqual`) to prevent timing leakages.

### 2.2 Password Hashing (`Argon2PasswordHashingServiceImpl`)
* **Scope:** Tailored specifically for user credentials.
* **Requirements:** Argon2id ONLY. Generate a standardized PHC format string (salt included).
* **API:** Should accept `char[]` or `byte[]` for passwords to allow memory wiping, outputting the hashed `String`.
* **Defaults:** 16-byte random salt, 32-byte hash length, 64MB memory, 3 iterations, 1 thread parallelism.

### 2.3 MAC (`HmacServiceImpl`)
* **Scope:** Message Authentication Codes using `javax.crypto.Mac`.
* **Support:** HmacSHA256, HmacSHA512.
* **Constraint:** Do not mix with generic hashing logic. Outputs must be verified using constant-time comparison.

---

## ✅ PHASE 3 — AES-GCM (PRIMARY ENCRYPTION)
**Goal:** Implement modern Authenticated Encryption with Associated Data (AEAD) as the default symmetric standard, fully supporting key rotation.

### 3.1 `AesGcmEncryptionServiceImpl`
* **Algorithm:** `AES/GCM/NoPadding`
* **API Support for AAD:** Primary methods `byte[] encrypt(byte[] payload, byte[] aad)` / `byte[] decrypt(byte[] payload, byte[] aad)`. Convenience overloads (e.g., `byte[] encrypt(byte[] payload)`) default to empty AAD `new byte[0]`.
* **IV Rules:** 12 bytes exactly, utilizing `SecureRandom`.
* **Tag Size:** 128 bits (16 bytes).
* **Output Format (Strict):** `[Version Byte (1)] + [KID Length (1)] + [KID Bytes] + [IV (12)] + [Ciphertext + GCM Tag]`
* **Flow (Encryption):** Ask Key Provider for active key -> Extract KID -> Generate IV -> Encrypt/Auth (with AAD) -> Prepend headers.
* **Flow (Decryption):** Parse Version & KID -> Ask Key Provider for key by ID -> Extract IV -> Decrypt (verifying AAD).

---

## ✅ PHASE 4 — AES-CBC + HMAC (LEGACY SUPPORT)
**Goal:** Support older integrations requiring AES-CBC safely by enforcing Encrypt-then-MAC (EtM).

### 4.1 `AesCbcHmacEncryptionServiceImpl`
* **Algorithms:** `AES/CBC/PKCS5Padding` and `HmacSHA256`.
* **Output Format:** `[Version Byte (1)] + [KID Length (1)] + [KID Bytes] + [IV (16 bytes)] + [Ciphertext] + [HMAC (32 bytes)]`
* **⚠️ Critical Security Rules (EtM):**
    1. **Strict Key Separation:** Requires two distinct keys (AES Key and HMAC Key).
    2. **Encryption:** Encrypt with IV to yield Ciphertext, compute HMAC over `(Version + KID + IV + Ciphertext)`.
    3. **Decryption:** MUST parse the payload and compute expected HMAC. If computed HMAC mismatches the attached HMAC, throw `DecryptionFailureException` IMMEDIATELY. **Never decrypt an unauthenticated ciphertext.**

---

## ✅ PHASE 5 — ASYMMETRIC (RSA + SIGNATURES)
**Goal:** Public key cryptography for encryption and signatures.

### 5.1 `RsaEncryptionServiceImpl`
* **Algorithm Limits:** Prevent `IllegalBlockSizeException` explicitly with preemptive size checks based on Padding.
* **Key Sizes:** 2048 (default), 3072, 4096.
* **Paddings:** OAEP-SHA256 (Primary), OAEP-SHA512, PKCS1_V1_5 (Legacy only).
* **Output Format:** Must strictly adhere to Key Rotation Binary Header standard: `[Version Byte (1)] + [KID Length (1)] + [KID Bytes] + [Ciphertext]`.

### 5.2 Digital Signatures
* **`RsaDigitalSignatureServiceImpl`:** Utilizes RSA-PSS (`RSASSA-PSS`) by default.
* **`EdDsaDigitalSignatureServiceImpl`:** Support `Ed25519`.
* **Output Format:** Must strictly adhere to Key Rotation Binary Header standard: `[Version Byte (1)] + [KID Length (1)] + [KID Bytes] + [Signature]`.

---

## ✅ PHASE 6 — KEY MANAGEMENT
**Goal:** Parse, generate, and structure Key Material safely without manually parsing Base64 strings across services.

### 6.1 `KeyParser` & `KeySerializer`
* Safely unmarshal PEM and DER streams.
* Validated parsing: If parsing an RSA key, assert the resulting `Key` object is indeed safely cast to `RSAPrivateKey` or `RSAPublicKey`.

### 6.2 Data Models (`KeyMaterial`)
* Value objects representing active keys. Wrapper structures like `AsymmetricKeyMaterial(KID, PublicKey, PrivateKey)` and `SymmetricKeyMaterial(KID, SecretKey, MacKey)`.
* **KID Limitations:** KIDs are strictly UTF-8 encoded strings up to a maximum of 255 bytes limit.

---

## ✅ PHASE 7 — AUTO-CONFIGURATION
**Goal:** Provide zero-friction integration for Spring boot developers.

### 7.1 `InfraCryptoAutoConfiguration`
* **Provider Safety:** BouncyCastle must be used explicitly per instance (e.g., `Cipher.getInstance("AES/GCM/NoPadding", new BouncyCastleProvider())` or localized string `"BC"`) to avoid polluting the global JVM `Security` state.
* Constructs implementations as beans (`KeyProvider`, `HashingService`, `SymmetricEncryptionService`, etc.).
* Uses `@ConditionalOnMissingBean` to allow specific application overrides gracefully.
* Uses `@ConditionalOnProperty` to selectively initialize beans.

### 7.2 Starter Registration
* Ensure project module follows the custom starter convention: `infra-crypto-spring-boot-starter`.
* File: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
* Add: `com.infra.core.crypto.config.InfraCryptoAutoConfiguration`

---

## ✅ PHASE 8 — TESTING
**Goal:** Mathematically prove non-tamperability and functional correctness.

### Must Cover:
* **Utility Bounds:** Bad hex/Base64 offsets map to runtime errors consistently.
* **Key Resolution:** Rotating the active key results in a new KID in the ciphertext; old ciphertexts decrypt successfully via historical lookup.
* **Threading:** Run parallel `IntStream.range().parallel()` encrypt/decrypt cycles to ensure no JCA concurrency collisions occur.
* **Hashing Validation:** Constant time validations explicitly tested.
* **AES-GCM AEAD / Malleability:** Tamper testing – flip a single byte in ciphertext, AAD, or IV and assert failure (`DecryptionFailureException`).
* **AES-CBC + HMAC:** Verify an invalid HMAC successfully bypasses inner CBC block executions ensuring no Padding Oracle leakage.

---

# 🚀 Execution Strategy for Antigravity
When implementing:
1. Always implement **interface first** to establish API contracts.
2. Then **default implementation** matching strict criteria.
3. Then **tests** verifying both happy paths and security boundary tampering.
4. Strictly verify logic against security rules, particularly **exception leaks** and **thread safety**.
5. Then move to next phase.
6. **If confused at any step → ASK FIRST, then implement.**

---

# 🏁 End Goal
A developer should seamlessly be capable of utilizing robust, rotation-ready cryptographic functionality simply through Dependency Injection:

```java
@Autowired
private SymmetricEncryptionService encryptionService;

// Internally resolves active key, manages IV lifecycle, cipher execution, and GCM Tags securely.
// Yields a forward-compatible payload ready for database storage.
byte[] securePayload = encryptionService.encrypt(data.getBytes(), "userId-123".getBytes());
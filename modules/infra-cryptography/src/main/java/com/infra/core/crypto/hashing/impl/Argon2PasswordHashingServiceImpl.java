package com.infra.core.crypto.hashing.impl;

import com.infra.core.crypto.enums.PasswordHashAlgorithm;
import com.infra.core.crypto.exception.HashingFailureException;
import com.infra.core.crypto.hashing.PasswordHashingService;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Service;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Production implementation of {@link PasswordHashingService} using BouncyCastle's Argon2id.
 *
 * <h2>Algorithm Choice: Argon2id</h2>
 * <p>Argon2id is the winner of the Password Hashing Competition (2015) and is the current
 * NIST-recommended algorithm (SP 800-63B). It combines the side-channel resistance of
 * Argon2i with the GPU-resistance of Argon2d, making it the safest general-purpose choice.
 * bcrypt and scrypt are intentionally excluded from this library.</p>
 *
 * <h2>Output Format: PHC String</h2>
 * <p>Outputs use a PHC-compatible format for self-describing, migration-friendly storage:
 * <pre>$argon2id$v=19$m=65536,t=3,p=1$&lt;base64-salt&gt;$&lt;base64-hash&gt;</pre>
 * The salt and all parameters are embedded in the string, making it completely self-contained
 * for verification.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>{@link Argon2BytesGenerator} is NOT thread-safe. A new instance is created per operation.
 * {@link SecureRandom} is thread-safe and shared as a single instance.</p>
 *
 * <h2>Memory Wiping</h2>
 * <p>Password {@code char[]} arrays are zeroed after the password bytes are derived from them.
 * The intermediate {@code byte[]} representation of the password is also zeroed before the
 * method returns. Note: JVM GC may compact heap memory, so wiping is best-effort but
 * significantly reduces the exposure window.</p>
 */
@Service
public class Argon2PasswordHashingServiceImpl implements PasswordHashingService {

    // NIST SP 800-63B / OWASP recommended minimums for Argon2id
    private static final int SALT_LENGTH_BYTES   = 16;
    private static final int HASH_LENGTH_BYTES   = 32;
    private static final int MEMORY_KB           = 65536; // 64 MB
    private static final int ITERATIONS          = 3;
    private static final int PARALLELISM         = 1;

    // SecureRandom is thread-safe; a single shared instance is fine.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String hashPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new HashingFailureException("Password must not be null or empty");
        }

        byte[] salt = generateSalt();
        byte[] passwordBytes = null;
        byte[] hash = new byte[HASH_LENGTH_BYTES];

        try {
            passwordBytes = toBytes(password);

            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(MEMORY_KB)
                    .withIterations(ITERATIONS)
                    .withParallelism(PARALLELISM)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13) // v=19 (decimal 19 == 0x13)
                    .build();

            // A new generator per call — Argon2BytesGenerator is NOT thread-safe.
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            generator.generateBytes(passwordBytes, hash);

            return encodeToPHC(salt, hash, params);

        } catch (Exception e) {
            throw new HashingFailureException("Argon2id password hashing failed", e);
        } finally {
            // Best-effort memory wiping to reduce exposure window.
            if (passwordBytes != null) {
                Arrays.fill(passwordBytes, (byte) 0);
            }
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public boolean verifyPassword(char[] password, String encodedHash) {
        if (password == null || password.length == 0) {
            throw new HashingFailureException("Password must not be null or empty for verification");
        }
        if (encodedHash == null || encodedHash.isBlank()) {
            throw new HashingFailureException("Encoded hash must not be null or blank");
        }

        byte[] passwordBytes = null;

        try {
            PhcHash phc = parsePhc(encodedHash);

            passwordBytes = toBytes(password);

            byte[] computedHash = new byte[phc.hash.length];

            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(phc.salt)
                    .withMemoryAsKB(phc.memoryKb)
                    .withIterations(phc.iterations)
                    .withParallelism(phc.parallelism)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .build();

            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            generator.generateBytes(passwordBytes, computedHash);

            // Constant-time comparison — prevents timing side-channels.
            return constantTimeEquals(phc.hash, computedHash);

        } catch (HashingFailureException e) {
            throw e; // Re-throw our own exceptions directly
        } catch (Exception e) {
            throw new HashingFailureException("Argon2id password verification failed", e);
        } finally {
            if (passwordBytes != null) {
                Arrays.fill(passwordBytes, (byte) 0);
            }
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public PasswordHashAlgorithm getAlgorithm() {
        return PasswordHashAlgorithm.ARGON2ID;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Converts a char[] password to bytes via UTF-8 CharBuffer conversion to avoid
     * creating an intermediate String (which would be interned in the JVM String pool
     * and uncontrollably GC'd, leaving secrets in memory).
     */
    private byte[] toBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        java.nio.ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(
                byteBuffer.array(),
                byteBuffer.position(),
                byteBuffer.limit());
        // Wipe the intermediate ByteBuffer backing array
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }

    /**
     * Encodes the computed hash into PHC format:
     * {@code $argon2id$v=19$m=65536,t=3,p=1$<base64-salt>$<base64-hash>}
     */
    private String encodeToPHC(byte[] salt, byte[] hash, Argon2Parameters params) {
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        return String.format(
                "$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
                params.getVersion(),
                params.getMemory(),
                params.getIterations(),
                params.getLanes(),
                encoder.encodeToString(salt),
                encoder.encodeToString(hash));
    }

    /**
     * Parses a PHC-format string back into its components.
     * Expected format: {@code $argon2id$v=19$m=65536,t=3,p=1$<base64-salt>$<base64-hash>}
     */
    private PhcHash parsePhc(String encodedHash) {
        try {
            // Split: ["", "argon2id", "v=19", "m=65536,t=3,p=1", "<salt>", "<hash>"]
            String[] parts = encodedHash.split("\\$");
            if (parts.length != 6) {
                throw new HashingFailureException("Invalid PHC hash format");
            }

            // parts[0] is empty string (before first "$"), parts[1] is "argon2id"
            if (!"argon2id".equals(parts[1])) {
                throw new HashingFailureException("Unsupported algorithm in PHC hash: " + parts[1]);
            }

            String[] costParams = parts[3].split(",");
            int memory = 0, iterations = 0, parallelism = 0;

            for (String kv : costParams) {
                String[] pair = kv.split("=");
                switch (pair[0]) {
                    case "m" -> memory = Integer.parseInt(pair[1]);
                    case "t" -> iterations = Integer.parseInt(pair[1]);
                    case "p" -> parallelism = Integer.parseInt(pair[1]);
                    default -> throw new HashingFailureException("Unknown PHC parameter: " + pair[0]);
                }
            }

            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[4]);
            byte[] hash = decoder.decode(parts[5]);

            return new PhcHash(salt, hash, memory, iterations, parallelism);

        } catch (HashingFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new HashingFailureException("Failed to parse PHC encoded hash", e);
        }
    }

    /**
     * Constant-time byte array comparison. Unlike {@link Arrays#equals}, this does not
     * short-circuit on the first mismatch, preventing timing-based attacks.
     * We do NOT use MessageDigest.isEqual here as it is designed for hash digests of fixed
     * length; this method handles arbitrary byte arrays and enforces length equality explicitly
     * without branching early.
     */
    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    /** Parsed components of a PHC-format Argon2id hash string. */
    private record PhcHash(byte[] salt, byte[] hash, int memoryKb, int iterations, int parallelism) {}
}

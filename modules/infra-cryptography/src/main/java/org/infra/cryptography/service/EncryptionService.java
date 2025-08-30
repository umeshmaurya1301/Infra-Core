package org.infra.cryptography.service;

import lombok.extern.slf4j.Slf4j;
import org.infra.commons.constants.ApplicationConstants;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encryption and decryption operations.
 */
@Slf4j
@Service
public class EncryptionService {
    
    private final SecureRandom secureRandom;
    
    public EncryptionService() {
        this.secureRandom = new SecureRandom();
        log.info("EncryptionService initialized with algorithm: {}", ApplicationConstants.DEFAULT_ALGORITHM);
    }
    
    /**
     * Generates a new AES key.
     */
    public SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ApplicationConstants.DEFAULT_ALGORITHM);
        keyGenerator.init(ApplicationConstants.DEFAULT_KEY_LENGTH);
        return keyGenerator.generateKey();
    }
    
    /**
     * Encrypts plaintext using AES encryption.
     */
    public String encrypt(String plaintext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ApplicationConstants.DEFAULT_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }
    
    /**
     * Decrypts ciphertext using AES decryption.
     */
    public String decrypt(String ciphertext, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(ApplicationConstants.DEFAULT_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Converts a base64 encoded key string to SecretKey.
     */
    public SecretKey keyFromString(String keyString) {
        byte[] decodedKey = Base64.getDecoder().decode(keyString);
        return new SecretKeySpec(decodedKey, ApplicationConstants.DEFAULT_ALGORITHM);
    }
    
    /**
     * Converts a SecretKey to base64 encoded string.
     */
    public String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}

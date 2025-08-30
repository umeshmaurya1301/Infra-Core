package org.infra.cryptography;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cryptography module application for encryption and cryptographic utilities.
 * This module depends on infra-commons and provides cryptographic services.
 */
@SpringBootApplication(scanBasePackages = {
    "org.infra.commons",
    "org.infra.cryptography"
})
public class CryptographyApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptographyApplication.class, args);
    }
}

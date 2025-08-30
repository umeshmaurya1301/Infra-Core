package org.infra.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Security module application for authentication and authorization.
 * This module depends on infra-commons and infra-core modules.
 */
@SpringBootApplication(scanBasePackages = {
    "org.infra.commons",
    "org.infra.core", 
    "org.infra.security"
})
public class SecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}

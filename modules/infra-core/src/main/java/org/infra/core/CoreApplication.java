package org.infra.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point that assembles all infrastructure modules.
 * This module serves as the application starter and configuration aggregator.
 */
@SpringBootApplication(scanBasePackages = {
    "org.infra.commons",
    "org.infra.core",
    "org.infra.security",
    "org.infra.cryptography", 
    "org.infra.audit",
    "org.infra.validation"
})
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

package org.infra.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Core module application containing core services and business logic.
 * This module depends on infra-commons and provides fundamental services.
 */
@SpringBootApplication(scanBasePackages = {
    "org.infra.commons",
    "org.infra.core"
})
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

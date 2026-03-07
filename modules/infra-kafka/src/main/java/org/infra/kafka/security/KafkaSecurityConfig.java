package org.infra.kafka.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Phase 6 — Configures TLS/SSL and SASL authentication for Kafka clients.
 *
 * <p>Centralized component that applies security properties to the native
 * Kafka configuration map. It transparently resolves keystore/truststore
 * locations from the classpath (extracting to a temp file if running from
 * a "fat" jar) or the filesystem.
 */
@Slf4j
@Configuration
public class KafkaSecurityConfig {

    private final InfraKafkaProperties properties;
    private final SaslConfigProvider saslProvider;
    private final ResourceLoader resourceLoader;

    public KafkaSecurityConfig(InfraKafkaProperties properties, SaslConfigProvider saslProvider) {
        this.properties = properties;
        this.saslProvider = saslProvider;
        this.resourceLoader = new DefaultResourceLoader();
    }

    /**
     * Applies the configured security protocol and associated properties
     * (SSL/SASL) to the provided client configuration map.
     *
     * @param config the Kafka client configuration map
     */
    public void applySecurity(Map<String, Object> config) {
        InfraKafkaProperties.SecurityProperties secProps = properties.getSecurity();
        String protocol = secProps.getProtocol();

        if (protocol == null || "PLAINTEXT".equalsIgnoreCase(protocol)) {
            return; // Fast path: no security
        }

        config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol.toUpperCase());
        log.info("[infra-kafka] Security protocol ENABLED: {}", protocol.toUpperCase());

        if (protocol.contains("SSL")) {
            applySsl(config, secProps.getSsl());
        }

        if (protocol.contains("SASL")) {
            applySasl(config, secProps.getSasl());
        }
    }

    private void applySsl(Map<String, Object> config, InfraKafkaProperties.SecurityProperties.SslProperties ssl) {
        if (hasText(ssl.getTruststoreLocation())) {
            config.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, resolveResource(ssl.getTruststoreLocation()));
            config.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ssl.getTruststorePassword());
        }

        if (hasText(ssl.getKeystoreLocation())) {
            config.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, resolveResource(ssl.getKeystoreLocation()));
            config.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, ssl.getKeystorePassword());
            if (hasText(ssl.getKeyPassword())) {
                config.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.getKeyPassword());
            }
        }
    }

    private void applySasl(Map<String, Object> config, InfraKafkaProperties.SecurityProperties.SaslProperties sasl) {
        config.put(SaslConfigs.SASL_MECHANISM, sasl.getMechanism());
        config.put(SaslConfigs.SASL_JAAS_CONFIG, saslProvider.getJaasConfig(sasl));
        log.info("[infra-kafka] SASL mechanism configured: {}", sasl.getMechanism());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Resolves a spring resource location (e.g. "classpath:...", "file:...")
     * into a physical absolute file path string usable by the Kafka client.
     */
    private String resolveResource(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            try {
                // If it's a direct file resource (IDE or unpacked), this will work
                return resource.getFile().getAbsolutePath();
            } catch (IOException ex) {
                // In a fat jar, getFile() throws IOException.
                // We must extract the classpath resource to a temp directory.
                log.debug("Resource {} is not a standard file (likely in a JAR). Extracting to temp dir...", location);
                File tempFile = extractToTempFile(resource);
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve Kafka security resource: " + location, e);
        }
    }

    private File extractToTempFile(Resource resource) throws IOException {
        String filename = resource.getFilename() != null ? resource.getFilename() : "kafka-cert.tmp";
        Path tempPath = Files.createTempFile("infra-kafka-", "-" + filename);
        tempPath.toFile().deleteOnExit();

        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempPath.toFile();
    }
}

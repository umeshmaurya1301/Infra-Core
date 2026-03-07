package org.infra.kafka.security;

import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.springframework.stereotype.Component;

/**
 * Phase 6 — Generates the JAAS configuration string required for SASL authentication.
 *
 * <p>Supports PLAIN and SCRAM-SHA-256 / SCRAM-SHA-512 mechanisms out of the box.
 * The generated string is passed to {@code sasl.jaas.config} in the Kafka client properties.
 */
@Component
public class SaslConfigProvider {

    /**
     * Builds the JAAS LoginModule string based on the configured mechanism and credentials.
     *
     * @param sasl the configured SASL properties
     * @return the formatted JAAS string
     */
    public String getJaasConfig(InfraKafkaProperties.SecurityProperties.SaslProperties sasl) {
        String mechanism = sasl.getMechanism() != null ? sasl.getMechanism().toUpperCase() : "PLAIN";
        
        if (mechanism.startsWith("SCRAM")) {
            return String.format(
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                    sasl.getUsername(),
                    sasl.getPassword()
            );
        } else {
            return String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    sasl.getUsername(),
                    sasl.getPassword()
            );
        }
    }
}

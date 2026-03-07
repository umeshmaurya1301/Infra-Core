package org.infra.kafka.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;

/**
 * Phase 7 — Exactly-Once Semantics (EOS v2) and Transactions.
 *
 * <p>Activated when {@code infra.kafka.transaction.enabled=true}.
 * Registers a {@link KafkaTransactionManager} that integrates with Spring's
 * {@code @Transactional} annotation, allowing consume-transform-produce pipelines
 * to execute atomically.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "infra.kafka",
        name = "transaction.enabled",
        havingValue = "true"
)
public class KafkaTransactionConfig {

    /**
     * Creates a {@link KafkaTransactionManager} bound to the primary {@link ProducerFactory}.
     *
     * @param producerFactory the factory used for transactional sends
     * @return the transaction manager wrapper
     */
    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager(
            ProducerFactory<String, Object> producerFactory) {
        log.info("[infra-kafka] Transaction mode ENABLED. KafkaTransactionManager initialized.");
        return new KafkaTransactionManager<>(producerFactory);
    }
}

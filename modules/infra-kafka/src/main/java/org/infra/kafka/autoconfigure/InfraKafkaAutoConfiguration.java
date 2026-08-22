package org.infra.kafka.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.infra.kafka.admin.KafkaAdminConfig;
import org.infra.kafka.consumer.KafkaConsumerConfig;
import org.infra.kafka.observability.ObservabilityConfig;
import org.infra.kafka.producer.KafkaProducerConfig;
import org.infra.kafka.retry.NonBlockingRetryConfig;
import org.infra.kafka.retry.RetryTopicConfig;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.infra.kafka.security.SaslConfigProvider;
import org.infra.kafka.serialization.AvroSerializerConfig;
import org.infra.kafka.serialization.SchemaRegistryHealthIndicator;
import org.infra.kafka.transaction.KafkaTransactionConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot Auto-configuration entry point for the infra-kafka library.
 *
 * <p>Loaded automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <h3>What gets auto-configured</h3>
 * <ul>
 *   <li>{@link InfraKafkaProperties} — all {@code infra.kafka.*} property bindings</li>
 *   <li><b>Phase 1</b> — {@link KafkaProducerConfig}: idempotent producer factory,
 *       KafkaTemplate, KafkaMessagePublisher</li>
 *   <li><b>Phase 2</b> — {@link KafkaConsumerConfig}: consumer factory,
 *       ConcurrentKafkaListenerContainerFactory</li>
 *   <li><b>Phase 3</b> — {@link RetryTopicConfig}: exponential-backoff retry +
 *       DLQ routing + deserialization error handler</li>
 *   <li><b>Phase 4</b> — {@link ObservabilityConfig}: producer logging interceptor,
 *       Micrometer counters/timers, consumer MDC propagation</li>
 *   <li><b>Phase 5</b> — {@link AvroSerializerConfig}: Avro serde + Schema Registry
 *       integration (opt-in via {@code infra.kafka.serialization.type=avro}); the
 *       {@link SchemaRegistryHealthIndicator} contributes to Actuator health in Avro mode</li>
 *   <li><b>Phase 6</b> — {@link KafkaSecurityConfig} + {@link SaslConfigProvider}: SSL/SASL
 *       wiring. These are unconditional beans (they no-op on {@code PLAINTEXT}) and are a
 *       hard dependency of the producer, consumer and Avro configurations, so they must be
 *       registered here.</li>
 *   <li><b>Phase 7</b> — {@link KafkaTransactionConfig}: transaction manager
 *       (opt-in via {@code infra.kafka.transaction.enabled=true})</li>
 *   <li><b>Phase 8</b> — {@link KafkaAdminConfig}: topic auto-creation
 *       (opt-in via {@code infra.kafka.admin.auto-create=true})</li>
 * </ul>
 *
 * <p><b>Wiring note:</b> this library is delivered as an auto-configuration, not a
 * component-scanned package, so every {@code @Configuration}/{@code @Component} it owns
 * must be listed in {@link Import} below. Classes that are opt-in carry their own
 * {@code @ConditionalOnProperty}/{@code @ConditionalOnClass} guards, so importing them
 * unconditionally is safe — they simply back off when their feature is disabled.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "infra.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InfraKafkaProperties.class)
@Import({
        // Phase 6 — security primitives; hard dependency of the configs below, so imported first
        SaslConfigProvider.class,
        KafkaSecurityConfig.class,
        // Phase 1–5 — producer, consumer, retry/DLQ, observability, Avro
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        RetryTopicConfig.class,
        NonBlockingRetryConfig.class,
        ObservabilityConfig.class,
        AvroSerializerConfig.class,
        SchemaRegistryHealthIndicator.class,
        // Phase 7–8 — opt-in transactions and topic auto-creation (self-gated)
        KafkaTransactionConfig.class,
        KafkaAdminConfig.class
})
public class InfraKafkaAutoConfiguration {

    public InfraKafkaAutoConfiguration() {
        log.info("[infra-kafka] Auto-configuration loaded — " +
                "Producer (P1) + Consumer (P2) + Retry/DLQ (P3) + Observability (P4) + Security (P6) active. " +
                "Avro (P5) activates when infra.kafka.serialization.type=avro; " +
                "Transactions (P7) when infra.kafka.transaction.enabled=true; " +
                "Admin auto-create (P8) when infra.kafka.admin.auto-create=true.");
    }
}

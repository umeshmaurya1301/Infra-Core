package org.infra.kafka.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.infra.kafka.consumer.KafkaConsumerConfig;
import org.infra.kafka.observability.ObservabilityConfig;
import org.infra.kafka.producer.KafkaProducerConfig;
import org.infra.kafka.retry.RetryTopicConfig;
import org.infra.kafka.serialization.AvroSerializerConfig;
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
 *       integration (opt-in via {@code infra.kafka.serialization.type=avro})</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "infra.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InfraKafkaProperties.class)
@Import({
        KafkaProducerConfig.class,
        KafkaConsumerConfig.class,
        RetryTopicConfig.class,
        ObservabilityConfig.class,
        AvroSerializerConfig.class
})
public class InfraKafkaAutoConfiguration {

    public InfraKafkaAutoConfiguration() {
        log.info("[infra-kafka] Auto-configuration loaded — " +
                "Producer (P1) + Consumer (P2) + Retry/DLQ (P3) + Observability (P4) active. " +
                "Avro (P5) activates when infra.kafka.serialization.type=avro.");
    }
}

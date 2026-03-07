package org.infra.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.error.DefaultKafkaErrorHandler;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring {@link Configuration} that creates and exposes the Kafka consumer infrastructure.
 *
 * <h3>What this class wires</h3>
 * <ol>
 *   <li>{@link ConsumerFactory} — thread-safe factory that manages consumer lifecycle</li>
 *   <li>{@link ConcurrentKafkaListenerContainerFactory} — container factory for
 *       {@code @KafkaListener} / {@code @InfraKafkaListener} methods</li>
 * </ol>
 *
 * <h3>Key Defaults</h3>
 * <ul>
 *   <li>{@code AckMode = RECORD} — offsets committed after each record is processed</li>
 *   <li>{@code enable.auto.commit = false} — manual ack prevents message loss</li>
 *   <li>{@code JsonDeserializer} wrapped in {@link ErrorHandlingDeserializer} to
 *       route deserialisation failures to DLQ without crashing the consumer</li>
 *   <li>{@code CooperativeStickyAssignor} — minimises stop-the-world rebalances</li>
 *   <li>{@link DefaultKafkaErrorHandler} wired as the container error handler (Phase 3)
 *       — provides exponential backoff retry + DLQ routing out of the box</li>
 *   <li>{@link RecordInterceptor} wired (Phase 4) for MDC propagation and consumer metrics</li>
 * </ul>
 *
 * <p>All beans are guarded with {@link ConditionalOnMissingBean} so consumer
 * applications can override any bean as needed.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    private final InfraKafkaProperties properties;
    private final KafkaSecurityConfig securityConfig;

    public KafkaConsumerConfig(InfraKafkaProperties properties, KafkaSecurityConfig securityConfig) {
        this.properties = properties;
        this.securityConfig = securityConfig;
    }

    /**
     * Builds the Kafka consumer configuration map from {@link InfraKafkaProperties}.
     *
     * <p>The {@link JsonDeserializer} is wrapped inside an
     * {@link ErrorHandlingDeserializer} so that poison-pill / malformed messages
     * are routed to the configured error handler rather than crashing the consumer.
     */
    public Map<String, Object> consumerConfigs() {
        InfraKafkaProperties.ConsumerProperties consumer = properties.getConsumer();
        Map<String, Object> config = new HashMap<>();

        // ── Core connectivity ────────────────────────────────────────────────
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.getGroupId());

        // ── Offset management ────────────────────────────────────────────────
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.isEnableAutoCommit());

        // ── Throughput ───────────────────────────────────────────────────────
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());

        // ── Partition assignment ─────────────────────────────────────────────
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                consumer.getPartitionAssignmentStrategy());

        // ── Deserializers — ErrorHandlingDeserializer wraps the real one ─────
        // Key: plain StringDeserializer (keys are always strings)
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        // Delegate deserializers used by ErrorHandlingDeserializer
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class);

        // Trusted packages for JSON deserialization
        config.put(JsonDeserializer.TRUSTED_PACKAGES, consumer.getTrustedPackages());
        // Do not expect Spring type info headers (clean, cross-service payloads)
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        log.info("[infra-kafka] ConsumerFactory initialized — groupId={}, autoOffsetReset={}, " +
                        "concurrency={}, enableAutoCommit={}",
                consumer.getGroupId(),
                consumer.getAutoOffsetReset(),
                consumer.getConcurrency(),
                consumer.isEnableAutoCommit());

        // ── Phase 7: Transactions / EOS ──────────────────────────────────────
        if (properties.getTransaction().isEnabled()) {
            config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            log.info("[infra-kafka] Transaction mode ENABLED. Consumer isolation.level set to read_committed.");
        }

        // ── Phase 6: Apply security settings ─────────────────────────────────
        securityConfig.applySecurity(config);

        return config;
    }

    /**
     * Creates the {@link ConsumerFactory} used to instantiate Kafka consumers.
     *
     * <p>Uses {@link DefaultKafkaConsumerFactory} which creates a new consumer
     * per listener thread (bounded by {@code concurrency}).
     */
    @Bean
    @ConditionalOnMissingBean(ConsumerFactory.class)
    public ConsumerFactory<String, Object> infraKafkaConsumerFactory() {
        DefaultKafkaConsumerFactory<String, Object> factory =
                new DefaultKafkaConsumerFactory<>(consumerConfigs());
        log.info("[infra-kafka] ConsumerFactory bean created");
        return factory;
    }

    /**
     * Creates the {@link ConcurrentKafkaListenerContainerFactory} bean.
     *
     * <p>This factory is used by {@code @KafkaListener} / {@code @InfraKafkaListener}
     * annotated methods. Key configuration:
     * <ul>
     *   <li><b>AckMode.RECORD</b> — offset committed immediately after each successful record</li>
     *   <li><b>Concurrency</b> — number of concurrent listener threads (bounded by partition count)</li>
     *   <li><b>CommonErrorHandler</b> (Phase 3) — wires {@link DefaultKafkaErrorHandler} when
     *       available in context, providing retry-with-backoff and DLQ routing for every listener</li>
     *   <li><b>RecordInterceptor</b> (Phase 4) — wires {@code infraKafkaConsumerInterceptor} when
     *       available, enabling MDC propagation and consumer metrics per record</li>
     * </ul>
     *
     * @param consumerFactory   the consumer factory (always present)
     * @param errorHandler      optional Phase 3 error handler; absent when retry is disabled
     * @param recordInterceptor optional Phase 4 MDC + metrics interceptor
     */
    @Bean("infraKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "infraKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> infraKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            ObjectProvider<DefaultKafkaErrorHandler> errorHandler,
            @Qualifier("infraKafkaConsumerInterceptor")
            ObjectProvider<RecordInterceptor<String, Object>> recordInterceptor) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConsumer().getConcurrency());

        // RECORD ack-mode: commit offset after each record (safe default)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Phase 8: Graceful shutdown max timeout waiting for in-flight records bounds
        if (properties.getShutdown().getTimeout() != null) {
            factory.getContainerProperties().setShutdownTimeout(properties.getShutdown().getTimeout().toMillis());
        }

        // Phase 3: wire the error handler when the bean is present in the context
        errorHandler.ifAvailable(handler -> {
            factory.setCommonErrorHandler(handler);
            log.info("[infra-kafka] DefaultKafkaErrorHandler wired — retry + DLQ active");
        });

        // Phase 4: wire the MDC + metrics record interceptor when present
        recordInterceptor.ifAvailable(interceptor -> {
            factory.setRecordInterceptor(interceptor);
            log.info("[infra-kafka] Consumer RecordInterceptor wired — MDC + metrics active");
        });

        log.info("[infra-kafka] ConcurrentKafkaListenerContainerFactory initialized — concurrency={}",
                properties.getConsumer().getConcurrency());

        return factory;
    }
}

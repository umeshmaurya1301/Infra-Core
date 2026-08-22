package org.infra.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
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
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.Arrays;
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
 *   <li>Raw {@code byte[]} value deserializer + a {@link ByteArrayJacksonJsonMessageConverter}
 *       on the factory that infers the payload type from the listener method signature</li>
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
     * <p>The value is read as raw {@code byte[]}; JSON binding to the listener's payload type is
     * performed by {@link #infraKafkaMessageConverter()} on the container factory. The key uses
     * an {@link ErrorHandlingDeserializer} so a malformed key cannot crash the consumer.
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

        // ── Deserializers ────────────────────────────────────────────────────
        // Key: StringDeserializer wrapped in ErrorHandlingDeserializer (poison-pill safe).
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);

        // Value: raw bytes on the wire. The typed POJO is produced by the
        // ByteArrayJacksonJsonMessageConverter (see infraKafkaMessageConverter) attached to the listener
        // container factory, which infers the target type from the @KafkaListener /
        // @InfraKafkaListener method signature. This keeps payloads free of Spring type headers
        // ("clean, cross-service payloads") while still deserializing to the declared listener type.
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

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
     * JSON {@link RecordMessageConverter} shared by both listener container factories.
     *
     * <p>The value deserializer emits raw {@code byte[]} (see {@link #consumerConfigs()}); this
     * converter turns those bytes into the payload type <em>declared on the listener method</em>.
     * Type resolution uses {@link JacksonJavaTypeMapper.TypePrecedence#INFERRED}, so the target
     * type comes from the method signature and producers need not stamp Spring type headers onto
     * messages — the key to clean, cross-service JSON payloads.
     *
     * <p>{@code infra.kafka.consumer.trusted-packages} is applied to the type mapper as
     * defence-in-depth for the header-fallback path. A value of {@code "*"} trusts every package
     * and is logged as a security warning (dev-only); blank (the secure default) trusts nothing.
     */
    @Bean
    @ConditionalOnMissingBean(RecordMessageConverter.class)
    public RecordMessageConverter infraKafkaMessageConverter() {
        ByteArrayJacksonJsonMessageConverter converter = new ByteArrayJacksonJsonMessageConverter();

        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);

        String trustedPackages = properties.getConsumer().getTrustedPackages();
        if (trustedPackages != null && "*".equals(trustedPackages.trim())) {
            log.warn("[infra-kafka] SECURITY: infra.kafka.consumer.trusted-packages=\"*\" trusts EVERY "
                    + "package for JSON type resolution, which is a deserialization-attack vector. Set it "
                    + "to your event-model package(s) (e.g. com.acme.orders.events) in production.");
        }
        if (trustedPackages != null && !trustedPackages.isBlank()) {
            typeMapper.addTrustedPackages(
                    Arrays.stream(trustedPackages.split(","))
                            .map(String::trim)
                            .toArray(String[]::new));
        }
        converter.setTypeMapper(typeMapper);

        log.info("[infra-kafka] ByteArrayJacksonJsonMessageConverter created — payload type inferred from "
                + "the listener method signature (no wire type headers required)");
        return converter;
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
            RecordMessageConverter messageConverter,
            @Qualifier("infraKafkaErrorHandler")
            ObjectProvider<DefaultKafkaErrorHandler> errorHandler,
            @Qualifier("infraKafkaConsumerInterceptor")
            ObjectProvider<RecordInterceptor<String, Object>> recordInterceptor) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                buildBaseFactory(consumerFactory, messageConverter, recordInterceptor);

        // Phase 3: retry-with-backoff + DLQ handler (the default listener behaviour).
        // Skipped in NON_BLOCKING mode — there, the retry-topic infrastructure installs its
        // own forwarding error handler, and setting a blocking handler here would conflict.
        if (isBlockingRetry()) {
            errorHandler.ifAvailable(handler -> {
                factory.setCommonErrorHandler(handler);
                log.info("[infra-kafka] Retry+DLQ error handler wired on infraKafkaListenerContainerFactory");
            });
        } else {
            log.info("[infra-kafka] NON_BLOCKING retry mode — deferring error handling to retry topics "
                    + "on infraKafkaListenerContainerFactory");
        }

        log.info("[infra-kafka] infraKafkaListenerContainerFactory initialized — concurrency={}",
                properties.getConsumer().getConcurrency());

        return factory;
    }

    /**
     * Creates the <b>no-retry</b> {@link ConcurrentKafkaListenerContainerFactory}.
     *
     * <p>Backs listeners that opt in via
     * {@link org.infra.kafka.consumer.InfraKafkaListener#NON_RETRYING_CONTAINER_FACTORY}:
     * a failing record is routed straight to the DLQ on the first failure, with no retries.
     * Shares every other default (ack-mode, concurrency, MDC/metrics interceptor) with the
     * retrying factory.
     *
     * @param consumerFactory    the consumer factory (always present)
     * @param noRetryErrorHandler the {@code infraKafkaNoRetryErrorHandler} bean (immediate DLQ)
     * @param recordInterceptor  optional Phase 4 MDC + metrics interceptor
     */
    @Bean("infraKafkaNoRetryListenerContainerFactory")
    @ConditionalOnMissingBean(name = "infraKafkaNoRetryListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> infraKafkaNoRetryListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            RecordMessageConverter messageConverter,
            @Qualifier("infraKafkaNoRetryErrorHandler")
            ObjectProvider<DefaultKafkaErrorHandler> noRetryErrorHandler,
            @Qualifier("infraKafkaConsumerInterceptor")
            ObjectProvider<RecordInterceptor<String, Object>> recordInterceptor) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                buildBaseFactory(consumerFactory, messageConverter, recordInterceptor);

        // Per-listener no-retry opt-out is a BLOCKING-mode feature; in NON_BLOCKING mode the
        // global retry-topic configuration governs every listener.
        if (isBlockingRetry()) {
            noRetryErrorHandler.ifAvailable(handler -> {
                factory.setCommonErrorHandler(handler);
                log.info("[infra-kafka] No-retry (immediate-DLQ) error handler wired on "
                        + "infraKafkaNoRetryListenerContainerFactory");
            });
        }

        log.info("[infra-kafka] infraKafkaNoRetryListenerContainerFactory initialized — concurrency={}",
                properties.getConsumer().getConcurrency());

        return factory;
    }

    /**
     * Builds a listener container factory with all defaults shared by both the retrying and
     * non-retrying variants — consumer factory, concurrency, {@code AckMode.RECORD}, graceful
     * shutdown timeout, and the optional MDC/metrics {@link RecordInterceptor}. The caller
     * attaches the appropriate {@code CommonErrorHandler}.
     */
    /**
     * @return {@code true} when retry is in {@code BLOCKING} mode (container-level in-memory
     *         backoff). {@code false} in {@code NON_BLOCKING} mode, where retry topics handle
     *         failures and no blocking error handler should be attached to the factory.
     */
    private boolean isBlockingRetry() {
        return properties.getRetry().getMode()
                == InfraKafkaProperties.RetryProperties.RetryMode.BLOCKING;
    }

    private ConcurrentKafkaListenerContainerFactory<String, Object> buildBaseFactory(
            ConsumerFactory<String, Object> consumerFactory,
            RecordMessageConverter messageConverter,
            ObjectProvider<RecordInterceptor<String, Object>> recordInterceptor) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConsumer().getConcurrency());

        // Convert the raw byte[] value into the listener method's declared payload type,
        // inferring the type from the method signature (no wire type headers needed).
        factory.setRecordMessageConverter(messageConverter);

        // RECORD ack-mode: commit offset after each record (safe default)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Phase 8: Graceful shutdown max timeout waiting for in-flight records bounds
        if (properties.getShutdown().getTimeout() != null) {
            factory.getContainerProperties().setShutdownTimeout(properties.getShutdown().getTimeout().toMillis());
        }

        // Phase 4: wire the MDC + metrics record interceptor when present
        recordInterceptor.ifAvailable(interceptor -> {
            factory.setRecordInterceptor(interceptor);
            log.info("[infra-kafka] Consumer RecordInterceptor wired — MDC + metrics active");
        });

        return factory;
    }
}

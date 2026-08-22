package org.infra.kafka.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.observability.KafkaLoggingInterceptor;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring {@link Configuration} that creates and exposes the Kafka producer infrastructure.
 *
 * <h3>What this class wires</h3>
 * <ol>
 *   <li>{@link ProducerFactory} — thread-safe factory that manages producer lifecycle</li>
 *   <li>{@link KafkaTemplate} — high-level send API backed by the producer factory</li>
 *   <li>{@link KafkaMessagePublisher} — library's public producer service</li>
 * </ol>
 *
 * <h3>Idempotent producer</h3>
 * When {@code infra.kafka.producer.idempotent=true} (the default), the following
 * properties are enforced:
 * <ul>
 *   <li>{@code enable.idempotence = true}</li>
 *   <li>{@code acks = all} (overrides any weaker setting)</li>
 *   <li>{@code retries ≥ 1}</li>
 *   <li>{@code max.in.flight.requests.per.connection ≤ 5}</li>
 * </ul>
 *
 * <h3>Phase 4 — Observability</h3>
 * When {@link KafkaLoggingInterceptor} is present in the context (created by
 * {@link org.infra.kafka.observability.ObservabilityConfig}), it is registered as
 * a Kafka producer interceptor via {@code interceptor.classes}. This enables
 * pre-send / post-ack structured logging and MDC propagation for every produced message.
 *
 * <p>All beans are guarded with {@link ConditionalOnMissingBean} so consumer
 * applications can override any bean as needed.
 */
@Slf4j
@Configuration
public class KafkaProducerConfig {

    private final InfraKafkaProperties properties;
    private final KafkaSecurityConfig securityConfig;

    public KafkaProducerConfig(InfraKafkaProperties properties, KafkaSecurityConfig securityConfig) {
        this.properties = properties;
        this.securityConfig = securityConfig;
    }

    /**
     * Builds the Kafka producer configuration map from {@link InfraKafkaProperties}.
     *
     * <p>Idempotency constraints are validated and enforced here. A warning is
     * logged if the caller has misconfigured properties that conflict with
     * idempotent mode.
     *
     * @param loggingInterceptor optional Phase 4 logging interceptor; absent when logging is disabled
     */
    public Map<String, Object> producerConfigs(
            ObjectProvider<KafkaLoggingInterceptor> loggingInterceptor) {
        InfraKafkaProperties.ProducerProperties producer = properties.getProducer();
        Map<String, Object> config = new HashMap<>();

        // ── Core connectivity ────────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());

        // ── Serializers ──────────────────────────────────────────────────────
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producer.getKeySerializer());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producer.getValueSerializer());

        // ── Reliability / delivery guarantee ─────────────────────────────────
        String acks = producer.getAcks();
        int retries = producer.getRetries();
        int maxInFlight = producer.getMaxInFlightRequestsPerConnection();

        if (producer.isIdempotent()) {
            // Idempotent mode requires acks=all, retries>0, maxInFlight<=5
            if (!"all".equals(acks) && !"-1".equals(acks)) {
                log.warn("[infra-kafka] Idempotent producer requires acks=all; overriding provided acks={}", acks);
                acks = "all";
            }
            if (retries < 1) {
                log.warn("[infra-kafka] Idempotent producer requires retries>=1; overriding to retries=3");
                retries = 3;
            }
            if (maxInFlight > 5) {
                log.warn("[infra-kafka] Idempotent producer requires max.in.flight<=5; overriding to 5");
                maxInFlight = 5;
            }
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            log.info("[infra-kafka] Idempotent producer ENABLED (acks=all, retries={}, maxInFlight={})",
                    retries, maxInFlight);
        } else {
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
            log.info("[infra-kafka] Idempotent producer DISABLED");
        }

        config.put(ProducerConfig.ACKS_CONFIG, acks);
        config.put(ProducerConfig.RETRIES_CONFIG, retries);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlight);

        // ── Batching & buffering ─────────────────────────────────────────────
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        config.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producer.getBufferMemory());

        // ── Compression ──────────────────────────────────────────────────────
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.getCompressionType());

        // ── Timeouts ─────────────────────────────────────────────────────────
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producer.getRequestTimeoutMs());
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producer.getDeliveryTimeoutMs());

        // ── JsonSerializer: do NOT add type headers (keeps payload clean) ────
        config.put("spring.json.add.type.headers", false);

        // ── Phase 4: register logging interceptor when present ───────────────
        loggingInterceptor.ifAvailable(interceptor -> {
            config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                    KafkaLoggingInterceptor.class.getName());
            // Pass log-payload flag via producer config map so interceptor.configure() picks it up
            config.put("infra.kafka.logging.log-payload",
                    properties.getLogging().isLogPayload());
            log.info("[infra-kafka] KafkaLoggingInterceptor registered on producer");
        });

        // ── Phase 6: Apply security settings ─────────────────────────────────
        securityConfig.applySecurity(config);

        return config;
    }

    /**
     * Creates the {@link ProducerFactory} used to instantiate Kafka producers.
     *
     * <p>Uses {@link DefaultKafkaProducerFactory} which caches a single producer
     * instance per factory — thread-safe for concurrent use.
     *
     * @param loggingInterceptor optional Phase 4 logging interceptor
     */
    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, Object> infraKafkaProducerFactory(
            ObjectProvider<KafkaLoggingInterceptor> loggingInterceptor) {
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(producerConfigs(loggingInterceptor));

        if (properties.getTransaction().isEnabled()) {
            factory.setTransactionIdPrefix(properties.getTransaction().getIdPrefix());
            log.info("[infra-kafka] Transaction mode ENABLED. ProducerFactory initialized with prefix: {}",
                    properties.getTransaction().getIdPrefix());
        }

        log.info("[infra-kafka] ProducerFactory initialized with bootstrap-servers={}",
                properties.getBootstrapServers());
        return factory;
    }

    /**
     * Creates the {@link KafkaTemplate} bean used internally and exposed for
     * direct use in consumer applications.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, Object> infraKafkaTemplate(
            ProducerFactory<String, Object> producerFactory) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory);
        log.info("[infra-kafka] KafkaTemplate initialized");
        return template;
    }

    /**
     * Dead-letter {@link KafkaTemplate} used by the error handlers to republish failed records to
     * the DLQ.
     *
     * <p>Because the consumer reads values as raw {@code byte[]} (see
     * {@code KafkaConsumerConfig}), a failed record's value reaching the
     * {@code DeadLetterPublishingRecoverer} is a {@code byte[]}. Publishing it through the primary
     * template (whose value serializer is {@code JsonSerializer}) would JSON-encode the bytes a
     * second time, corrupting the DLQ payload. This template uses a
     * {@link ByteArraySerializer} for values (and {@link StringSerializer} for keys) so the
     * original JSON bytes are forwarded to the DLQ unchanged.
     */
    @Bean("infraKafkaDltTemplate")
    @ConditionalOnMissingBean(name = "infraKafkaDltTemplate")
    public KafkaTemplate<Object, Object> infraKafkaDltTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        securityConfig.applySecurity(config);

        log.info("[infra-kafka] Dead-letter KafkaTemplate initialized (byte[] passthrough to DLQ)");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    /**
     * Creates the primary {@link KafkaMessagePublisher} bean.
     *
     * <p>Consumer projects inject this bean to publish messages:
     * <pre>{@code
     * @Autowired
     * private KafkaMessagePublisher publisher;
     * }</pre>
     */
    @Bean
    @ConditionalOnMissingBean(KafkaMessagePublisher.class)
    public KafkaMessagePublisher kafkaMessagePublisher(
            KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("[infra-kafka] KafkaMessagePublisher initialized");
        return new KafkaMessagePublisher(kafkaTemplate, properties);
    }
}

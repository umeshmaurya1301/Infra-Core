package org.infra.kafka.serialization;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 5 — Avro Serialization configuration (optional, opt-in).
 *
 * <h3>Activation</h3>
 * <p>This configuration is active only when:
 * <ol>
 *   <li>{@code infra.kafka.serialization.type=avro} is set in {@code application.yml}</li>
 *   <li>{@code io.confluent.kafka.serializers.KafkaAvroSerializer} is on the classpath
 *       (i.e., the consumer project has added {@code kafka-avro-serializer} as a dependency)</li>
 * </ol>
 *
 * <h3>What this class wires</h3>
 * <ul>
 *   <li>{@link ProducerFactory} (Avro) — overrides the JSON producer factory from Phase 1
 *       with Avro-aware {@link KafkaAvroSerializer}</li>
 *   <li>{@link ConsumerFactory} (Avro) — overrides the JSON consumer factory from Phase 2
 *       with Avro-aware {@link KafkaAvroDeserializer}</li>
 * </ul>
 *
 * <h3>Schema Registry</h3>
 * <p>Both serializer and deserializer are pointed at the Schema Registry URL configured
 * via {@code infra.kafka.serialization.schema-registry-url}.
 * Optional Basic Auth is supported via {@code infra.kafka.serialization.schema-registry-auth}.
 *
 * <h3>Subject Naming Strategy</h3>
 * <p>Defaults to {@code TopicNameStrategy} (one schema per topic).
 * Override via {@code infra.kafka.serialization.subject-naming-strategy}.
 *
 * <h3>Adding the dependency</h3>
 * <pre>
 * // build.gradle.kts (consumer project)
 * implementation("io.confluent:kafka-avro-serializer:7.6.0")
 * </pre>
 *
 * @see InfraKafkaProperties.SerializationProperties
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "infra.kafka.serialization",
        name = "type",
        havingValue = "avro"
)
@ConditionalOnClass(KafkaAvroSerializer.class)
public class AvroSerializerConfig {

    private final InfraKafkaProperties properties;
    private final KafkaSecurityConfig securityConfig;

    public AvroSerializerConfig(InfraKafkaProperties properties, KafkaSecurityConfig securityConfig) {
        this.properties = properties;
        this.securityConfig = securityConfig;
        log.info("[infra-kafka] Avro serialization mode ACTIVE — Schema Registry: {}",
                properties.getSerialization().getSchemaRegistryUrl());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Avro Producer Factory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the Avro producer configuration map.
     *
     * <p>Key settings applied:
     * <ul>
     *   <li>Key serializer: {@link StringSerializer} (keys are always strings)</li>
     *   <li>Value serializer: {@link KafkaAvroSerializer} (Confluent Avro)</li>
     *   <li>{@code schema.registry.url}: from {@code infra.kafka.serialization.schema-registry-url}</li>
     *   <li>{@code auto.register.schemas}: defaults to {@code false} for production safety</li>
     *   <li>{@code use.latest.version}: {@code true} — use latest schema version</li>
     * </ul>
     */
    public Map<String, Object> avroProducerConfigs() {
        InfraKafkaProperties.ProducerProperties producer = properties.getProducer();
        InfraKafkaProperties.SerializationProperties serialization = properties.getSerialization();

        Map<String, Object> config = new HashMap<>();

        // ── Core connectivity ────────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());

        // ── Avro Serializers ─────────────────────────────────────────────────
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

        // ── Schema Registry ──────────────────────────────────────────────────
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                serialization.getSchemaRegistryUrl());
        config.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS,
                serialization.isAutoRegisterSchemas());
        config.put(AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION,
                serialization.isUseLatestVersion());

        // ── Subject Naming Strategy ───────────────────────────────────────────
        if (serialization.getSubjectNamingStrategy() != null
                && !serialization.getSubjectNamingStrategy().isBlank()) {
            config.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                    serialization.getSubjectNamingStrategy());
        }

        // ── Basic Auth for Schema Registry ───────────────────────────────────
        applySchemaRegistryAuth(config, serialization);

        // ── Reliability / idempotency ────────────────────────────────────────
        if (producer.isIdempotent()) {
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.RETRIES_CONFIG, Math.max(producer.getRetries(), 1));
            config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                    Math.min(producer.getMaxInFlightRequestsPerConnection(), 5));
        } else {
            config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
            config.put(ProducerConfig.ACKS_CONFIG, producer.getAcks());
            config.put(ProducerConfig.RETRIES_CONFIG, producer.getRetries());
            config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                    producer.getMaxInFlightRequestsPerConnection());
        }

        // ── Batching & buffering ──────────────────────────────────────────────
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, producer.getBatchSize());
        config.put(ProducerConfig.LINGER_MS_CONFIG, producer.getLingerMs());
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producer.getBufferMemory());
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producer.getCompressionType());

        // ── Timeouts ──────────────────────────────────────────────────────────
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, producer.getRequestTimeoutMs());
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, producer.getDeliveryTimeoutMs());

        // ── Phase 6: Apply security settings ─────────────────────────────────
        securityConfig.applySecurity(config);

        return config;
    }

    /**
     * Avro-aware {@link ProducerFactory} that overrides the JSON producer factory from Phase 1.
     *
     * <p>Marked {@link Primary} so Spring injects this factory when Avro is active,
     * without requiring consumer projects to change any code.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "avroProducerFactory")
    public ProducerFactory<String, Object> avroProducerFactory() {
        log.info("[infra-kafka] Avro ProducerFactory created — schema-registry={}",
                properties.getSerialization().getSchemaRegistryUrl());
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(avroProducerConfigs());

        if (properties.getTransaction().isEnabled()) {
            factory.setTransactionIdPrefix(properties.getTransaction().getIdPrefix());
            log.info("[infra-kafka] Transaction mode ENABLED. Avro ProducerFactory initialized with prefix: {}",
                    properties.getTransaction().getIdPrefix());
        }

        return factory;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Avro Consumer Factory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the Avro consumer configuration map.
     *
     * <p>Key settings applied:
     * <ul>
     *   <li>Key deserializer: {@link StringDeserializer}</li>
     *   <li>Value deserializer: {@link KafkaAvroDeserializer} (Confluent Avro)</li>
     *   <li>{@code specific.avro.reader}: {@code true} — deserializes into generated Avro POJOs</li>
     *   <li>{@code schema.registry.url}: from {@code infra.kafka.serialization.schema-registry-url}</li>
     * </ul>
     */
    public Map<String, Object> avroConsumerConfigs() {
        InfraKafkaProperties.ConsumerProperties consumer = properties.getConsumer();
        InfraKafkaProperties.SerializationProperties serialization = properties.getSerialization();

        Map<String, Object> config = new HashMap<>();

        // ── Core connectivity ────────────────────────────────────────────────
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, consumer.getGroupId());

        // ── Offset management ────────────────────────────────────────────────
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumer.getAutoOffsetReset());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumer.isEnableAutoCommit());
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumer.getMaxPollRecords());

        // ── Phase 7: Transactions / EOS ──────────────────────────────────────
        if (properties.getTransaction().isEnabled()) {
            config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            log.info("[infra-kafka] Transaction mode ENABLED. Avro Consumer isolation.level set to read_committed.");
        }

        // ── Partition assignment ─────────────────────────────────────────────
        config.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                consumer.getPartitionAssignmentStrategy());

        // ── Avro Deserializers ───────────────────────────────────────────────
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

        // ── Schema Registry ──────────────────────────────────────────────────
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                serialization.getSchemaRegistryUrl());
        config.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                serialization.isSpecificAvroReader());
        config.put(AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION,
                serialization.isUseLatestVersion());

        // ── Subject Naming Strategy ───────────────────────────────────────────
        if (serialization.getSubjectNamingStrategy() != null
                && !serialization.getSubjectNamingStrategy().isBlank()) {
            config.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                    serialization.getSubjectNamingStrategy());
        }

        // ── Basic Auth for Schema Registry ───────────────────────────────────
        applySchemaRegistryAuth(config, serialization);

        log.info("[infra-kafka] Avro ConsumerFactory config built — groupId={}, schema-registry={}",
                consumer.getGroupId(), serialization.getSchemaRegistryUrl());

        // ── Phase 6: Apply security settings ─────────────────────────────────
        securityConfig.applySecurity(config);

        return config;
    }

    /**
     * Avro-aware {@link ConsumerFactory} that overrides the JSON consumer factory from Phase 2.
     *
     * <p>Marked {@link Primary} so Spring injects this factory when Avro is active.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "avroConsumerFactory")
    public ConsumerFactory<String, Object> avroConsumerFactory() {
        log.info("[infra-kafka] Avro ConsumerFactory created — schema-registry={}",
                properties.getSerialization().getSchemaRegistryUrl());
        return new DefaultKafkaConsumerFactory<>(avroConsumerConfigs());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies Schema Registry Basic Auth credentials to the config map when
     * {@code infra.kafka.serialization.schema-registry-auth} is configured.
     *
     * <p>Uses Confluent's {@code USER_INFO} source to embed {@code username:password}
     * in the client credentials:
     * <pre>
     * infra:
     *   kafka:
     *     serialization:
     *       schema-registry-auth: "my-user:my-password"
     * </pre>
     */
    private void applySchemaRegistryAuth(
            Map<String, Object> config,
            InfraKafkaProperties.SerializationProperties serialization) {

        String auth = serialization.getSchemaRegistryAuth();
        if (auth != null && !auth.isBlank()) {
            config.put(AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO");
            config.put(AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG, auth);
            log.info("[infra-kafka] Schema Registry Basic Auth configured");
        }
    }
}

package org.infra.kafka.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.List;

/**
 * Centralized configuration properties for the infra-kafka library.
 *
 * <p>Bind via {@code application.yml}:
 * <pre>
 * infra:
 *   kafka:
 *     bootstrap-servers: localhost:9092
 *     producer:
 *       acks: all
 *       idempotent: true
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "infra.kafka")
public class InfraKafkaProperties {

    /**
     * Comma-separated list of Kafka broker addresses.
     * Example: {@code localhost:9092,localhost:9093}
     */
    private String bootstrapServers = "localhost:9092";

    // ─────────────────────────────────────────────────────────────────────────
    // Nested property groups (populated by Spring Boot property binding)
    // ─────────────────────────────────────────────────────────────────────────

    @NestedConfigurationProperty
    private ProducerProperties producer = new ProducerProperties();

    @NestedConfigurationProperty
    private ConsumerProperties consumer = new ConsumerProperties();

    @NestedConfigurationProperty
    private RetryProperties retry = new RetryProperties();

    @NestedConfigurationProperty
    private DlqProperties dlq = new DlqProperties();

    @NestedConfigurationProperty
    private SerializationProperties serialization = new SerializationProperties();

    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();

    @NestedConfigurationProperty
    private TransactionProperties transaction = new TransactionProperties();

    @NestedConfigurationProperty
    private AdminProperties admin = new AdminProperties();

    @NestedConfigurationProperty
    private LoggingProperties logging = new LoggingProperties();

    @NestedConfigurationProperty
    private MetricsProperties metrics = new MetricsProperties();

    @NestedConfigurationProperty
    private ShutdownProperties shutdown = new ShutdownProperties();

    @NestedConfigurationProperty
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    // ─────────────────────────────────────────────────────────────────────────
    // Inner property classes
    // ─────────────────────────────────────────────────────────────────────────

    /** Producer-specific settings. */
    @Data
    public static class ProducerProperties {
        /**
         * Acknowledgement mode: {@code 0}, {@code 1}, or {@code all}.
         * Default is {@code all} for maximum durability.
         */
        private String acks = "all";

        /**
         * Number of automatic retries the Kafka producer client will make
         * on transient failures.
         */
        private int retries = 3;

        /** Batch accumulation size in bytes. Default 16 KB. */
        private int batchSize = 16384;

        /**
         * Time in milliseconds to wait before sending a batch.
         * Increases throughput at the cost of latency.
         */
        private int lingerMs = 5;

        /** Total memory available to the producer for buffering. Default 32 MB. */
        private long bufferMemory = 33554432L;

        /**
         * Compression codec: {@code none}, {@code gzip}, {@code snappy},
         * {@code lz4}, or {@code zstd}.
         */
        private String compressionType = "snappy";

        /**
         * When {@code true}, enables idempotent producer mode:
         * {@code enable.idempotence=true}, {@code acks=all}, {@code retries>0}.
         * Prevents duplicate messages caused by producer retries.
         */
        private boolean idempotent = true;

        /** Fully-qualified key serializer class name. */
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";

        /** Fully-qualified value serializer class name. */
        private String valueSerializer = "org.springframework.kafka.support.serializer.JsonSerializer";

        /** Maximum in-flight requests per connection. */
        private int maxInFlightRequestsPerConnection = 5;

        /** Request timeout in milliseconds. */
        private int requestTimeoutMs = 30000;

        /** Delivery timeout in milliseconds. */
        private int deliveryTimeoutMs = 120000;
    }

    /** Consumer-specific settings. */
    @Data
    public static class ConsumerProperties {
        /** Consumer group ID. Defaults to Spring application name. */
        private String groupId = "${spring.application.name:default-group}";

        /** Offset reset strategy: {@code earliest}, {@code latest}, or {@code none}. */
        private String autoOffsetReset = "earliest";

        /** When {@code false}, offsets are committed manually by the library (recommended). */
        private boolean enableAutoCommit = false;

        /** Maximum records returned in a single poll. */
        private int maxPollRecords = 500;

        /** Number of concurrent listener threads. Should not exceed partition count. */
        private int concurrency = 3;

        /** Fully-qualified key deserializer class name. */
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

        /** Fully-qualified value deserializer class name. */
        private String valueDeserializer = "org.springframework.kafka.support.serializer.JsonDeserializer";

        /**
         * Comma-separated list of trusted packages for JsonDeserializer.
         * Use {@code "*"} only in development environments.
         */
        private String trustedPackages = "*";

        /** Partition assignment strategy class name. */
        private String partitionAssignmentStrategy =
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor";
    }

    /** Non-blocking retry settings. */
    @Data
    public static class RetryProperties {
        /** Enable or disable retry altogether. */
        private boolean enabled = true;

        /** Total attempts including the first (e.g., 3 = 1 original + 2 retries). */
        private int maxAttempts = 3;

        /** Initial backoff delay in milliseconds. */
        private long backoffInitialInterval = 1000L;

        /** Multiplier applied to the backoff delay after each retry. */
        private double backoffMultiplier = 2.0;

        /** Maximum backoff delay cap in milliseconds. */
        private long backoffMaxInterval = 10000L;
    }

    /** Dead-letter queue settings. */
    @Data
    public static class DlqProperties {
        /** Enable dead-letter queue routing after exhausted retries. */
        private boolean enabled = true;

        /** Suffix appended to the original topic name to form the DLQ topic. */
        private String suffix = "-dlq";
    }

    /** Serialization settings. */
    @Data
    public static class SerializationProperties {
        /**
         * Serialization format: {@code json} (default) or {@code avro}.
         * Avro requires a running Schema Registry (see {@link #schemaRegistryUrl}).
         */
        private String type = "json";

        /**
         * Confluent Schema Registry URL.
         * Required when {@code type=avro}.
         * Example: {@code http://localhost:8081} or {@code https://registry.example.com}
         */
        private String schemaRegistryUrl = "http://localhost:8081";

        /**
         * When {@code true}, the Avro serializer automatically registers new schemas
         * with the Schema Registry. Set to {@code false} in production to avoid
         * accidental schema mutations — schemas should be registered via CI/CD.
         */
        private boolean autoRegisterSchemas = false;

        /**
         * When {@code true}, the serializer/deserializer uses the latest schema version
         * registered under the subject in Schema Registry instead of the writer schema
         * embedded in the message.
         */
        private boolean useLatestVersion = true;

        /**
         * When {@code true}, the {@link io.confluent.kafka.serializers.KafkaAvroDeserializer}
         * deserializes messages into generated Avro POJO classes (specific readers) rather
         * than generic {@link org.apache.avro.generic.GenericRecord} objects.
         * Set to {@code false} when consuming messages without generated classes.
         */
        private boolean specificAvroReader = true;

        /**
         * Fully-qualified class name of the Confluent Subject Naming Strategy.
         * Defaults to {@code TopicNameStrategy} (one schema per topic value).
         * Other options:
         * <ul>
         *   <li>{@code io.confluent.kafka.serializers.subject.TopicNameStrategy} (default)</li>
         *   <li>{@code io.confluent.kafka.serializers.subject.RecordNameStrategy}</li>
         *   <li>{@code io.confluent.kafka.serializers.subject.TopicRecordNameStrategy}</li>
         * </ul>
         */
        private String subjectNamingStrategy = "";

        /**
         * Basic Auth credentials for Schema Registry in the form {@code username:password}.
         * Leave blank if Schema Registry does not require authentication.
         * <strong>Never hardcode credentials</strong> — use environment variables:
         * {@code schema-registry-auth: "${SR_USER}:${SR_PASS}"}
         */
        private String schemaRegistryAuth = "";

        /**
         * HTTP connection timeout (ms) used by the Schema Registry health indicator.
         * Default: 3000 ms.
         */
        private int connectionTimeoutMs = 3000;

        /**
         * HTTP read timeout (ms) used by the Schema Registry health indicator.
         * Default: 3000 ms.
         */
        private int readTimeoutMs = 3000;
    }

    /** Security settings (SSL / SASL). */
    @Data
    public static class SecurityProperties {
        /**
         * Kafka security protocol.
         * One of: {@code PLAINTEXT}, {@code SSL}, {@code SASL_PLAINTEXT}, {@code SASL_SSL}.
         */
        private String protocol = "PLAINTEXT";

        /** SASL configuration. */
        private SaslProperties sasl = new SaslProperties();

        /** SSL/TLS configuration. */
        private SslProperties ssl = new SslProperties();

        @Data
        public static class SaslProperties {
            /** SASL mechanism: {@code PLAIN}, {@code SCRAM-SHA-256}, or {@code SCRAM-SHA-512}. */
            private String mechanism = "PLAIN";
            private String username = "";
            private String password = "";
        }

        @Data
        public static class SslProperties {
            private String truststoreLocation = "";
            private String truststorePassword = "";
            private String keystoreLocation = "";
            private String keystorePassword = "";
            private String keyPassword = "";
        }
    }

    /** Kafka Transactions (EOS) settings. */
    @Data
    public static class TransactionProperties {
        /** Enable transactional producer. Off by default to avoid performance overhead. */
        private boolean enabled = false;

        /** Prefix used to generate a unique {@code transactional.id} per producer instance. */
        private String idPrefix = "txn-infra-";
    }

    /** Kafka Admin / topic management settings. */
    @Data
    public static class AdminProperties {
        /**
         * Automatically create topics declared under {@link #topics} on startup.
         * <strong>Keep {@code false} in production</strong>; manage topics via IaC.
         */
        private boolean autoCreate = false;

        /** Topic definitions to auto-create when {@link #autoCreate} is {@code true}. */
        private List<TopicDefinition> topics = List.of();

        @Data
        public static class TopicDefinition {
            private String name;
            private int partitions = 1;
            private short replicationFactor = 1;
        }
    }

    /** Structured logging settings. */
    @Data
    public static class LoggingProperties {
        /** Enable library-level structured logging per message. */
        private boolean enabled = true;

        /**
         * When {@code true}, the message payload is included in log entries.
         * Only enable in non-production environments.
         */
        private boolean logPayload = false;
    }

    /** Micrometer metrics settings. */
    @Data
    public static class MetricsProperties {
        /** Enable Micrometer metric collection. */
        private boolean enabled = true;
    }

    /** Graceful shutdown settings. */
    @Data
    public static class ShutdownProperties {
        /**
         * Maximum time (as a Duration string, e.g. {@code 30s}) to wait for
         * in-flight messages to drain before forceful shutdown.
         */
        private Duration timeout = Duration.ofSeconds(30);
    }

    /** Circuit breaker settings. */
    @Data
    public static class CircuitBreakerProperties {
        /**
         * Enable Resilience4j circuit breaking on consumer downstream failures.
         * Default is false to avoid pausing consumption unexpectedly.
         */
        private boolean enabled = false;
    }
}

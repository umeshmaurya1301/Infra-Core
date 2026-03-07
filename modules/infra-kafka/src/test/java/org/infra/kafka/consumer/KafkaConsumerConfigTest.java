package org.infra.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.error.DefaultKafkaErrorHandler;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.infra.kafka.security.SaslConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaConsumerConfig Unit Tests")
class KafkaConsumerConfigTest {

    private KafkaConsumerConfig buildConfig(InfraKafkaProperties props) {
        KafkaSecurityConfig securityConfig = new KafkaSecurityConfig(props, new SaslConfigProvider());
        return new KafkaConsumerConfig(props, securityConfig);
    }

    private InfraKafkaProperties defaultProperties() {
        return new InfraKafkaProperties();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // consumerConfigs() — core settings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consumerConfigs() sets bootstrap servers from properties")
    void consumerConfigs_setsBootstrapServers() {
        InfraKafkaProperties props = defaultProperties();
        props.setBootstrapServers("broker1:9092,broker2:9092");

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs)
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
    }

    @Test
    @DisplayName("consumerConfigs() sets group-id from properties")
    void consumerConfigs_setsGroupId() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setGroupId("my-service");

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "my-service");
    }

    @Test
    @DisplayName("consumerConfigs() sets enable.auto.commit=false by default")
    void consumerConfigs_autoCommitDisabled_byDefault() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    }

    @Test
    @DisplayName("consumerConfigs() can enable auto-commit when configured")
    void consumerConfigs_autoCommitEnabled_whenConfigured() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setEnableAutoCommit(true);

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
    }

    @Test
    @DisplayName("consumerConfigs() sets auto-offset-reset to earliest by default")
    void consumerConfigs_autoOffsetReset_earliest_byDefault() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    }

    @Test
    @DisplayName("consumerConfigs() respects custom auto-offset-reset value")
    void consumerConfigs_respectsCustomAutoOffsetReset() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setAutoOffsetReset("latest");

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    }

    @Test
    @DisplayName("consumerConfigs() sets max-poll-records from properties")
    void consumerConfigs_setsMaxPollRecords() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setMaxPollRecords(250);

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 250);
    }

    @Test
    @DisplayName("consumerConfigs() sets partition-assignment-strategy from properties")
    void consumerConfigs_setsPartitionAssignmentStrategy() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs)
                .containsEntry(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                        "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // consumerConfigs() — deserializer wrapping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consumerConfigs() wraps key deserializer in ErrorHandlingDeserializer")
    void consumerConfigs_wrapsKeyDeserializerInErrorHandling() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs)
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        ErrorHandlingDeserializer.class);
    }

    @Test
    @DisplayName("consumerConfigs() wraps value deserializer in ErrorHandlingDeserializer")
    void consumerConfigs_wrapsValueDeserializerInErrorHandling() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        ErrorHandlingDeserializer.class);
    }

    @Test
    @DisplayName("consumerConfigs() sets trusted packages for JsonDeserializer")
    void consumerConfigs_setsTrustedPackages() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setTrustedPackages("com.example.*");

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs)
                .containsEntry(JsonDeserializer.TRUSTED_PACKAGES, "com.example.*");
    }

    @Test
    @DisplayName("consumerConfigs() disables type header usage in JsonDeserializer")
    void consumerConfigs_disablesTypeInfoHeaders() {
        Map<String, Object> configs = buildConfig(defaultProperties()).consumerConfigs();

        assertThat(configs)
                .containsEntry(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 7: Transactions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("consumerConfigs() sets isolation.level to read_committed when transaction enabled")
    void consumerConfigs_setsIsolationLevel_whenTransactionEnabled() {
        InfraKafkaProperties props = defaultProperties();
        props.getTransaction().setEnabled(true);

        Map<String, Object> configs = buildConfig(props).consumerConfigs();

        assertThat(configs).containsEntry(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 8: Hardening & Shutdown
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraKafkaListenerContainerFactory() maps shutdown timeout from properties correctly")
    void infraKafkaListenerContainerFactory_mapsShutdownTimeout() {
        InfraKafkaProperties props = defaultProperties();
        props.getShutdown().setTimeout(Duration.ofSeconds(45));

        KafkaConsumerConfig config = buildConfig(props);
        ConsumerFactory<String, Object> consumerFactory = config.infraKafkaConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                config.infraKafkaListenerContainerFactory(
                        consumerFactory, emptyErrorHandlerProvider(), emptyRecordInterceptorProvider());

        assertThat(listenerFactory.getContainerProperties().getShutdownTimeout())
                .isEqualTo(45000L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bean creation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraKafkaConsumerFactory() creates a non-null ConsumerFactory")
    void infraKafkaConsumerFactory_createsBean() {
        KafkaConsumerConfig config = buildConfig(defaultProperties());
        ConsumerFactory<String, Object> factory = config.infraKafkaConsumerFactory();

        assertThat(factory).isNotNull();
    }

    @Test
    @DisplayName("infraKafkaListenerContainerFactory() creates a non-null factory")
    void infraKafkaListenerContainerFactory_createsBean() {
        KafkaConsumerConfig config = buildConfig(defaultProperties());
        ConsumerFactory<String, Object> consumerFactory = config.infraKafkaConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                config.infraKafkaListenerContainerFactory(
                        consumerFactory, emptyErrorHandlerProvider(), emptyRecordInterceptorProvider());

        assertThat(listenerFactory).isNotNull();
    }

    @Test
    @DisplayName("infraKafkaListenerContainerFactory() creates a non-null factory with custom concurrency config")
    void infraKafkaListenerContainerFactory_setConcurrency() {
        InfraKafkaProperties props = defaultProperties();
        props.getConsumer().setConcurrency(5);

        KafkaConsumerConfig config = buildConfig(props);
        ConsumerFactory<String, Object> consumerFactory = config.infraKafkaConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                config.infraKafkaListenerContainerFactory(
                        consumerFactory, emptyErrorHandlerProvider(), emptyRecordInterceptorProvider());

        // Factory creation succeeds when concurrency override is applied
        assertThat(listenerFactory).isNotNull();
    }

    @Test
    @DisplayName("infraKafkaListenerContainerFactory() uses AckMode.RECORD by default")
    void infraKafkaListenerContainerFactory_usesRecordAckMode() {
        KafkaConsumerConfig config = buildConfig(defaultProperties());
        ConsumerFactory<String, Object> consumerFactory = config.infraKafkaConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, Object> listenerFactory =
                config.infraKafkaListenerContainerFactory(
                        consumerFactory, emptyErrorHandlerProvider(), emptyRecordInterceptorProvider());

        assertThat(listenerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns an ObjectProvider that always returns empty (no error handler).
     * Used to test the container factory without Phase 3 active.
     */
    private ObjectProvider<DefaultKafkaErrorHandler> emptyErrorHandlerProvider() {
        return new ObjectProvider<>() {
            @Override public DefaultKafkaErrorHandler getObject() { return null; }
            @Override public DefaultKafkaErrorHandler getObject(Object... args) { return null; }
            @Override public DefaultKafkaErrorHandler getIfAvailable() { return null; }
            @Override public DefaultKafkaErrorHandler getIfUnique() { return null; }
        };
    }

    /**
     * Returns an ObjectProvider that always returns empty (no record interceptor).
     * Used to test the container factory without Phase 4 active.
     */
    private ObjectProvider<RecordInterceptor<String, Object>> emptyRecordInterceptorProvider() {
        return new ObjectProvider<>() {
            @Override public RecordInterceptor<String, Object> getObject() { return null; }
            @Override public RecordInterceptor<String, Object> getObject(Object... args) { return null; }
            @Override public RecordInterceptor<String, Object> getIfAvailable() { return null; }
            @Override public RecordInterceptor<String, Object> getIfUnique() { return null; }
        };
    }
}

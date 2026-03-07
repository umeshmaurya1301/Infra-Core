package org.infra.kafka.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.observability.KafkaLoggingInterceptor;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.infra.kafka.security.SaslConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaProducerConfig Unit Tests")
class KafkaProducerConfigTest {

    private KafkaProducerConfig buildConfig(InfraKafkaProperties props) {
        KafkaSecurityConfig securityConfig = new KafkaSecurityConfig(props, new SaslConfigProvider());
        return new KafkaProducerConfig(props, securityConfig);
    }

    private InfraKafkaProperties defaultProperties() {
        return new InfraKafkaProperties();
    }

    /** Empty ObjectProvider — simulates Phase 4 logging interceptor not being in context. */
    private ObjectProvider<KafkaLoggingInterceptor> noInterceptor() {
        return new ObjectProvider<>() {
            @Override public KafkaLoggingInterceptor getObject() { return null; }
            @Override public KafkaLoggingInterceptor getObject(Object... args) { return null; }
            @Override public KafkaLoggingInterceptor getIfAvailable() { return null; }
            @Override public KafkaLoggingInterceptor getIfUnique() { return null; }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotent mode ON (default)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("producerConfigs() sets enable.idempotence=true by default")
    void producerConfigs_idempotentEnabled_byDefault() {
        KafkaProducerConfig config = buildConfig(defaultProperties());
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    @DisplayName("producerConfigs() sets acks=all when idempotent")
    void producerConfigs_acksAll_whenIdempotent() {
        KafkaProducerConfig config = buildConfig(defaultProperties());
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    }

    @Test
    @DisplayName("producerConfigs() overrides acks to all when idempotent and weaker acks provided")
    void producerConfigs_overridesAcks_whenWeakAcksAndIdempotent() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setAcks("1"); // weak acks — should be overridden

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    }

    @Test
    @DisplayName("producerConfigs() overrides retries to 3 when idempotent and retries=0")
    void producerConfigs_overridesRetries_whenZeroAndIdempotent() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setRetries(0); // invalid for idempotent

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat((int) configs.get(ProducerConfig.RETRIES_CONFIG)).isGreaterThan(0);
    }

    @Test
    @DisplayName("producerConfigs() caps maxInFlight to 5 when idempotent and higher value provided")
    void producerConfigs_capsMaxInFlight_whenIdempotent() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setMaxInFlightRequestsPerConnection(10); // too high for idempotent

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat((int) configs.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION))
                .isLessThanOrEqualTo(5);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotent mode OFF
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("producerConfigs() sets enable.idempotence=false when disabled")
    void producerConfigs_idempotentDisabled_whenConfiguredOff() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setIdempotent(false);

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
    }

    @Test
    @DisplayName("producerConfigs() respects configured acks when idempotent is false")
    void producerConfigs_respectsAcks_whenIdempotentDisabled() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setIdempotent(false);
        props.getProducer().setAcks("1");

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.ACKS_CONFIG, "1");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bootstrap / serializer / misc
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("producerConfigs() sets bootstrap servers from properties")
    void producerConfigs_setsBootstrapServers() {
        InfraKafkaProperties props = defaultProperties();
        props.setBootstrapServers("broker1:9092,broker2:9092");

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
    }

    @Test
    @DisplayName("producerConfigs() includes batch-size and linger-ms settings")
    void producerConfigs_includesBatchingSettings() {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setBatchSize(32768);
        props.getProducer().setLingerMs(20);

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        assertThat(configs).containsEntry(ProducerConfig.LINGER_MS_CONFIG, 20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "gzip", "snappy", "lz4", "zstd"})
    @DisplayName("producerConfigs() accepts all compression types")
    void producerConfigs_acceptsCompressionTypes(String compression) {
        InfraKafkaProperties props = defaultProperties();
        props.getProducer().setCompressionType(compression);

        KafkaProducerConfig config = buildConfig(props);
        Map<String, Object> configs = config.producerConfigs(noInterceptor());

        assertThat(configs).containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, compression);
    }

    @Test
    @DisplayName("producerConfigs() sets interceptor.classes when logging interceptor is provided")
    void producerConfigs_setsInterceptorClass_whenLoggingEnabled() {
        KafkaProducerConfig config = buildConfig(defaultProperties());

        // Provide a real interceptor instance via ObjectProvider
        KafkaLoggingInterceptor interceptor = new KafkaLoggingInterceptor();
        ObjectProvider<KafkaLoggingInterceptor> provider = new ObjectProvider<>() {
            @Override public KafkaLoggingInterceptor getObject() { return interceptor; }
            @Override public KafkaLoggingInterceptor getObject(Object... args) { return interceptor; }
            @Override public KafkaLoggingInterceptor getIfAvailable() { return interceptor; }
            @Override public KafkaLoggingInterceptor getIfUnique() { return interceptor; }
        };

        Map<String, Object> configs = config.producerConfigs(provider);

        assertThat(configs).containsEntry(
                ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                KafkaLoggingInterceptor.class.getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transactions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraKafkaProducerFactory() sets transactionIdPrefix when transactions are enabled")
    void infraKafkaProducerFactory_setsTransactionIdPrefix() {
        InfraKafkaProperties props = defaultProperties();
        props.getTransaction().setEnabled(true);
        props.getTransaction().setIdPrefix("test-txn-");

        KafkaProducerConfig config = buildConfig(props);
        DefaultKafkaProducerFactory<String, Object> factory =
                (DefaultKafkaProducerFactory<String, Object>) config.infraKafkaProducerFactory(noInterceptor());

        assertThat(factory.getTransactionIdPrefix()).isEqualTo("test-txn-");
    }
}

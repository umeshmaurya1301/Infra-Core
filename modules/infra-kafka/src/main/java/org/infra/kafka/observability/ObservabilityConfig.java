package org.infra.kafka.observability;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Spring {@link Configuration} that wires Phase 4 observability:
 * structured logging and Micrometer metrics for both the producer and consumer paths.
 *
 * <h3>Beans created</h3>
 * <ol>
 *   <li>{@link KafkaLoggingInterceptor} — producer-side interceptor; enabled when
 *       {@code infra.kafka.logging.enabled=true} (default)</li>
 *   <li>{@link KafkaMetricsConfig} — Micrometer metric registry wrapper; enabled when
 *       {@code infra.kafka.metrics.enabled=true} (default) <em>and</em> Micrometer is present</li>
 *   <li>{@link RecordInterceptor} ({@code infraKafkaConsumerInterceptor}) — consumer-side
 *       interceptor that populates MDC before processing and records timing + counters after</li>
 * </ol>
 *
 * <p>All beans are guarded with {@link ConditionalOnMissingBean} so consumer
 * applications can supply custom implementations when needed.
 */
@Slf4j
@Configuration
public class ObservabilityConfig {

    // Header names that carry correlation / trace IDs from upstream services
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_TRACE_ID       = "X-Trace-Id";

    // MDC keys — mirror the producer interceptor for consistent log correlation
    private static final String MDC_TOPIC       = KafkaLoggingInterceptor.MDC_TOPIC;
    private static final String MDC_KEY         = KafkaLoggingInterceptor.MDC_KEY;
    private static final String MDC_CORRELATION = KafkaLoggingInterceptor.MDC_CORRELATION;
    private static final String MDC_TRACE       = KafkaLoggingInterceptor.MDC_TRACE;

    private final InfraKafkaProperties properties;

    public ObservabilityConfig(InfraKafkaProperties properties) {
        this.properties = properties;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Producer-side observability
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the {@link KafkaLoggingInterceptor} bean registered on the producer factory.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaLoggingInterceptor.class)
    @ConditionalOnProperty(prefix = "infra.kafka.logging", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public KafkaLoggingInterceptor infraKafkaLoggingInterceptor() {
        log.info("[infra-kafka] KafkaLoggingInterceptor bean created");
        return new KafkaLoggingInterceptor();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Micrometer metrics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the {@link KafkaMetricsConfig} bean when Micrometer is present and
     * {@code infra.kafka.metrics.enabled=true} (the default).
     *
     * @param meterRegistry the Micrometer meter registry from Spring Boot Actuator
     */
    @Bean
    @ConditionalOnMissingBean(KafkaMetricsConfig.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnProperty(prefix = "infra.kafka.metrics", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public KafkaMetricsConfig infraKafkaMetricsConfig(
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        log.info("[infra-kafka] KafkaMetricsConfig bean created — registry={}",
                meterRegistry.getClass().getSimpleName());
        return new KafkaMetricsConfig(meterRegistry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consumer-side observability — RecordInterceptor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the consumer-side {@link RecordInterceptor} that:
     * <ol>
     *   <li>Populates MDC ({@code kafka.topic}, {@code kafka.key}, {@code correlationId},
     *       {@code traceId}) from message headers <em>before</em> the listener executes</li>
     *   <li>Clears MDC after the record has been processed</li>
     *   <li>Records a consumer-success Micrometer counter when {@link KafkaMetricsConfig} is present</li>
     * </ol>
     *
     * @param metricsConfig optional metrics config; used to record counters when available
     */
    @Bean("infraKafkaConsumerInterceptor")
    @ConditionalOnMissingBean(name = "infraKafkaConsumerInterceptor")
    @ConditionalOnProperty(prefix = "infra.kafka.logging", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RecordInterceptor<String, Object> infraKafkaConsumerInterceptor(
            ObjectProvider<KafkaMetricsConfig> metricsConfig) {

        log.info("[infra-kafka] Consumer RecordInterceptor created — MDC + metrics active");

        return new InfraConsumerRecordInterceptor(properties, metricsConfig);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner implementation class — avoids anonymous class / lambda type issues
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Named implementation of {@link RecordInterceptor} so the Spring Kafka
     * generic type system can resolve it unambiguously.
     */
    static class InfraConsumerRecordInterceptor implements RecordInterceptor<String, Object> {

        private final InfraKafkaProperties properties;
        private final ObjectProvider<KafkaMetricsConfig> metricsConfig;

        InfraConsumerRecordInterceptor(InfraKafkaProperties properties,
                                       ObjectProvider<KafkaMetricsConfig> metricsConfig) {
            this.properties = properties;
            this.metricsConfig = metricsConfig;
        }

        /**
         * Called <em>before</em> the listener method processes the record.
         * Populates the MDC with topic / key / correlation context from headers.
         */
        @Override
        public ConsumerRecord<String, Object> intercept(
                ConsumerRecord<String, Object> record,
                Consumer<String, Object> consumer) {

            MDC.put(MDC_TOPIC, record.topic());
            if (record.key() != null) MDC.put(MDC_KEY, record.key());

            String correlationId = headerValue(record, HEADER_CORRELATION_ID);
            if (correlationId != null) MDC.put(MDC_CORRELATION, correlationId);

            String traceId = headerValue(record, HEADER_TRACE_ID);
            if (traceId != null) MDC.put(MDC_TRACE, traceId);

            if (properties.getLogging().isEnabled()) {
                if (properties.getLogging().isLogPayload()) {
                    log.debug("[infra-kafka] CONSUME BEGIN topic={} partition={} offset={} key={} payload={}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), record.value());
                } else {
                    log.debug("[infra-kafka] CONSUME BEGIN topic={} partition={} offset={} key={}",
                            record.topic(), record.partition(), record.offset(), record.key());
                }
            }

            return record;
        }

        /**
         * Called <em>after</em> the listener method returns successfully.
         * Records the success metric and clears MDC.
         */
        @Override
        public void success(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
            try {
                String groupId = properties.getConsumer().getGroupId();
                metricsConfig.ifAvailable(m -> m.recordConsumerSuccess(record.topic(), groupId));

                if (properties.getLogging().isEnabled()) {
                    log.debug("[infra-kafka] CONSUME OK topic={} partition={} offset={} key={}",
                            record.topic(), record.partition(), record.offset(), record.key());
                }
            } finally {
                clearMdc();
            }
        }

        /**
         * Called when the listener method throws an exception.
         * Records the failure metric and clears MDC.
         */
        @Override
        public void failure(ConsumerRecord<String, Object> record,
                            Exception exception,
                            Consumer<String, Object> consumer) {
            try {
                String groupId = properties.getConsumer().getGroupId();
                String exName = exception.getClass().getSimpleName();
                metricsConfig.ifAvailable(m -> m.recordConsumerFailure(record.topic(), groupId, exName));

                log.warn("[infra-kafka] CONSUME FAILED topic={} partition={} offset={} key={} exception={}",
                        record.topic(), record.partition(), record.offset(),
                        record.key(), exception.getMessage(), exception);
            } finally {
                clearMdc();
            }
        }

        // ── helpers ──────────────────────────────────────────────────────────

        private void clearMdc() {
            MDC.remove(MDC_TOPIC);
            MDC.remove(MDC_KEY);
            MDC.remove(MDC_CORRELATION);
            MDC.remove(MDC_TRACE);
        }

        private String headerValue(ConsumerRecord<?, ?> record, String name) {
            var header = record.headers().lastHeader(name);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }
}

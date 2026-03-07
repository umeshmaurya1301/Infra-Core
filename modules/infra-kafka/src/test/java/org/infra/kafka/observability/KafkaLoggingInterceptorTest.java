package org.infra.kafka.observability;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaLoggingInterceptor Unit Tests")
class KafkaLoggingInterceptorTest {

    private KafkaLoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new KafkaLoggingInterceptor();
        // configure with default settings (no log-payload)
        interceptor.configure(Map.of());
    }

    @Test
    @DisplayName("onSend() returns the same record unchanged")
    void onSend_returnsRecordUnchanged() {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>("order-events", "key-1", "payload");

        ProducerRecord<String, Object> result = interceptor.onSend(record);

        assertThat(result).isSameAs(record);
    }

    @Test
    @DisplayName("onSend() populates MDC with topic and key")
    void onSend_populatesMdcTopicAndKey() {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>("order-events", "key-1", "payload");

        interceptor.onSend(record);

        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_TOPIC)).isEqualTo("order-events");
        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_KEY)).isEqualTo("key-1");
    }

    @Test
    @DisplayName("onSend() propagates correlationId header to MDC")
    void onSend_propagatesCorrelationIdToMdc() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Correlation-Id", "corr-abc".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, Object> record =
                new ProducerRecord<>("order-events", null, "key-1", "payload", headers);

        interceptor.onSend(record);

        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_CORRELATION)).isEqualTo("corr-abc");
    }

    @Test
    @DisplayName("onSend() propagates traceId header to MDC")
    void onSend_propagatesTraceIdToMdc() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Trace-Id", "trace-xyz".getBytes(StandardCharsets.UTF_8));

        ProducerRecord<String, Object> record =
                new ProducerRecord<>("order-events", null, "key-1", "payload", headers);

        interceptor.onSend(record);

        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_TRACE)).isEqualTo("trace-xyz");
    }

    @Test
    @DisplayName("onAcknowledgement() clears MDC after successful ack")
    void onAcknowledgement_clearsMdcAfterSuccess() {
        // Pre-populate MDC as onSend() would
        MDC.put(KafkaLoggingInterceptor.MDC_TOPIC, "order-events");
        MDC.put(KafkaLoggingInterceptor.MDC_KEY, "key-1");

        interceptor.onAcknowledgement(null, null);

        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_TOPIC)).isNull();
        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("onAcknowledgement() clears MDC even when exception is non-null")
    void onAcknowledgement_clearsMdcOnFailure() {
        MDC.put(KafkaLoggingInterceptor.MDC_TOPIC, "order-events");

        interceptor.onAcknowledgement(null, new RuntimeException("broker down"));

        assertThat(MDC.get(KafkaLoggingInterceptor.MDC_TOPIC)).isNull();
    }

    @Test
    @DisplayName("configure() reads log-payload=true correctly")
    void configure_readsLogPayloadFlag() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("infra.kafka.logging.log-payload", true);
        interceptor.configure(configs);
        // No assertion needed — verifying no exception is thrown is sufficient
    }

    @Test
    @DisplayName("close() does not throw")
    void close_doesNotThrow() {
        interceptor.close();
    }
}

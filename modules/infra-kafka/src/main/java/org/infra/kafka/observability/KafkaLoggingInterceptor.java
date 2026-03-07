package org.infra.kafka.observability;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ProducerInterceptor} that provides structured logging and
 * MDC (Mapped Diagnostic Context) propagation for every produced message.
 *
 * <h3>What it does</h3>
 * <ul>
 *   <li>Logs at {@code DEBUG} level before the record is sent (pre-send)</li>
 *   <li>Logs at {@code INFO} on successful acknowledgment from the broker</li>
 *   <li>Logs at {@code ERROR} on send failure</li>
 *   <li>Propagates MDC keys ({@code correlationId}, {@code traceId}) from the
 *       message headers into the log context so that all log lines for a
 *       single request chain share the same IDs in log aggregation tools</li>
 * </ul>
 *
 * <h3>MDC keys set</h3>
 * <table>
 *   <tr><th>Key</th><th>Source</th></tr>
 *   <tr><td>{@code kafka.topic}</td><td>ProducerRecord.topic()</td></tr>
 *   <tr><td>{@code kafka.key}</td><td>ProducerRecord.key()</td></tr>
 *   <tr><td>{@code correlationId}</td><td>{@code X-Correlation-Id} header</td></tr>
 *   <tr><td>{@code traceId}</td><td>{@code X-Trace-Id} header</td></tr>
 * </table>
 *
 * <h3>Activation</h3>
 * Registered automatically via {@link ObservabilityConfig} when
 * {@code infra.kafka.logging.enabled=true} (the default).
 * Consumer projects can disable it with {@code infra.kafka.logging.enabled=false}.
 */
@Slf4j
public class KafkaLoggingInterceptor implements ProducerInterceptor<String, Object> {

    // MDC key names — use these to filter in Kibana / Grafana Loki / Splunk
    public static final String MDC_TOPIC         = "kafka.topic";
    public static final String MDC_KEY           = "kafka.key";
    public static final String MDC_CORRELATION   = "correlationId";
    public static final String MDC_TRACE         = "traceId";

    // Header names that carry correlation / trace IDs set by the caller
    static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    static final String HEADER_TRACE_ID       = "X-Trace-Id";

    private boolean logPayload = false;

    // ─────────────────────────────────────────────────────────────────────────
    // ProducerInterceptor lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void configure(Map<String, ?> configs) {
        Object logPayloadProp = configs.get("infra.kafka.logging.log-payload");
        if (logPayloadProp instanceof Boolean b) {
            this.logPayload = b;
        } else if (logPayloadProp instanceof String s) {
            this.logPayload = Boolean.parseBoolean(s);
        }
        log.info("[infra-kafka] KafkaLoggingInterceptor configured — logPayload={}", logPayload);
    }

    /**
     * Called before the record is serialized and sent.
     * Populates the MDC context with topic/key/correlation metadata.
     */
    @Override
    public ProducerRecord<String, Object> onSend(ProducerRecord<String, Object> record) {
        populateMdc(record.topic(), record.key(), record.headers());

        if (logPayload) {
            log.debug("[infra-kafka] PRE-SEND topic={} key={} payload={}",
                    record.topic(), record.key(), record.value());
        } else {
            log.debug("[infra-kafka] PRE-SEND topic={} key={}",
                    record.topic(), record.key());
        }

        return record;
    }

    /**
     * Called after a successful broker acknowledgment.
     * Logs partition and offset at INFO level.
     */
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        try {
            if (exception != null) {
                log.error("[infra-kafka] SEND FAILED topic={} partition={} exception={}",
                        metadata != null ? metadata.topic() : "unknown",
                        metadata != null ? metadata.partition() : -1,
                        exception.getMessage(), exception);
            } else if (metadata != null) {
                log.info("[infra-kafka] SEND ACK topic={} partition={} offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        } finally {
            clearMdc();
        }
    }

    @Override
    public void close() {
        // no resources to release
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MDC helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void populateMdc(String topic, String key, Headers headers) {
        MDC.put(MDC_TOPIC, topic);
        if (key != null) {
            MDC.put(MDC_KEY, key);
        }

        String correlationId = headerValue(headers, HEADER_CORRELATION_ID);
        if (correlationId != null) {
            MDC.put(MDC_CORRELATION, correlationId);
        }

        String traceId = headerValue(headers, HEADER_TRACE_ID);
        if (traceId != null) {
            MDC.put(MDC_TRACE, traceId);
        }
    }

    private void clearMdc() {
        MDC.remove(MDC_TOPIC);
        MDC.remove(MDC_KEY);
        MDC.remove(MDC_CORRELATION);
        MDC.remove(MDC_TRACE);
    }

    private String headerValue(Headers headers, String name) {
        var header = headers.lastHeader(name);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}

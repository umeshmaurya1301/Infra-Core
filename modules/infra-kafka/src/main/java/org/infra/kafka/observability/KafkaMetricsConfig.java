package org.infra.kafka.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Micrometer-based metrics configuration for the infra-kafka library.
 *
 * <p>Registers and caches the following meters per topic:
 *
 * <h3>Producer Metrics</h3>
 * <table>
 *   <tr><th>Metric name</th><th>Type</th><th>Tags</th><th>Description</th></tr>
 *   <tr><td>{@code infra.kafka.producer.send.success}</td><td>Counter</td>
 *       <td>topic</td><td>Successfully acknowledged sends</td></tr>
 *   <tr><td>{@code infra.kafka.producer.send.failure}</td><td>Counter</td>
 *       <td>topic, exception</td><td>Failed send attempts</td></tr>
 *   <tr><td>{@code infra.kafka.producer.send.duration}</td><td>Timer</td>
 *       <td>topic</td><td>End-to-end send latency</td></tr>
 * </table>
 *
 * <h3>Consumer Metrics</h3>
 * <table>
 *   <tr><th>Metric name</th><th>Type</th><th>Tags</th><th>Description</th></tr>
 *   <tr><td>{@code infra.kafka.consumer.record.success}</td><td>Counter</td>
 *       <td>topic, groupId</td><td>Successfully processed records</td></tr>
 *   <tr><td>{@code infra.kafka.consumer.record.failure}</td><td>Counter</td>
 *       <td>topic, groupId, exception</td><td>Records that triggered an exception</td></tr>
 *   <tr><td>{@code infra.kafka.consumer.record.dlq}</td><td>Counter</td>
 *       <td>topic, groupId</td><td>Records routed to DLQ after exhausted retries</td></tr>
 *   <tr><td>{@code infra.kafka.consumer.record.duration}</td><td>Timer</td>
 *       <td>topic, groupId</td><td>Record processing latency</td></tr>
 * </table>
 *
 * <p>All meters are lazily created and cached per (topic[, groupId]) combination
 * to avoid repeated registry lookups in the hot path.
 *
 * <h3>Usage in Grafana / Prometheus</h3>
 * <pre>
 * # Total produce rate
 * sum(rate(infra_kafka_producer_send_success_total[1m])) by (topic)
 *
 * # Consumer error rate
 * sum(rate(infra_kafka_consumer_record_failure_total[1m])) by (topic, groupId)
 *
 * # DLQ rate
 * sum(rate(infra_kafka_consumer_record_dlq_total[1m])) by (topic)
 *
 * # p99 send latency
 * histogram_quantile(0.99, rate(infra_kafka_producer_send_duration_seconds_bucket[5m]))
 * </pre>
 */
@Slf4j
public class KafkaMetricsConfig {

    // ── Metric names ─────────────────────────────────────────────────────────
    public static final String METRIC_PRODUCER_SEND_SUCCESS  = "infra.kafka.producer.send.success";
    public static final String METRIC_PRODUCER_SEND_FAILURE  = "infra.kafka.producer.send.failure";
    public static final String METRIC_PRODUCER_SEND_DURATION = "infra.kafka.producer.send.duration";

    public static final String METRIC_CONSUMER_SUCCESS       = "infra.kafka.consumer.record.success";
    public static final String METRIC_CONSUMER_FAILURE       = "infra.kafka.consumer.record.failure";
    public static final String METRIC_CONSUMER_DLQ           = "infra.kafka.consumer.record.dlq";
    public static final String METRIC_CONSUMER_DURATION      = "infra.kafka.consumer.record.duration";

    private final MeterRegistry meterRegistry;

    // ── Lazy caches — avoid redundant registry lookups in hot path ───────────
    private final ConcurrentMap<String, Counter> producerSuccessCounters  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> producerFailureCounters  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer>   producerSendTimers       = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Counter> consumerSuccessCounters  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> consumerFailureCounters  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> consumerDlqCounters      = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer>   consumerDurationTimers   = new ConcurrentHashMap<>();

    public KafkaMetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[infra-kafka] KafkaMetricsConfig initialized — Micrometer metrics active");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Producer metric accessors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Increments the producer send-success counter for {@code topic}.
     *
     * @param topic the Kafka topic name
     */
    public void recordProducerSuccess(String topic) {
        producerSuccessCounters
                .computeIfAbsent(topic, t -> Counter.builder(METRIC_PRODUCER_SEND_SUCCESS)
                        .description("Number of successfully acknowledged Kafka produces")
                        .tag("topic", t)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * Increments the producer send-failure counter for {@code topic}.
     *
     * @param topic         the Kafka topic name
     * @param exceptionName simple class name of the exception (tag value)
     */
    public void recordProducerFailure(String topic, String exceptionName) {
        String key = topic + "|" + exceptionName;
        producerFailureCounters
                .computeIfAbsent(key, k -> Counter.builder(METRIC_PRODUCER_SEND_FAILURE)
                        .description("Number of failed Kafka produce attempts")
                        .tag("topic", topic)
                        .tag("exception", exceptionName)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * Returns the producer send-duration {@link Timer} for {@code topic},
     * creating it on first access.
     *
     * @param topic the Kafka topic name
     * @return timer to record send latency
     */
    public Timer producerSendTimer(String topic) {
        return producerSendTimers
                .computeIfAbsent(topic, t -> Timer.builder(METRIC_PRODUCER_SEND_DURATION)
                        .description("End-to-end Kafka produce latency")
                        .tag("topic", t)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consumer metric accessors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Increments the consumer record-success counter for the given topic and groupId.
     *
     * @param topic   the Kafka topic name
     * @param groupId consumer group ID
     */
    public void recordConsumerSuccess(String topic, String groupId) {
        String key = topic + "|" + groupId;
        consumerSuccessCounters
                .computeIfAbsent(key, k -> Counter.builder(METRIC_CONSUMER_SUCCESS)
                        .description("Number of successfully processed consumer records")
                        .tag("topic", topic)
                        .tag("groupId", groupId)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * Increments the consumer record-failure counter for the given topic and groupId.
     *
     * @param topic         the Kafka topic name
     * @param groupId       consumer group ID
     * @param exceptionName simple class name of the exception
     */
    public void recordConsumerFailure(String topic, String groupId, String exceptionName) {
        String key = topic + "|" + groupId + "|" + exceptionName;
        consumerFailureCounters
                .computeIfAbsent(key, k -> Counter.builder(METRIC_CONSUMER_FAILURE)
                        .description("Number of consumer records that triggered exceptions")
                        .tag("topic", topic)
                        .tag("groupId", groupId)
                        .tag("exception", exceptionName)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * Increments the consumer DLQ-routed counter for the given topic and groupId.
     *
     * @param topic   the Kafka topic name
     * @param groupId consumer group ID
     */
    public void recordConsumerDlq(String topic, String groupId) {
        String key = topic + "|" + groupId;
        consumerDlqCounters
                .computeIfAbsent(key, k -> Counter.builder(METRIC_CONSUMER_DLQ)
                        .description("Number of consumer records routed to DLQ after exhausted retries")
                        .tag("topic", topic)
                        .tag("groupId", groupId)
                        .register(meterRegistry))
                .increment();
    }

    /**
     * Returns the consumer record-processing {@link Timer} for {topic, groupId},
     * creating it on first access.
     *
     * @param topic   the Kafka topic name
     * @param groupId consumer group ID
     * @return timer to measure record processing latency
     */
    public Timer consumerDurationTimer(String topic, String groupId) {
        String key = topic + "|" + groupId;
        return consumerDurationTimers
                .computeIfAbsent(key, k -> Timer.builder(METRIC_CONSUMER_DURATION)
                        .description("Kafka consumer record processing latency")
                        .tag("topic", topic)
                        .tag("groupId", groupId)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry));
    }
}

package org.infra.kafka.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaMetricsConfig Unit Tests")
class KafkaMetricsConfigTest {

    private SimpleMeterRegistry registry;
    private KafkaMetricsConfig metricsConfig;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsConfig = new KafkaMetricsConfig(registry);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Producer metrics
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordProducerSuccess() increments counter by 1")
    void recordProducerSuccess_incrementsCounter() {
        metricsConfig.recordProducerSuccess("order-events");
        metricsConfig.recordProducerSuccess("order-events");

        Counter counter = registry.find(KafkaMetricsConfig.METRIC_PRODUCER_SEND_SUCCESS)
                .tag("topic", "order-events")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("recordProducerFailure() increments failure counter with exception tag")
    void recordProducerFailure_incrementsCounter() {
        metricsConfig.recordProducerFailure("order-events", "TimeoutException");

        Counter counter = registry.find(KafkaMetricsConfig.METRIC_PRODUCER_SEND_FAILURE)
                .tag("topic", "order-events")
                .tag("exception", "TimeoutException")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("producerSendTimer() returns non-null timer and records samples")
    void producerSendTimer_recordsSamples() {
        Timer timer = metricsConfig.producerSendTimer("order-events");

        timer.record(100, TimeUnit.MILLISECONDS);
        timer.record(200, TimeUnit.MILLISECONDS);

        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("producerSendTimer() returns the same cached Timer on repeated calls")
    void producerSendTimer_returnsCachedInstance() {
        Timer t1 = metricsConfig.producerSendTimer("order-events");
        Timer t2 = metricsConfig.producerSendTimer("order-events");

        assertThat(t1).isSameAs(t2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consumer metrics
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("recordConsumerSuccess() increments counter with topic and groupId tags")
    void recordConsumerSuccess_incrementsCounter() {
        metricsConfig.recordConsumerSuccess("order-events", "order-service");

        Counter counter = registry.find(KafkaMetricsConfig.METRIC_CONSUMER_SUCCESS)
                .tag("topic", "order-events")
                .tag("groupId", "order-service")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordConsumerFailure() increments failure counter with exception tag")
    void recordConsumerFailure_incrementsCounter() {
        metricsConfig.recordConsumerFailure("order-events", "order-service", "IllegalStateException");

        Counter counter = registry.find(KafkaMetricsConfig.METRIC_CONSUMER_FAILURE)
                .tag("topic", "order-events")
                .tag("groupId", "order-service")
                .tag("exception", "IllegalStateException")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordConsumerDlq() increments DLQ counter")
    void recordConsumerDlq_incrementsCounter() {
        metricsConfig.recordConsumerDlq("order-events", "order-service");
        metricsConfig.recordConsumerDlq("order-events", "order-service");
        metricsConfig.recordConsumerDlq("order-events", "order-service");

        Counter counter = registry.find(KafkaMetricsConfig.METRIC_CONSUMER_DLQ)
                .tag("topic", "order-events")
                .tag("groupId", "order-service")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("consumerDurationTimer() returns non-null timer and records samples")
    void consumerDurationTimer_recordsSamples() {
        Timer timer = metricsConfig.consumerDurationTimer("order-events", "order-service");

        timer.record(50, TimeUnit.MILLISECONDS);

        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("counters for different topics are tracked independently")
    void counters_trackedIndependentlyPerTopic() {
        metricsConfig.recordProducerSuccess("topic-a");
        metricsConfig.recordProducerSuccess("topic-a");
        metricsConfig.recordProducerSuccess("topic-b");

        Counter counterA = registry.find(KafkaMetricsConfig.METRIC_PRODUCER_SEND_SUCCESS)
                .tag("topic", "topic-a").counter();
        Counter counterB = registry.find(KafkaMetricsConfig.METRIC_PRODUCER_SEND_SUCCESS)
                .tag("topic", "topic-b").counter();

        assertThat(counterA.count()).isEqualTo(2.0);
        assertThat(counterB.count()).isEqualTo(1.0);
    }
}

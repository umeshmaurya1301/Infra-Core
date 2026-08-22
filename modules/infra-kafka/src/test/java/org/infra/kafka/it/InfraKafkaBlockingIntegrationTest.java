package org.infra.kafka.it;

import org.infra.kafka.autoconfigure.InfraKafkaAutoConfiguration;
import org.infra.kafka.consumer.InfraKafkaListener;
import org.infra.kafka.producer.KafkaMessagePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.handler.annotation.Payload;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for <b>blocking</b> retry mode against a real (embedded) Kafka broker.
 *
 * <p>Unlike the {@code ApplicationContextRunner} wiring tests, these boot the library exactly the
 * way a consuming service does — {@code @EnableKafka} + the auto-configuration + a live broker —
 * and drive actual produce/consume/retry/DLQ traffic. They prove three behaviours:
 * <ol>
 *   <li>a JSON payload published via {@link KafkaMessagePublisher} round-trips to an
 *       {@code @InfraKafkaListener} and deserializes back to its typed POJO;</li>
 *   <li>a listener that keeps throwing is retried {@code max-attempts} times and then the record
 *       lands in {@code <topic>-dlq} (default retrying factory);</li>
 *   <li>a listener on the no-retry factory sends a failure <em>straight</em> to the DLQ on the
 *       first attempt, with no retries.</li>
 * </ol>
 *
 * <p>The broker is embedded (no Docker). Retry backoff is compressed to ~200&nbsp;ms so the suite
 * runs in a few seconds.
 */
@SpringBootTest(
        classes = InfraKafkaBlockingIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "infra.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "infra.kafka.consumer.group-id=it-blocking",
                "infra.kafka.consumer.concurrency=1",
                "infra.kafka.consumer.trusted-packages=org.infra.kafka.it",
                "infra.kafka.retry.max-attempts=3",
                "infra.kafka.retry.backoff-initial-interval=200",
                "infra.kafka.retry.backoff-multiplier=1.0",
                "infra.kafka.retry.backoff-max-interval=200",
                "infra.kafka.logging.enabled=false"
        })
@EmbeddedKafka(partitions = 1, topics = {
        "it-roundtrip",
        "it-blk-retry", "it-blk-retry-dlq",
        "it-noretry", "it-noretry-dlq"
})
@DisplayName("infra-kafka — blocking-mode end-to-end (EmbeddedKafka)")
class InfraKafkaBlockingIntegrationTest {

    @Autowired KafkaMessagePublisher publisher;
    @Autowired RoundTripListener roundTrip;
    @Autowired FailingRetryListener failingRetry;
    @Autowired RetryDlqListener retryDlq;
    @Autowired FailingNoRetryListener failingNoRetry;
    @Autowired NoRetryDlqListener noRetryDlq;

    @Test
    @DisplayName("publish → consume round-trips a typed JSON payload")
    void roundTrip() throws InterruptedException {
        publisher.send("it-roundtrip", "order-1", new OrderEvent("order-1", "NEW"));

        OrderEvent received = roundTrip.received.poll(10, TimeUnit.SECONDS);

        assertThat(received).as("listener should have consumed the published event").isNotNull();
        assertThat(received.getId()).isEqualTo("order-1");
        assertThat(received.getStatus()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("failing listener is retried max-attempts times, then routed to <topic>-dlq")
    void blockingRetryThenDlq() throws InterruptedException {
        publisher.send("it-blk-retry", "order-2", new OrderEvent("order-2", "BOOM"));

        OrderEvent inDlq = retryDlq.received.poll(15, TimeUnit.SECONDS);

        assertThat(inDlq).as("record should land in the DLQ after retries are exhausted").isNotNull();
        assertThat(inDlq.getId()).isEqualTo("order-2");
        // max-attempts=3 → the original delivery plus 2 retries = 3 invocations before DLQ.
        assertThat(failingRetry.attempts.get())
                .as("listener should have been retried up to max-attempts")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("no-retry factory sends a failure straight to the DLQ (exactly one attempt)")
    void noRetryImmediateDlq() throws InterruptedException {
        publisher.send("it-noretry", "order-3", new OrderEvent("order-3", "BOOM"));

        OrderEvent inDlq = noRetryDlq.received.poll(10, TimeUnit.SECONDS);

        assertThat(inDlq).as("record should be in the DLQ immediately").isNotNull();
        assertThat(inDlq.getId()).isEqualTo("order-3");
        assertThat(failingNoRetry.attempts.get())
                .as("no-retry factory must not retry — exactly one invocation")
                .isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test context — the library the way a real service wires it, minus Docker.
    // ─────────────────────────────────────────────────────────────────────────

    @SpringBootConfiguration
    @EnableKafka
    @Import(InfraKafkaAutoConfiguration.class)
    static class TestApp {
        // A real service gets this from spring-boot-starter-actuator; supply one so
        // ObservabilityConfig's Micrometer beans can be satisfied.
        @Bean io.micrometer.core.instrument.MeterRegistry meterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }

        @Bean RoundTripListener roundTripListener() { return new RoundTripListener(); }
        @Bean FailingRetryListener failingRetryListener() { return new FailingRetryListener(); }
        @Bean RetryDlqListener retryDlqListener() { return new RetryDlqListener(); }
        @Bean FailingNoRetryListener failingNoRetryListener() { return new FailingNoRetryListener(); }
        @Bean NoRetryDlqListener noRetryDlqListener() { return new NoRetryDlqListener(); }
    }

    /** Happy-path consumer that captures the deserialized event. */
    static class RoundTripListener {
        final BlockingQueue<OrderEvent> received = new LinkedBlockingQueue<>();

        @InfraKafkaListener(topics = "it-roundtrip", groupId = "it-roundtrip-grp")
        public void onMessage(@Payload OrderEvent event) {
            received.add(event);
        }
    }

    /** Always fails so the default (retrying) factory exhausts retries and routes to the DLQ. */
    static class FailingRetryListener {
        final AtomicInteger attempts = new AtomicInteger();

        @InfraKafkaListener(topics = "it-blk-retry", groupId = "it-blk-retry-grp")
        public void onMessage(@Payload OrderEvent event) {
            attempts.incrementAndGet();
            throw new RuntimeException("boom (retryable) for " + event.getId());
        }
    }

    /** Captures whatever the retrying factory dead-letters. */
    static class RetryDlqListener {
        final BlockingQueue<OrderEvent> received = new LinkedBlockingQueue<>();

        @InfraKafkaListener(topics = "it-blk-retry-dlq", groupId = "it-blk-retry-dlq-grp",
                containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
        public void onMessage(@Payload OrderEvent event) {
            received.add(event);
        }
    }

    /** Always fails on the no-retry factory — must go straight to the DLQ. */
    static class FailingNoRetryListener {
        final AtomicInteger attempts = new AtomicInteger();

        @InfraKafkaListener(topics = "it-noretry", groupId = "it-noretry-grp",
                containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
        public void onMessage(@Payload OrderEvent event) {
            attempts.incrementAndGet();
            throw new RuntimeException("boom (immediate dlq) for " + event.getId());
        }
    }

    /** Captures whatever the no-retry factory dead-letters. */
    static class NoRetryDlqListener {
        final BlockingQueue<OrderEvent> received = new LinkedBlockingQueue<>();

        @InfraKafkaListener(topics = "it-noretry-dlq", groupId = "it-noretry-dlq-grp",
                containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
        public void onMessage(@Payload OrderEvent event) {
            received.add(event);
        }
    }
}

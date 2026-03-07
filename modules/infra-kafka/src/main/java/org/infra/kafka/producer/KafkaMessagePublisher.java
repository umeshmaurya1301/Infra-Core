package org.infra.kafka.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Primary producer API for the infra-kafka library.
 *
 * <p>Provides fire-and-forget ({@link #send}) and synchronous ({@link #sendAndWait})
 * publish methods. Both variants support optional header enrichment.
 *
 * <p>Idempotent producer is <b>enabled by default</b> ({@code enable.idempotence=true},
 * {@code acks=all}, {@code retries>0}). The Kafka broker deduplicates messages
 * using Producer ID + sequence number.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Autowired
 * private KafkaMessagePublisher publisher;
 *
 * // Basic publish
 * publisher.send("order-events", order.getId(), order);
 *
 * // With custom headers
 * publisher.send("order-events", order.getId(), order, headers -> {
 *     headers.add("X-Correlation-Id", correlationId.getBytes());
 *     headers.add("X-Event-Type", "ORDER_CREATED".getBytes());
 * });
 *
 * // Synchronous (blocks until broker acks)
 * SendResult<String, Object> result = publisher.sendAndWait("order-events", order.getId(), order);
 * }</pre>
 */
@Slf4j
public class KafkaMessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InfraKafkaProperties properties;

    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                 InfraKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fire-and-forget (async) variants
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Publishes a message to the specified topic asynchronously.
     *
     * @param topic   target Kafka topic
     * @param key     partition key (guarantees ordering per key)
     * @param payload the message payload (serialized to JSON by default)
     * @return a {@link CompletableFuture} that completes when the broker acknowledges
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic,
                                                              String key,
                                                              Object payload) {
        return send(topic, key, payload, null);
    }

    /**
     * Publishes a message to the specified topic asynchronously,
     * with optional header customization.
     *
     * @param topic          target Kafka topic
     * @param key            partition key
     * @param payload        the message payload
     * @param headerConsumer optional callback to add/modify Kafka headers;
     *                       may be {@code null} if no extra headers are needed
     * @return a {@link CompletableFuture} that completes when the broker acknowledges
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic,
                                                              String key,
                                                              Object payload,
                                                              Consumer<org.apache.kafka.common.header.Headers> headerConsumer) {
        ProducerRecord<String, Object> record = buildRecord(topic, key, payload, headerConsumer);

        if (properties.getLogging().isEnabled()) {
            if (properties.getLogging().isLogPayload()) {
                log.info("[infra-kafka] SEND topic={} key={} payload={}", topic, key, payload);
            } else {
                log.info("[infra-kafka] SEND topic={} key={}", topic, key);
            }
        }

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[infra-kafka] SEND FAILED topic={} key={} error={}",
                        topic, key, ex.getMessage(), ex);
                handleSendFailure(topic, key, payload, ex);
            } else if (properties.getLogging().isEnabled()) {
                log.info("[infra-kafka] SEND OK topic={} key={} partition={} offset={}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Publishes a message to the specified topic on a specific partition.
     *
     * @param topic     target Kafka topic
     * @param partition target partition index
     * @param key       partition key
     * @param payload   the message payload
     * @return a {@link CompletableFuture} that completes when the broker acknowledges
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic,
                                                              int partition,
                                                              String key,
                                                              Object payload) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, partition, key, payload);

        if (properties.getLogging().isEnabled()) {
            log.info("[infra-kafka] SEND topic={} partition={} key={}", topic, partition, key);
        }

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[infra-kafka] SEND FAILED topic={} partition={} key={} error={}",
                        topic, partition, key, ex.getMessage(), ex);
                handleSendFailure(topic, key, payload, ex);
            } else if (properties.getLogging().isEnabled()) {
                log.info("[infra-kafka] SEND OK topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Synchronous (blocking) variants
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Publishes a message synchronously and blocks until the broker acknowledges.
     *
     * <p><b>Warning</b>: blocks the calling thread. Use only when the result metadata
     * (partition / offset) is required immediately, or in tests.
     *
     * @param topic   target Kafka topic
     * @param key     partition key
     * @param payload the message payload
     * @return the {@link SendResult} containing partition and offset information
     * @throws RuntimeException if the send fails or times out
     */
    public SendResult<String, Object> sendAndWait(String topic, String key, Object payload) {
        return sendAndWait(topic, key, payload, null);
    }

    /**
     * Publishes a message synchronously with optional header customization.
     *
     * @param topic          target Kafka topic
     * @param key            partition key
     * @param payload        the message payload
     * @param headerConsumer optional header callback
     * @return the {@link SendResult} containing partition and offset information
     * @throws RuntimeException if the send fails or times out
     */
    public SendResult<String, Object> sendAndWait(String topic,
                                                   String key,
                                                   Object payload,
                                                   Consumer<org.apache.kafka.common.header.Headers> headerConsumer) {
        try {
            return send(topic, key, payload, headerConsumer).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "[infra-kafka] Interrupted while waiting for send result on topic=" + topic, e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "[infra-kafka] Send failed on topic=" + topic + " key=" + key, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ProducerRecord<String, Object> buildRecord(String topic,
                                                       String key,
                                                       Object payload,
                                                       Consumer<org.apache.kafka.common.header.Headers> headerConsumer) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);

        // Apply user-supplied header enrichment
        if (headerConsumer != null) {
            headerConsumer.accept(record.headers());
        }

        // Library default: stamp every message with the event type derived from payload class
        if (payload != null) {
            record.headers().add(new RecordHeader(
                    "X-Infra-Event-Type",
                    payload.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8)));
        }

        return record;
    }

    /**
     * Extension point: invoked when an async send fails.
     * Override or configure a {@link ProducerCallback} bean to react to failures.
     */
    protected void handleSendFailure(String topic, String key, Object payload, Throwable cause) {
        // Default: already logged above. Subclasses or callback beans can extend this.
    }
}

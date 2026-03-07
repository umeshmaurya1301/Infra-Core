package org.infra.kafka.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

/**
 * Utility class that provides structured processing of individual
 * {@link ConsumerRecord}s along with optional acknowledgment management.
 *
 * <p>This class is intended for use-cases where a consumer method handles a
 * {@link ConsumerRecord} directly (rather than the deserialized payload) and
 * needs to control acknowledgement manually. When the listener container
 * {@code AckMode} is {@code RECORD} (the library default), Spring Kafka manages
 * offset commits automatically — this class is then useful purely for logging
 * and metrics hooks.
 *
 * <h3>Usage — direct record access</h3>
 * <pre>{@code
 * @InfraKafkaListener(topics = "order-events", groupId = "order-service")
 * public void consume(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
 *     ConsumerRecordProcessor.process(record, ack, payload -> {
 *         orderService.handle(payload);
 *     });
 * }
 * }</pre>
 *
 * <h3>Usage — payload only (AckMode.RECORD, no explicit ack)</h3>
 * <pre>{@code
 * @InfraKafkaListener(topics = "order-events", groupId = "order-service")
 * public void consume(OrderEvent event) {
 *     // Spring commits offset automatically after method returns without exception
 * }
 * }</pre>
 */
@Slf4j
public final class ConsumerRecordProcessor {

    private ConsumerRecordProcessor() {
        // utility class — no instances
    }

    /**
     * Processes a {@link ConsumerRecord} by applying the specified handler and
     * acknowledging the offset on success.
     *
     * <p>On any exception the acknowledgment is <em>not</em> called, and the
     * exception propagates to the container's error handler.
     *
     * @param <V>     value type
     * @param record  the consumer record to process
     * @param ack     the acknowledgment; may be {@code null} when the container
     *                manages acks automatically (e.g. {@code AckMode.RECORD})
     * @param handler a {@link RecordHandler} that performs the business logic
     */
    public static <V> void process(ConsumerRecord<String, V> record,
                                   Acknowledgment ack,
                                   RecordHandler<V> handler) {
        String topic = record.topic();
        int partition = record.partition();
        long offset = record.offset();
        String key = record.key();

        log.debug("[infra-kafka] CONSUME BEGIN topic={} partition={} offset={} key={}",
                topic, partition, offset, key);

        try {
            handler.handle(record.value());
        } catch (RuntimeException e) {
            throw e; // propagate as-is to the container error handler
        } catch (Exception e) {
            throw new RuntimeException(
                    "[infra-kafka] Record processing failed — topic=" + topic
                            + " partition=" + partition + " offset=" + offset, e);
        }

        // Acknowledge only if a manual ack is provided
        if (ack != null) {
            ack.acknowledge();
        }

        log.debug("[infra-kafka] CONSUME OK    topic={} partition={} offset={} key={}",
                topic, partition, offset, key);
    }

    /**
     * Functional interface for the business logic that processes the record value.
     *
     * @param <V> value type
     */
    @FunctionalInterface
    public interface RecordHandler<V> {
        /**
         * Perform business logic on the deserialized payload.
         *
         * @param value the deserialized record value
         * @throws Exception any exception causes the offset not to be committed
         */
        void handle(V value) throws Exception;
    }
}

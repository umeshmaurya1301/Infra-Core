package org.infra.kafka.error;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;

import java.nio.charset.StandardCharsets;

/**
 * Handles Kafka messages that fail to deserialize, routing them directly to
 * the Dead Letter Queue (DLQ) without any retry attempts.
 *
 * <p>Deserialization failures are fundamentally different from transient errors:
 * retrying a corrupt or schema-mismatched message will <em>never</em> succeed.
 * This handler detects {@link DeserializationException}s injected by Spring Kafka's
 * {@link org.springframework.kafka.support.serializer.ErrorHandlingDeserializer} and
 * immediately publishes the raw bytes to the DLQ topic.
 *
 * <h3>DLQ Additional Headers (set by this handler)</h3>
 * <ul>
 *   <li>{@code X-Infra-DLQ-Reason} — {@code "DESERIALIZATION_ERROR"}</li>
 *   <li>{@code X-Infra-Original-Topic} — source topic name</li>
 *   <li>{@code X-Infra-Exception-Message} — human-readable error description</li>
 * </ul>
 *
 * <h3>Integration</h3>
 * Wired automatically by {@link org.infra.kafka.retry.RetryTopicConfig}.
 * Consumer projects do not need to configure this class directly.
 *
 * @see DefaultKafkaErrorHandler
 */
@Slf4j
public class DeserializationErrorHandler {

    static final String HEADER_DLQ_REASON         = "X-Infra-DLQ-Reason";
    static final String HEADER_ORIGINAL_TOPIC      = "X-Infra-Original-Topic";
    static final String HEADER_EXCEPTION_MESSAGE   = "X-Infra-Exception-Message";

    private final DeadLetterPublishingRecoverer recoverer;
    private final String dlqSuffix;

    /**
     * @param kafkaTemplate the template used to publish to DLQ topics
     * @param dlqSuffix     suffix appended to the original topic to form the DLQ topic name
     */
    public DeserializationErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                       String dlqSuffix) {
        this.dlqSuffix = dlqSuffix;

        // Route to <topic><dlqSuffix>; preserve original partition for ordering
        this.recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    String dlqTopic = record.topic() + dlqSuffix;
                    log.error("[infra-kafka] DESERIALIZATION FAILED → DLQ topic={} " +
                                    "partition={} offset={} exception={}",
                            dlqTopic, record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(dlqTopic, record.partition());
                }
        );
    }

    /**
     * Checks whether the given consumer record carries a deserialization failure
     * embedded by {@link org.springframework.kafka.support.serializer.ErrorHandlingDeserializer}.
     *
     * @param record the record to inspect
     * @return {@code true} if this record contains a deserialization error
     */
    public boolean isDeserializationError(ConsumerRecord<?, ?> record) {
        return record.value() instanceof DeserializationException
                || record.key() instanceof DeserializationException;
    }

    /**
     * Routes the failed record straight to the DLQ, enriching headers with
     * diagnostic metadata such as the original topic and the exception message.
     *
     * @param record    the consumer record that failed deserialization
     * @param exception the deserialization exception (may be wrapped)
     */
    public void handle(ConsumerRecord<?, ?> record, Exception exception) {
        String originalTopic = record.topic();
        String exMessage = exception.getMessage() != null
                ? exception.getMessage()
                : exception.getClass().getName();

        log.error("[infra-kafka] Routing deserialization failure to DLQ — " +
                        "originTopic={}{} partition={} offset={} error={}",
                originalTopic, dlqSuffix,
                record.partition(), record.offset(), exMessage);

        // Enrich the record headers before publishing to DLQ
        record.headers()
                .add(HEADER_DLQ_REASON,
                        "DESERIALIZATION_ERROR".getBytes(StandardCharsets.UTF_8))
                .add(HEADER_ORIGINAL_TOPIC,
                        originalTopic.getBytes(StandardCharsets.UTF_8))
                .add(HEADER_EXCEPTION_MESSAGE,
                        exMessage.getBytes(StandardCharsets.UTF_8));

        recoverer.accept(record, exception);
    }
}

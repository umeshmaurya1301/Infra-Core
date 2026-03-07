package org.infra.kafka.retry;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Utility service that provides structured handling for messages consumed from
 * a Dead Letter Queue (DLQ) topic.
 *
 * <p>This class serves two purposes:
 * <ol>
 *   <li><b>Alert &amp; log</b> — every DLQ message is logged at {@code ERROR} level with
 *       all diagnostic headers extracted for easy searchability in log aggregation systems.</li>
 *   <li><b>Manual replay</b> — the {@link #replay(ConsumerRecord)} method republishes
 *       the original message to its source topic so it can be reprocessed after the
 *       underlying issue has been resolved.</li>
 * </ol>
 *
 * <h3>How to listen to a DLQ topic</h3>
 * <pre>{@code
 * @Component
 * public class OrderDlqConsumer {
 *
 *     private final DlqHandler dlqHandler;
 *
 *     @InfraKafkaListener(topics = "order-events-dlq", groupId = "order-service-dlq")
 *     public void handleDlq(ConsumerRecord<String, Object> record) {
 *         dlqHandler.handle(record);
 *         // optionally: dlqHandler.replay(record) to republish to the original topic
 *     }
 * }
 * }</pre>
 *
 * <h3>DLQ Headers Reference</h3>
 * <ul>
 *   <li>{@code kafka_dlt-original-topic} — source topic</li>
 *   <li>{@code kafka_dlt-original-partition} — source partition (integer bytes)</li>
 *   <li>{@code kafka_dlt-original-offset} — source offset (long bytes)</li>
 *   <li>{@code kafka_dlt-exception-fqcn} — fully-qualified exception class name</li>
 *   <li>{@code kafka_dlt-exception-message} — exception message</li>
 *   <li>{@code X-Infra-DLQ-Reason} — library-level classification</li>
 * </ul>
 */
@Slf4j
public class DlqHandler {

    // Header names set by Spring Kafka's DeadLetterPublishingRecoverer
    private static final String DLT_ORIGINAL_TOPIC     = "kafka_dlt-original-topic";
    private static final String DLT_EXCEPTION_FQCN     = "kafka_dlt-exception-fqcn";
    private static final String DLT_EXCEPTION_MESSAGE  = "kafka_dlt-exception-message";

    // Library-level header (set by DeserializationErrorHandler)
    private static final String INFRA_DLQ_REASON       = "X-Infra-DLQ-Reason";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final InfraKafkaProperties properties;

    public DlqHandler(KafkaTemplate<String, Object> kafkaTemplate,
                      InfraKafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Processes a record consumed from a DLQ topic.
     *
     * <p>Logs all available diagnostic headers at {@code ERROR} level and optionally
     * emits metrics (Phase 4). Consumer applications call this from their
     * {@code @InfraKafkaListener} on the {@code *-dlq} topic.
     *
     * @param record the DLQ record
     */
    public void handle(ConsumerRecord<String, Object> record) {
        String originalTopic   = headerValue(record, DLT_ORIGINAL_TOPIC, "unknown");
        String exceptionClass  = headerValue(record, DLT_EXCEPTION_FQCN, "unknown");
        String exceptionMsg    = headerValue(record, DLT_EXCEPTION_MESSAGE, "unknown");
        String dlqReason       = headerValue(record, INFRA_DLQ_REASON, "BUSINESS_LOGIC_ERROR");

        log.error("[infra-kafka] DLQ MESSAGE RECEIVED — " +
                        "dlqTopic={} originalTopic={} partition={} offset={} key={} " +
                        "reason={} exceptionClass={} exceptionMsg={}",
                record.topic(),
                originalTopic,
                record.partition(),
                record.offset(),
                record.key(),
                dlqReason,
                exceptionClass,
                exceptionMsg);
    }

    /**
     * Replays a DLQ record back to its original topic.
     *
     * <p><b>Warning:</b> Only replay messages after the underlying root cause has been
     * resolved. Replaying without fixing the bug will just cycle the message back
     * into the DLQ.
     *
     * @param record the DLQ record to replay
     */
    public void replay(ConsumerRecord<String, Object> record) {
        String originalTopic = headerValue(record, DLT_ORIGINAL_TOPIC, null);

        if (originalTopic == null || originalTopic.isBlank()) {
            log.error("[infra-kafka] Cannot replay — original topic header missing from DLQ record " +
                            "dlqTopic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        String key     = record.key();
        Object payload = record.value();

        log.info("[infra-kafka] Replaying DLQ message → originalTopic={} key={}", originalTopic, key);
        kafkaTemplate.send(originalTopic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[infra-kafka] Replay FAILED → originalTopic={} key={} error={}",
                                originalTopic, key, ex.getMessage(), ex);
                    } else {
                        log.info("[infra-kafka] Replay OK → originalTopic={} key={} partition={} offset={}",
                                originalTopic, key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String headerValue(ConsumerRecord<?, ?> record, String headerName, String defaultValue) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null) {
            return defaultValue;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}

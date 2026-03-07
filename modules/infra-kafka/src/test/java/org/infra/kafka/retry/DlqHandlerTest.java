package org.infra.kafka.retry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqHandler Unit Tests")
class DlqHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private DlqHandler dlqHandler;

    private InfraKafkaProperties defaultProperties() {
        return new InfraKafkaProperties();
    }

    @BeforeEach
    void setUp() {
        dlqHandler = new DlqHandler(kafkaTemplate, defaultProperties());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // handle()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handle() does not throw when all headers are present")
    void handle_doesNotThrowWhenHeadersPresent() {
        ConsumerRecord<String, Object> record = buildDlqRecord(
                "order-events-dlq",
                "order-events",
                "order-1",
                "payload",
                "BUSINESS_LOGIC_ERROR",
                "com.example.OrderException",
                "Order not found"
        );

        // Should complete without exception
        dlqHandler.handle(record);
    }

    @Test
    @DisplayName("handle() does not throw when headers are absent")
    void handle_doesNotThrowWhenHeadersMissing() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events-dlq", 0, 200L, "order-1", "payload");

        dlqHandler.handle(record);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // replay()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("replay() publishes to original topic when header is present")
    void replay_publishesToOriginalTopic() {
        String originalTopic = "order-events";
        ConsumerRecord<String, Object> record = buildDlqRecord(
                "order-events-dlq",
                originalTopic,
                "order-1",
                "payload",
                null, null, null
        );

        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(
                mock(SendResult.class));
        when(kafkaTemplate.send(eq(originalTopic), eq("order-1"), anyString()))
                .thenReturn(future);

        dlqHandler.replay(record);

        verify(kafkaTemplate, times(1)).send(eq(originalTopic), eq("order-1"), anyString());
    }

    @Test
    @DisplayName("replay() does NOT publish when original topic header is missing")
    void replay_doesNotPublishWhenOriginalTopicMissing() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events-dlq", 0, 200L, "order-1", "payload");

        dlqHandler.replay(record);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("replay() does NOT publish when original topic header is blank")
    void replay_doesNotPublishWhenOriginalTopicBlank() {
        ConsumerRecord<String, Object> record = buildDlqRecord(
                "order-events-dlq",
                "   ",  // blank
                "order-1",
                "payload",
                null, null, null
        );

        dlqHandler.replay(record);

        verifyNoInteractions(kafkaTemplate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a ConsumerRecord with headers pre-populated to simulate a DLQ record.
     */
    private ConsumerRecord<String, Object> buildDlqRecord(String dlqTopic,
                                                          String originalTopic,
                                                          String key,
                                                          Object value,
                                                          String dlqReason,
                                                          String exceptionFqcn,
                                                          String exceptionMessage) {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(dlqTopic, 0, 200L, key, value);

        if (originalTopic != null) {
            record.headers().add("kafka_dlt-original-topic",
                    originalTopic.getBytes(StandardCharsets.UTF_8));
        }
        if (dlqReason != null) {
            record.headers().add("X-Infra-DLQ-Reason",
                    dlqReason.getBytes(StandardCharsets.UTF_8));
        }
        if (exceptionFqcn != null) {
            record.headers().add("kafka_dlt-exception-fqcn",
                    exceptionFqcn.getBytes(StandardCharsets.UTF_8));
        }
        if (exceptionMessage != null) {
            record.headers().add("kafka_dlt-exception-message",
                    exceptionMessage.getBytes(StandardCharsets.UTF_8));
        }

        return record;
    }
}

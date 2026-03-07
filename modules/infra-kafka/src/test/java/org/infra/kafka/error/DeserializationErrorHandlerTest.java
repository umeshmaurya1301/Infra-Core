package org.infra.kafka.error;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.DeserializationException;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeserializationErrorHandler Unit Tests")
class DeserializationErrorHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private DeserializationErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DeserializationErrorHandler(kafkaTemplate, "-dlq");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isDeserializationError()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isDeserializationError() returns true when value is DeserializationException")
    void isDeserializationError_trueWhenValueIsDeserialisationException() {
        DeserializationException deserEx =
                new DeserializationException("bad json", new byte[0], false, new RuntimeException("parse error"));

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events", 0, 100L, "key-1", deserEx);

        assertThat(handler.isDeserializationError(record)).isTrue();
    }

    @Test
    @DisplayName("isDeserializationError() returns true when key is DeserializationException")
    void isDeserializationError_trueWhenKeyIsDeserialisationException() {
        DeserializationException deserEx =
                new DeserializationException("bad key", new byte[0], true, new RuntimeException("key error"));

        ConsumerRecord<Object, String> record = new ConsumerRecord<>(
                "order-events", 0, 100L, deserEx, "payload");

        assertThat(handler.isDeserializationError(record)).isTrue();
    }

    @Test
    @DisplayName("isDeserializationError() returns false for normal records")
    void isDeserializationError_falseForNormalRecord() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events", 0, 100L, "key-1", "{\"id\":\"1\"}");

        assertThat(handler.isDeserializationError(record)).isFalse();
    }

    @Test
    @DisplayName("isDeserializationError() returns false when value is null")
    void isDeserializationError_falseWhenValueIsNull() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "order-events", 0, 100L, "key-1", null);

        assertThat(handler.isDeserializationError(record)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header constants
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HEADER_DLQ_REASON constant has expected value")
    void headerDlqReason_hasExpectedValue() {
        assertThat(DeserializationErrorHandler.HEADER_DLQ_REASON)
                .isEqualTo("X-Infra-DLQ-Reason");
    }

    @Test
    @DisplayName("HEADER_ORIGINAL_TOPIC constant has expected value")
    void headerOriginalTopic_hasExpectedValue() {
        assertThat(DeserializationErrorHandler.HEADER_ORIGINAL_TOPIC)
                .isEqualTo("X-Infra-Original-Topic");
    }

    @Test
    @DisplayName("HEADER_EXCEPTION_MESSAGE constant has expected value")
    void headerExceptionMessage_hasExpectedValue() {
        assertThat(DeserializationErrorHandler.HEADER_EXCEPTION_MESSAGE)
                .isEqualTo("X-Infra-Exception-Message");
    }
}

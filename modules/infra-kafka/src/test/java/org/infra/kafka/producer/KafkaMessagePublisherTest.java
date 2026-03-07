package org.infra.kafka.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaMessagePublisher Unit Tests")
class KafkaMessagePublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaMessagePublisher publisher;
    private InfraKafkaProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InfraKafkaProperties();
        properties.getLogging().setEnabled(false); // quieten log output in tests
        publisher = new KafkaMessagePublisher(kafkaTemplate, properties);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // send(topic, key, payload)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() delegates to KafkaTemplate and returns a CompletableFuture")
    void send_delegatesToTemplate() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        CompletableFuture<SendResult<String, Object>> result =
                publisher.send("order-events", "order-123", "payload");

        // Assert
        assertThat(result).isSameAs(future);
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("send() creates a ProducerRecord with the correct topic and key")
    void send_createsRecordWithCorrectTopicAndKey() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        // Act
        publisher.send("order-events", "order-123", "my-payload");

        // Assert
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> captured = captor.getValue();
        assertThat(captured.topic()).isEqualTo("order-events");
        assertThat(captured.key()).isEqualTo("order-123");
        assertThat(captured.value()).isEqualTo("my-payload");
    }

    @Test
    @DisplayName("send() stamps X-Infra-Event-Type header automatically")
    void send_stampsEventTypeHeader() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        // Act
        publisher.send("order-events", "key-1", "StringPayload");

        // Assert
        verify(kafkaTemplate).send(captor.capture());
        var headers = captor.getValue().headers();
        assertThat(headers.lastHeader("X-Infra-Event-Type")).isNotNull();
        assertThat(new String(headers.lastHeader("X-Infra-Event-Type").value()))
                .isEqualTo("String");
    }

    @Test
    @DisplayName("send() with headerConsumer applies extra headers")
    void send_withHeaderConsumer_appliesExtraHeaders() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        // Act
        publisher.send("order-events", "key-1", "payload",
                headers -> headers.add("X-Correlation-Id", "corr-42".getBytes()));

        // Assert
        verify(kafkaTemplate).send(captor.capture());
        var headers = captor.getValue().headers();
        assertThat(headers.lastHeader("X-Correlation-Id")).isNotNull();
        assertThat(new String(headers.lastHeader("X-Correlation-Id").value()))
                .isEqualTo("corr-42");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendAndWait(topic, key, payload)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendAndWait() blocks and returns SendResult on success")
    void sendAndWait_returnsResult_onSuccess() throws Exception {
        // Arrange
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("order-events", 0), 0, 0, 0, 0, 0);
        ProducerRecord<String, Object> rec = new ProducerRecord<>("order-events", "k", "v");
        SendResult<String, Object> sendResult = new SendResult<>(rec, metadata);

        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        SendResult<String, Object> result = publisher.sendAndWait("order-events", "k", "v");

        // Assert
        assertThat(result).isSameAs(sendResult);
        assertThat(result.getRecordMetadata().topic()).isEqualTo("order-events");
        assertThat(result.getRecordMetadata().partition()).isZero();
    }

    @Test
    @DisplayName("sendAndWait() wraps exception in RuntimeException on failure")
    void sendAndWait_wrapsException_onFailure() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        assertThatThrownBy(() -> publisher.sendAndWait("order-events", "k", "v"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Send failed on topic=order-events");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // send(topic, partition, key, payload)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() with explicit partition targets the correct partition")
    void send_withPartition_targetsCorrectPartition() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        // Act
        publisher.send("order-events", 2, "key-1", "payload");

        // Assert
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().partition()).isEqualTo(2);
    }

    @Test
    @DisplayName("send() with null payload should not add event-type header")
    void send_withNullPayload_doesNotAddEventTypeHeader() {
        // Arrange
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        ArgumentCaptor<ProducerRecord<String, Object>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);

        // Act
        publisher.send("order-events", "key-1", null);

        // Assert
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().headers().lastHeader("X-Infra-Event-Type")).isNull();
    }
}

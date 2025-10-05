package org.infra.messaging.kafka;

import org.infra.messaging.properties.KafkaProperties;
import org.infra.messaging.properties.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private MessagingProperties messagingProperties;

    @Mock
    private KafkaDlqHandler dlqHandler;

    private KafkaProducerService kafkaProducerService;

    @BeforeEach
    void setUp() {
        // Create real properties objects to avoid unnecessary stubbing
        MessagingProperties realProps = new MessagingProperties();
        realProps.getKafka().getDlq().setEnabled(false); // Disable DLQ for tests
        
        kafkaProducerService = new KafkaProducerService(kafkaTemplate, realProps, dlqHandler);
    }

    @Test
    void shouldSendMessageAsync() {
        // Given
        String topic = "test-topic";
        Object message = "test-message";
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        
        when(kafkaTemplate.send(eq(topic), eq((String) null), eq(message))).thenReturn(future);

        // When
        kafkaProducerService.sendAsync(topic, message);

        // Then
        verify(kafkaTemplate).send(topic, null, message);
    }

    @Test
    void shouldSendMessageWithKeyAsync() {
        // Given
        String topic = "test-topic";
        String key = "test-key";
        Object message = "test-message";
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(null);
        
        when(kafkaTemplate.send(eq(topic), eq(key), eq(message))).thenReturn(future);

        // When
        kafkaProducerService.sendAsync(topic, key, message);

        // Then
        verify(kafkaTemplate).send(topic, key, message);
    }
}

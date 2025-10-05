package org.infra.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.exception.KafkaMessagingException;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for producing messages to Kafka topics
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "infra.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessagingProperties messagingProperties;
    private final KafkaDlqHandler dlqHandler;

    /**
     * Send message to Kafka topic asynchronously
     */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, Object message) {
        return sendAsync(topic, null, message);
    }

    /**
     * Send message to Kafka topic with key asynchronously
     */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, String key, Object message) {
        log.debug("Sending message to topic: {} with key: {}", topic, key);
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, message);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Failed to send message to topic: {} with key: {}", topic, key, throwable);
                handleSendFailure(topic, key, message, throwable);
            } else {
                log.debug("Successfully sent message to topic: {} at offset: {}", 
                         topic, result.getRecordMetadata().offset());
            }
        });
        
        return future;
    }

    /**
     * Send message to Kafka topic synchronously
     */
    public SendResult<String, Object> sendSync(String topic, Object message) {
        return sendSync(topic, null, message);
    }

    /**
     * Send message to Kafka topic with key synchronously
     */
    public SendResult<String, Object> sendSync(String topic, String key, Object message) {
        log.debug("Sending message synchronously to topic: {} with key: {}", topic, key);
        
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, message).get();
            log.debug("Successfully sent message to topic: {} at offset: {}", 
                     topic, result.getRecordMetadata().offset());
            return result;
        } catch (Exception e) {
            log.error("Failed to send message synchronously to topic: {} with key: {}", topic, key, e);
            handleSendFailure(topic, key, message, e);
            throw new KafkaMessagingException("Failed to send message synchronously", topic, e);
        }
    }

    /**
     * Send message with partition
     */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, Integer partition, String key, Object message) {
        log.debug("Sending message to topic: {}, partition: {} with key: {}", topic, partition, key);
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, partition, key, message);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Failed to send message to topic: {}, partition: {} with key: {}", topic, partition, key, throwable);
                handleSendFailure(topic, key, message, throwable);
            } else {
                log.debug("Successfully sent message to topic: {}, partition: {} at offset: {}", 
                         topic, partition, result.getRecordMetadata().offset());
            }
        });
        
        return future;
    }

    /**
     * Send message with timestamp
     */
    public CompletableFuture<SendResult<String, Object>> sendAsync(String topic, String key, Object message, Long timestamp) {
        log.debug("Sending message to topic: {} with key: {} and timestamp: {}", topic, key, timestamp);
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, null, timestamp, key, message);
        
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Failed to send message to topic: {} with key: {} and timestamp: {}", topic, key, timestamp, throwable);
                handleSendFailure(topic, key, message, throwable);
            } else {
                log.debug("Successfully sent message to topic: {} at offset: {} with timestamp: {}", 
                         topic, result.getRecordMetadata().offset(), timestamp);
            }
        });
        
        return future;
    }

    /**
     * Send multiple messages in batch
     */
    public void sendBatch(String topic, java.util.List<Object> messages) {
        log.debug("Sending batch of {} messages to topic: {}", messages.size(), topic);
        
        messages.forEach(message -> sendAsync(topic, message));
    }

    /**
     * Send multiple messages with keys in batch
     */
    public void sendBatch(String topic, java.util.Map<String, Object> keyMessagePairs) {
        log.debug("Sending batch of {} messages to topic: {}", keyMessagePairs.size(), topic);
        
        keyMessagePairs.forEach((key, message) -> sendAsync(topic, key, message));
    }

    private void handleSendFailure(String topic, String key, Object message, Throwable throwable) {
        if (messagingProperties.getKafka().getDlq().isEnabled()) {
            try {
                dlqHandler.sendToDlqWithRetry(topic, key, message, 
                    throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ after send failure", dlqException);
            }
        }
    }
}

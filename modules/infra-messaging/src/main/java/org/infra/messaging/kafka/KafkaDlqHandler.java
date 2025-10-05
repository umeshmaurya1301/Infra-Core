package org.infra.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.infra.messaging.exception.KafkaMessagingException;
import org.infra.messaging.properties.KafkaProperties;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Dead Letter Queue operations for Kafka
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaDlqHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MessagingProperties messagingProperties;

    /**
     * Send message to Dead Letter Queue
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendToDlq(ConsumerRecord<?, ?> failedRecord, Exception exception) {
        KafkaProperties.DlqProperties dlqProps = messagingProperties.getKafka().getDlq();
        
        if (!dlqProps.isEnabled()) {
            log.warn("DLQ is disabled. Skipping DLQ send for topic: {}", failedRecord.topic());
            return;
        }

        String originalTopic = failedRecord.topic();
        String dlqTopic = originalTopic + dlqProps.getTopicSuffix();
        
        try {
            // Create DLQ message with metadata
            Map<String, Object> dlqMessage = createDlqMessage(failedRecord, exception);
            
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                dlqTopic, 
                failedRecord.key() != null ? failedRecord.key().toString() : null,
                dlqMessage
            );
            
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send message to DLQ topic: {}", dlqTopic, throwable);
                    throw new KafkaMessagingException("Failed to send to DLQ", dlqTopic, throwable);
                } else {
                    log.info("Successfully sent message to DLQ topic: {} at offset: {}", 
                            dlqTopic, result.getRecordMetadata().offset());
                }
            });
            
        } catch (Exception e) {
            log.error("Error sending message to DLQ topic: {}", dlqTopic, e);
            throw new KafkaMessagingException("Failed to send to DLQ", dlqTopic, e);
        }
    }

    /**
     * Send message to DLQ with retry logic
     */
    public void sendToDlqWithRetry(String topic, String key, Object message, Exception exception) {
        KafkaProperties.DlqProperties dlqProps = messagingProperties.getKafka().getDlq();
        
        if (!dlqProps.isEnabled()) {
            log.warn("DLQ is disabled. Skipping DLQ send for topic: {}", topic);
            return;
        }

        String dlqTopic = topic + dlqProps.getTopicSuffix();
        
        int maxRetries = dlqProps.getMaxRetries();
        Duration retryInterval = dlqProps.getRetryInterval();
        double backoffMultiplier = dlqProps.getBackoffMultiplier();
        Duration maxBackoffInterval = dlqProps.getMaxBackoffInterval();
        
        int attempt = 0;
        Duration currentBackoff = retryInterval;
        
        while (attempt < maxRetries) {
            try {
                Map<String, Object> dlqMessage = createDlqMessage(topic, key, message, exception, attempt);
                
                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(dlqTopic, key, dlqMessage);
                
                // Wait for completion
                SendResult<String, Object> result = future.get();
                log.info("Successfully sent message to DLQ topic: {} at offset: {} after {} attempts", 
                        dlqTopic, result.getRecordMetadata().offset(), attempt + 1);
                return;
                
            } catch (Exception e) {
                attempt++;
                log.warn("Attempt {} failed to send message to DLQ topic: {}", attempt, dlqTopic, e);
                
                if (attempt >= maxRetries) {
                    log.error("Failed to send message to DLQ after {} attempts", maxRetries);
                    throw new KafkaMessagingException("Failed to send to DLQ after retries", dlqTopic, e);
                }
                
                try {
                    Thread.sleep(currentBackoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new KafkaMessagingException("Interrupted while retrying DLQ send", dlqTopic, ie);
                }
                
                // Calculate next backoff
                currentBackoff = Duration.ofMillis(
                    Math.min(
                        (long) (currentBackoff.toMillis() * backoffMultiplier),
                        maxBackoffInterval.toMillis()
                    )
                );
            }
        }
    }

    private Map<String, Object> createDlqMessage(ConsumerRecord<?, ?> failedRecord, Exception exception) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalTopic", failedRecord.topic());
        dlqMessage.put("originalPartition", failedRecord.partition());
        dlqMessage.put("originalOffset", failedRecord.offset());
        dlqMessage.put("originalKey", failedRecord.key());
        dlqMessage.put("originalValue", failedRecord.value());
        dlqMessage.put("originalTimestamp", failedRecord.timestamp());
        dlqMessage.put("failureTimestamp", LocalDateTime.now().toString());
        dlqMessage.put("exceptionMessage", exception.getMessage());
        dlqMessage.put("exceptionClass", exception.getClass().getSimpleName());
        
        // Add headers if present
        if (failedRecord.headers() != null && failedRecord.headers().iterator().hasNext()) {
            Map<String, String> headers = new HashMap<>();
            failedRecord.headers().forEach(header -> 
                headers.put(header.key(), new String(header.value()))
            );
            dlqMessage.put("originalHeaders", headers);
        }
        
        return dlqMessage;
    }

    private Map<String, Object> createDlqMessage(String topic, String key, Object message, 
                                               Exception exception, int attempt) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalTopic", topic);
        dlqMessage.put("originalKey", key);
        dlqMessage.put("originalValue", message);
        dlqMessage.put("failureTimestamp", LocalDateTime.now().toString());
        dlqMessage.put("exceptionMessage", exception.getMessage());
        dlqMessage.put("exceptionClass", exception.getClass().getSimpleName());
        dlqMessage.put("retryAttempt", attempt);
        
        return dlqMessage;
    }
}

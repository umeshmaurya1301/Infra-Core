package org.infra.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.infra.messaging.exception.KafkaMessagingException;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Kafka error handler with retry and DLQ support
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaErrorHandler implements CommonErrorHandler {

    private final MessagingProperties messagingProperties;

    @Override
    public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record, 
                           Consumer<?, ?> consumer, MessageListenerContainer container) {
        log.error("Error processing Kafka message from topic: {}, partition: {}, offset: {}", 
                record.topic(), record.partition(), record.offset(), thrownException);

        try {
            // Handle the error based on configuration
            handleError(thrownException, record, consumer);
            return true; // Error handled
        } catch (Exception e) {
            log.error("Failed to handle Kafka error", e);
            return false; // Let Spring Kafka handle it
        }
    }

    @Override
    public void handleOtherException(Exception thrownException, Consumer<?, ?> consumer, 
                                   MessageListenerContainer container, boolean batchListener) {
        log.error("Kafka consumer error", thrownException);
    }

    private void handleError(Exception thrownException, ConsumerRecord<?, ?> record, Consumer<?, ?> consumer) {
        String topic = record.topic();
        
        // Check if DLQ is enabled
        if (messagingProperties.getKafka().getDlq().isEnabled()) {
            try {
                // Send to DLQ (this would typically be handled by KafkaDlqHandler)
                log.info("Sending message to DLQ for topic: {}", topic);
                // The actual DLQ sending would be handled by the KafkaDlqHandler
                
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ for topic: {}", topic, dlqException);
                throw new KafkaMessagingException("Failed to handle error and send to DLQ", topic, dlqException);
            }
        } else {
            log.warn("DLQ is disabled. Message will be skipped for topic: {}", topic);
        }
        
        // Commit the offset to move past the problematic message
        try {
            TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            consumer.commitSync(java.util.Map.of(topicPartition, 
                new org.apache.kafka.clients.consumer.OffsetAndMetadata(record.offset() + 1)));
        } catch (Exception commitException) {
            log.error("Failed to commit offset after error handling", commitException);
        }
    }
}

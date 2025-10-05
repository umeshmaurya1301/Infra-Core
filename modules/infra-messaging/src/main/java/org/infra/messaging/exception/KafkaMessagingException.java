package org.infra.messaging.exception;

/**
 * Exception for Kafka-specific messaging operations
 */
public class KafkaMessagingException extends MessagingException {
    
    private final String topic;
    
    public KafkaMessagingException(String message, String topic) {
        super(message);
        this.topic = topic;
    }
    
    public KafkaMessagingException(String message, String topic, Throwable cause) {
        super(message, cause);
        this.topic = topic;
    }
    
    public String getTopic() {
        return topic;
    }
}

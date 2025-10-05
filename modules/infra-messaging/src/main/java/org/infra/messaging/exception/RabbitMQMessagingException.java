package org.infra.messaging.exception;

/**
 * Exception for RabbitMQ-specific messaging operations
 */
public class RabbitMQMessagingException extends MessagingException {
    
    private final String exchange;
    private final String routingKey;
    
    public RabbitMQMessagingException(String message, String exchange, String routingKey) {
        super(message);
        this.exchange = exchange;
        this.routingKey = routingKey;
    }
    
    public RabbitMQMessagingException(String message, String exchange, String routingKey, Throwable cause) {
        super(message, cause);
        this.exchange = exchange;
        this.routingKey = routingKey;
    }
    
    public String getExchange() {
        return exchange;
    }
    
    public String getRoutingKey() {
        return routingKey;
    }
}

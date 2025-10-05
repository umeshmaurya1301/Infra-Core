package org.infra.messaging.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.exception.RabbitMQMessagingException;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service for producing messages to RabbitMQ exchanges
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "infra.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitProducerService {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;
    private final RabbitDlqHandler dlqHandler;

    /**
     * Send message to default exchange with routing key
     */
    public void send(String routingKey, Object message) {
        String defaultExchange = messagingProperties.getRabbitmq().getExchange().getDefaultName();
        send(defaultExchange, routingKey, message);
    }

    /**
     * Send message to specific exchange with routing key
     */
    public void send(String exchange, String routingKey, Object message) {
        log.debug("Sending message to exchange: {} with routing key: {}", exchange, routingKey);
        
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.debug("Successfully sent message to exchange: {} with routing key: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send message to exchange: {} with routing key: {}", exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send message", exchange, routingKey, e);
        }
    }

    /**
     * Send message with custom headers
     */
    public void send(String exchange, String routingKey, Object message, Map<String, Object> headers) {
        log.debug("Sending message with headers to exchange: {} with routing key: {}", exchange, routingKey);
        
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, messagePostProcessor -> {
                MessageProperties props = messagePostProcessor.getMessageProperties();
                if (headers != null) {
                    headers.forEach((key, value) -> props.setHeader(key, value));
                }
                props.setTimestamp(new java.util.Date());
                return messagePostProcessor;
            });
            log.debug("Successfully sent message with headers to exchange: {} with routing key: {}", 
                     exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send message with headers to exchange: {} with routing key: {}", 
                     exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send message with headers", exchange, routingKey, e);
        }
    }

    /**
     * Send message with priority
     */
    public void send(String exchange, String routingKey, Object message, int priority) {
        log.debug("Sending message with priority {} to exchange: {} with routing key: {}", 
                 priority, exchange, routingKey);
        
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, messagePostProcessor -> {
                MessageProperties props = messagePostProcessor.getMessageProperties();
                props.setPriority(priority);
                props.setTimestamp(new java.util.Date());
                return messagePostProcessor;
            });
            log.debug("Successfully sent message with priority to exchange: {} with routing key: {}", 
                     exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send message with priority to exchange: {} with routing key: {}", 
                     exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send message with priority", exchange, routingKey, e);
        }
    }

    /**
     * Send message with expiration (TTL)
     */
    public void send(String exchange, String routingKey, Object message, long ttlMillis) {
        log.debug("Sending message with TTL {} to exchange: {} with routing key: {}", 
                 ttlMillis, exchange, routingKey);
        
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, messagePostProcessor -> {
                MessageProperties props = messagePostProcessor.getMessageProperties();
                props.setExpiration(String.valueOf(ttlMillis));
                props.setTimestamp(new java.util.Date());
                return messagePostProcessor;
            });
            log.debug("Successfully sent message with TTL to exchange: {} with routing key: {}", 
                     exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send message with TTL to exchange: {} with routing key: {}", 
                     exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send message with TTL", exchange, routingKey, e);
        }
    }

    /**
     * Send raw message
     */
    public void send(String exchange, String routingKey, Message message) {
        log.debug("Sending raw message to exchange: {} with routing key: {}", exchange, routingKey);
        
        try {
            rabbitTemplate.send(exchange, routingKey, message);
            log.debug("Successfully sent raw message to exchange: {} with routing key: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send raw message to exchange: {} with routing key: {}", exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send raw message", exchange, routingKey, e);
        }
    }

    /**
     * Send multiple messages in batch
     */
    public void sendBatch(String exchange, String routingKey, java.util.List<Object> messages) {
        log.debug("Sending batch of {} messages to exchange: {} with routing key: {}", 
                 messages.size(), exchange, routingKey);
        
        messages.forEach(message -> {
            try {
                send(exchange, routingKey, message);
            } catch (Exception e) {
                log.error("Failed to send message in batch", e);
                // Continue with other messages
            }
        });
    }

    /**
     * Send multiple messages with different routing keys
     */
    public void sendBatch(String exchange, Map<String, Object> routingKeyMessagePairs) {
        log.debug("Sending batch of {} messages to exchange: {}", routingKeyMessagePairs.size(), exchange);
        
        routingKeyMessagePairs.forEach((routingKey, message) -> {
            try {
                send(exchange, routingKey, message);
            } catch (Exception e) {
                log.error("Failed to send message in batch for routing key: {}", routingKey, e);
                // Continue with other messages
            }
        });
    }

    /**
     * Send and receive (RPC pattern)
     */
    public Object sendAndReceive(String exchange, String routingKey, Object message) {
        log.debug("Sending RPC message to exchange: {} with routing key: {}", exchange, routingKey);
        
        try {
            Object response = rabbitTemplate.convertSendAndReceive(exchange, routingKey, message);
            log.debug("Successfully received RPC response from exchange: {} with routing key: {}", 
                     exchange, routingKey);
            return response;
        } catch (Exception e) {
            log.error("Failed to send RPC message to exchange: {} with routing key: {}", exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send RPC message", exchange, routingKey, e);
        }
    }

    /**
     * Send and receive with timeout (RPC pattern)
     */
    public Object sendAndReceive(String exchange, String routingKey, Object message, long timeoutMillis) {
        log.debug("Sending RPC message with timeout {} to exchange: {} with routing key: {}", 
                 timeoutMillis, exchange, routingKey);
        
        try {
            Object response = rabbitTemplate.convertSendAndReceive(exchange, routingKey, message, messagePostProcessor -> {
                messagePostProcessor.getMessageProperties().setReplyTo("temp-reply-queue");
                return messagePostProcessor;
            });
            
            log.debug("Successfully received RPC response with timeout from exchange: {} with routing key: {}", 
                     exchange, routingKey);
            return response;
        } catch (Exception e) {
            log.error("Failed to send RPC message with timeout to exchange: {} with routing key: {}", 
                     exchange, routingKey, e);
            handleSendFailure(exchange, routingKey, message, e);
            throw new RabbitMQMessagingException("Failed to send RPC message with timeout", exchange, routingKey, e);
        }
    }

    private void handleSendFailure(String exchange, String routingKey, Object message, Exception exception) {
        if (messagingProperties.getRabbitmq().getDlq().isEnabled()) {
            try {
                dlqHandler.sendToDlqWithRetry(exchange, routingKey, message, exception);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ after send failure", dlqException);
            }
        }
    }
}

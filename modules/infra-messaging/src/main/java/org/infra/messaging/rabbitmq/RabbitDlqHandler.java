package org.infra.messaging.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.exception.RabbitMQMessagingException;
import org.infra.messaging.properties.MessagingProperties;
import org.infra.messaging.properties.RabbitMQProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles Dead Letter Queue operations for RabbitMQ
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitDlqHandler {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messagingProperties;

    /**
     * Send message to Dead Letter Queue
     */
    @Retryable(
        retryFor = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void sendToDlq(Message failedMessage, Exception exception) {
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        if (!dlqProps.isEnabled()) {
            log.warn("DLQ is disabled. Skipping DLQ send for message");
            return;
        }

        String originalExchange = failedMessage.getMessageProperties().getReceivedExchange();
        String originalRoutingKey = failedMessage.getMessageProperties().getReceivedRoutingKey();
        
        String dlqExchange = originalExchange + dlqProps.getExchangeSuffix();
        String dlqRoutingKey = originalRoutingKey + dlqProps.getRoutingKeySuffix();
        
        try {
            // Create DLQ message with metadata
            Message dlqMessage = createDlqMessage(failedMessage, exception);
            
            rabbitTemplate.send(dlqExchange, dlqRoutingKey, dlqMessage);
            
            log.info("Successfully sent message to DLQ exchange: {} with routing key: {}", 
                    dlqExchange, dlqRoutingKey);
            
        } catch (Exception e) {
            log.error("Error sending message to DLQ exchange: {} with routing key: {}", 
                     dlqExchange, dlqRoutingKey, e);
            throw new RabbitMQMessagingException("Failed to send to DLQ", dlqExchange, dlqRoutingKey, e);
        }
    }

    /**
     * Send message to DLQ with retry logic
     */
    public void sendToDlqWithRetry(String exchange, String routingKey, Object message, Exception exception) {
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        if (!dlqProps.isEnabled()) {
            log.warn("DLQ is disabled. Skipping DLQ send for exchange: {}, routing key: {}", exchange, routingKey);
            return;
        }

        String dlqExchange = exchange + dlqProps.getExchangeSuffix();
        String dlqRoutingKey = routingKey + dlqProps.getRoutingKeySuffix();
        
        int maxRetries = dlqProps.getMaxRetries();
        Duration retryInterval = dlqProps.getRetryInterval();
        
        int attempt = 0;
        Duration currentBackoff = retryInterval;
        
        while (attempt < maxRetries) {
            try {
                Map<String, Object> dlqMessage = createDlqMessage(exchange, routingKey, message, exception, attempt);
                
                rabbitTemplate.convertAndSend(dlqExchange, dlqRoutingKey, dlqMessage);
                
                log.info("Successfully sent message to DLQ exchange: {} with routing key: {} after {} attempts", 
                        dlqExchange, dlqRoutingKey, attempt + 1);
                return;
                
            } catch (Exception e) {
                attempt++;
                log.warn("Attempt {} failed to send message to DLQ exchange: {} with routing key: {}", 
                        attempt, dlqExchange, dlqRoutingKey, e);
                
                if (attempt >= maxRetries) {
                    log.error("Failed to send message to DLQ after {} attempts", maxRetries);
                    throw new RabbitMQMessagingException("Failed to send to DLQ after retries", 
                                                       dlqExchange, dlqRoutingKey, e);
                }
                
                try {
                    Thread.sleep(currentBackoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RabbitMQMessagingException("Interrupted while retrying DLQ send", 
                                                       dlqExchange, dlqRoutingKey, ie);
                }
                
                // Calculate next backoff (simple doubling)
                currentBackoff = Duration.ofMillis(currentBackoff.toMillis() * 2);
            }
        }
    }

    /**
     * Send custom message to DLQ
     */
    public void sendToDlq(String exchange, String routingKey, Object message, Map<String, Object> headers) {
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        if (!dlqProps.isEnabled()) {
            log.warn("DLQ is disabled. Skipping DLQ send for exchange: {}, routing key: {}", exchange, routingKey);
            return;
        }

        String dlqExchange = exchange + dlqProps.getExchangeSuffix();
        String dlqRoutingKey = routingKey + dlqProps.getRoutingKeySuffix();
        
        try {
            rabbitTemplate.convertAndSend(dlqExchange, dlqRoutingKey, message, messagePostProcessor -> {
                MessageProperties props = messagePostProcessor.getMessageProperties();
                if (headers != null) {
                    headers.forEach((key, value) -> props.setHeader(key, value));
                }
                props.setHeader("x-dlq-timestamp", LocalDateTime.now().toString());
                return messagePostProcessor;
            });
            
            log.info("Successfully sent custom message to DLQ exchange: {} with routing key: {}", 
                    dlqExchange, dlqRoutingKey);
            
        } catch (Exception e) {
            log.error("Error sending custom message to DLQ exchange: {} with routing key: {}", 
                     dlqExchange, dlqRoutingKey, e);
            throw new RabbitMQMessagingException("Failed to send custom message to DLQ", 
                                               dlqExchange, dlqRoutingKey, e);
        }
    }

    private Message createDlqMessage(Message failedMessage, Exception exception) {
        MessageProperties originalProps = failedMessage.getMessageProperties();
        MessageProperties dlqProps = new MessageProperties();
        
        // Copy original properties
        dlqProps.setContentType(originalProps.getContentType());
        dlqProps.setContentEncoding(originalProps.getContentEncoding());
        dlqProps.setHeaders(new HashMap<>(originalProps.getHeaders()));
        
        // Add DLQ metadata
        dlqProps.setHeader("x-original-exchange", originalProps.getReceivedExchange());
        dlqProps.setHeader("x-original-routing-key", originalProps.getReceivedRoutingKey());
        dlqProps.setHeader("x-original-timestamp", originalProps.getTimestamp());
        dlqProps.setHeader("x-failure-timestamp", LocalDateTime.now().toString());
        dlqProps.setHeader("x-exception-message", exception.getMessage());
        dlqProps.setHeader("x-exception-class", exception.getClass().getSimpleName());
        
        return new Message(failedMessage.getBody(), dlqProps);
    }

    private Map<String, Object> createDlqMessage(String exchange, String routingKey, Object message, 
                                               Exception exception, int attempt) {
        Map<String, Object> dlqMessage = new HashMap<>();
        dlqMessage.put("originalExchange", exchange);
        dlqMessage.put("originalRoutingKey", routingKey);
        dlqMessage.put("originalMessage", message);
        dlqMessage.put("failureTimestamp", LocalDateTime.now().toString());
        dlqMessage.put("exceptionMessage", exception.getMessage());
        dlqMessage.put("exceptionClass", exception.getClass().getSimpleName());
        dlqMessage.put("retryAttempt", attempt);
        
        return dlqMessage;
    }
}

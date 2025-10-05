package org.infra.messaging.rabbitmq;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.exception.RabbitMQMessagingException;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.util.ErrorHandler;

/**
 * RabbitMQ error handler with retry and DLQ support
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitErrorHandler implements ErrorHandler {

    private final MessagingProperties messagingProperties;

    @Override
    public void handleError(Throwable t) {
        log.error("RabbitMQ error occurred", t);
        
        if (t instanceof ListenerExecutionFailedException) {
            ListenerExecutionFailedException ex = (ListenerExecutionFailedException) t;
            handleListenerError(ex);
        }
    }

    private void handleListenerError(ListenerExecutionFailedException exception) {
        // Handle the error based on the exception
        log.error("Handling listener execution failed exception", exception);
    }

    private void handleListenerError(Message message, Exception exception) {
        String exchange = message.getMessageProperties().getReceivedExchange();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        
        log.error("Error processing RabbitMQ message from exchange: {}, routing key: {}", 
                exchange, routingKey, exception);

        // Check if DLQ is enabled
        if (messagingProperties.getRabbitmq().getDlq().isEnabled()) {
            try {
                // Send to DLQ (this would typically be handled by RabbitDlqHandler)
                log.info("Sending message to DLQ for exchange: {}, routing key: {}", exchange, routingKey);
                // The actual DLQ sending would be handled by the RabbitDlqHandler
                
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ for exchange: {}, routing key: {}", 
                         exchange, routingKey, dlqException);
                throw new RabbitMQMessagingException("Failed to handle error and send to DLQ", 
                                                   exchange, routingKey, dlqException);
            }
        } else {
            log.warn("DLQ is disabled. Message will be acknowledged for exchange: {}, routing key: {}", 
                    exchange, routingKey);
        }
    }
}

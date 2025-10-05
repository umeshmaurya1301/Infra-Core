package org.infra.messaging.example;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.kafka.KafkaProducerService;
import org.infra.messaging.rabbitmq.RabbitProducerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Example usage of the messaging library
 * This class demonstrates how to use both Kafka and RabbitMQ features
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "infra.messaging", name = "enabled", havingValue = "true")
public class MessagingUsageExample {

    private final KafkaProducerService kafkaProducer;
    private final RabbitProducerService rabbitProducer;

    /**
     * Example method showing how to send messages to Kafka
     */
    public void sendKafkaMessages() {
        // Simple message sending
        kafkaProducer.sendAsync("user-events", "User logged in");
        
        // Message with key
        kafkaProducer.sendAsync("user-events", "user-123", "User profile updated");
        
        // Synchronous sending
        try {
            kafkaProducer.sendSync("critical-events", "System alert");
        } catch (Exception e) {
            log.error("Failed to send critical event", e);
        }
        
        // Batch sending
        kafkaProducer.sendBatch("notifications", java.util.List.of(
            "Notification 1",
            "Notification 2", 
            "Notification 3"
        ));
    }

    /**
     * Example method showing how to send messages to RabbitMQ
     */
    public void sendRabbitMessages() {
        // Simple message sending
        rabbitProducer.send("user.created", "New user registered");
        
        // Message to specific exchange
        rabbitProducer.send("user.exchange", "user.updated", "User profile changed");
        
        // Message with headers
        Map<String, Object> headers = Map.of(
            "source", "user-service",
            "version", "1.0",
            "timestamp", System.currentTimeMillis()
        );
        rabbitProducer.send("user.exchange", "user.deleted", "User account deleted", headers);
        
        // Message with priority
        rabbitProducer.send("priority.exchange", "urgent.task", "High priority task", 10);
        
        // Message with TTL
        rabbitProducer.send("temp.exchange", "temp.message", "Temporary message", 60000L); // 1 minute TTL
        
        // RPC pattern
        try {
            Object response = rabbitProducer.sendAndReceive("rpc.exchange", "calculate.sum", 
                Map.of("a", 10, "b", 20));
            log.info("RPC response: {}", response);
        } catch (Exception e) {
            log.error("RPC call failed", e);
        }
    }

    /**
     * Example Kafka message consumer
     */
    @KafkaListener(topics = "user-events")
    public void handleUserEvent(String message, Acknowledgment ack) {
        try {
            log.info("Received Kafka message: {}", message);
            
            // Process the message
            processUserEvent(message);
            
            // Acknowledge the message
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", message, e);
            // Don't acknowledge - message will be retried or sent to DLQ
            throw e;
        }
    }

    /**
     * Example Kafka message consumer with key
     */
    @KafkaListener(topics = "user-events", groupId = "user-processor-group")
    public void handleUserEventWithKey(String key, String message, Acknowledgment ack) {
        try {
            log.info("Received Kafka message with key {}: {}", key, message);
            
            // Process the message using the key
            processUserEventWithKey(key, message);
            
            // Acknowledge the message
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing Kafka message with key {}: {}", key, message, e);
            throw e;
        }
    }

    /**
     * Example RabbitMQ message consumer
     */
    @RabbitListener(queues = "user.queue")
    public void handleUserMessage(String message, @Header Map<String, Object> headers) {
        try {
            log.info("Received RabbitMQ message: {} with headers: {}", message, headers);
            
            // Process the message
            processUserMessage(message, headers);
            
        } catch (Exception e) {
            log.error("Error processing RabbitMQ message: {}", message, e);
            // Message will be retried or sent to DLQ based on configuration
            throw e;
        }
    }

    /**
     * Example RabbitMQ message consumer with manual acknowledgment
     */
    @RabbitListener(queues = "critical.queue", ackMode = "MANUAL")
    public void handleCriticalMessage(String message, 
                                    com.rabbitmq.client.Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            log.info("Received critical RabbitMQ message: {}", message);
            
            // Process the critical message
            processCriticalMessage(message);
            
            // Manually acknowledge the message
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("Error processing critical RabbitMQ message: {}", message, e);
            try {
                // Reject the message and requeue it
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ackException) {
                log.error("Failed to nack message", ackException);
            }
        }
    }

    /**
     * Example RPC message handler
     */
    @RabbitListener(queues = "rpc.queue")
    public Map<String, Integer> handleCalculationRequest(Map<String, Integer> request) {
        log.info("Received RPC calculation request: {}", request);
        
        Integer a = request.get("a");
        Integer b = request.get("b");
        
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both 'a' and 'b' must be provided");
        }
        
        int sum = a + b;
        Map<String, Integer> response = Map.of("result", sum);
        
        log.info("Returning RPC calculation response: {}", response);
        return response;
    }

    // Helper methods for processing messages
    
    private void processUserEvent(String message) {
        // Simulate processing
        log.debug("Processing user event: {}", message);
    }
    
    private void processUserEventWithKey(String key, String message) {
        // Simulate processing with key
        log.debug("Processing user event with key {}: {}", key, message);
    }
    
    private void processUserMessage(String message, Map<String, Object> headers) {
        // Simulate processing with headers
        log.debug("Processing user message: {} with headers: {}", message, headers);
    }
    
    private void processCriticalMessage(String message) {
        // Simulate critical message processing
        log.debug("Processing critical message: {}", message);
        
        // Simulate potential failure
        if (message.contains("error")) {
            throw new RuntimeException("Simulated processing error");
        }
    }
}

package org.infra.messaging.rabbitmq;

import org.infra.messaging.properties.MessagingProperties;
import org.infra.messaging.properties.RabbitMQProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitProducerServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitDlqHandler dlqHandler;

    private RabbitProducerService rabbitProducerService;

    @BeforeEach
    void setUp() {
        // Create real properties objects to avoid unnecessary stubbing
        MessagingProperties realProps = new MessagingProperties();
        realProps.getRabbitmq().getExchange().setDefaultName("default.exchange");
        realProps.getRabbitmq().getDlq().setEnabled(false); // Disable DLQ for tests
        
        rabbitProducerService = new RabbitProducerService(rabbitTemplate, realProps, dlqHandler);
    }

    @Test
    void shouldSendMessageToDefaultExchange() {
        // Given
        String routingKey = "test.routing.key";
        Object message = "test-message";

        // When
        rabbitProducerService.send(routingKey, message);

        // Then
        verify(rabbitTemplate).convertAndSend("default.exchange", routingKey, message);
    }

    @Test
    void shouldSendMessageToSpecificExchange() {
        // Given
        String exchange = "custom.exchange";
        String routingKey = "test.routing.key";
        Object message = "test-message";

        // When
        rabbitProducerService.send(exchange, routingKey, message);

        // Then
        verify(rabbitTemplate).convertAndSend(exchange, routingKey, message);
    }
}

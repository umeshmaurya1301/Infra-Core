package org.infra.messaging.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Main configuration properties for messaging module
 */
@Data
@ConfigurationProperties(prefix = "infra.messaging")
public class MessagingProperties {
    
    /**
     * Whether messaging is enabled
     */
    private boolean enabled = true;
    
    /**
     * Kafka configuration
     */
    @NestedConfigurationProperty
    private KafkaProperties kafka = new KafkaProperties();
    
    /**
     * RabbitMQ configuration
     */
    @NestedConfigurationProperty
    private RabbitMQProperties rabbitmq = new RabbitMQProperties();
}

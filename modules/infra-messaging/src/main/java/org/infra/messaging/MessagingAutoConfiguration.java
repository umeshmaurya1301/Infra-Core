package org.infra.messaging;

import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.kafka.KafkaConfiguration;
import org.infra.messaging.properties.MessagingProperties;
import org.infra.messaging.rabbitmq.RabbitMQConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for messaging infrastructure
 * 
 * This configuration is automatically loaded when the messaging library is on the classpath.
 * It conditionally enables Kafka and RabbitMQ configurations based on properties.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "infra.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
    KafkaConfiguration.class,
    RabbitMQConfiguration.class
})
public class MessagingAutoConfiguration {

    public MessagingAutoConfiguration() {
        log.info("Infra Messaging Auto-Configuration initialized");
    }
}

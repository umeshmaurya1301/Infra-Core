package org.infra.messaging.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.infra.messaging.properties.KafkaProperties;
import org.infra.messaging.properties.MessagingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for producer and consumer
 */
@Slf4j
@Configuration
@EnableKafka
@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "infra.messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaConfiguration {

    private final MessagingProperties messagingProperties;

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.kafka.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProducerFactory<String, Object> producerFactory() {
        KafkaProperties kafkaProps = messagingProperties.getKafka();
        KafkaProperties.ProducerProperties producerProps = kafkaProps.getProducer();
        
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producerProps.getKeySerializer());
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producerProps.getValueSerializer());
        
        // Performance configuration
        configProps.put(ProducerConfig.ACKS_CONFIG, producerProps.getAcks());
        configProps.put(ProducerConfig.RETRIES_CONFIG, producerProps.getRetries());
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, producerProps.getBatchSize());
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, producerProps.getLingerMs());
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, producerProps.getBufferMemory());
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) producerProps.getRequestTimeout().toMillis());
        
        // Security configuration
        addSecurityConfig(configProps, kafkaProps.getSecurity());
        
        // Additional properties
        configProps.putAll(kafkaProps.getProperties());
        configProps.putAll(producerProps.getProperties());
        
        // JSON serializer configuration
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        log.info("Kafka Producer configured with bootstrap servers: {}", kafkaProps.getBootstrapServers());
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.kafka.producer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // Set default topic if needed
        // template.setDefaultTopic("default-topic");
        
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConsumerFactory<String, Object> consumerFactory() {
        KafkaProperties kafkaProps = messagingProperties.getKafka();
        KafkaProperties.ConsumerProperties consumerProps = kafkaProps.getConsumer();
        
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic configuration
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.getBootstrapServers());
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProps.getGroupId());
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, consumerProps.getKeyDeserializer());
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, consumerProps.getValueDeserializer());
        
        // Consumer behavior configuration
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProps.getAutoOffsetReset());
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumerProps.isEnableAutoCommit());
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, (int) consumerProps.getSessionTimeout().toMillis());
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, (int) consumerProps.getHeartbeatInterval().toMillis());
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProps.getMaxPollRecords());
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, (int) consumerProps.getMaxPollInterval().toMillis());
        
        // Security configuration
        addSecurityConfig(configProps, kafkaProps.getSecurity());
        
        // Additional properties
        configProps.putAll(kafkaProps.getProperties());
        configProps.putAll(consumerProps.getProperties());
        
        // JSON deserializer configuration
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class);
        
        log.info("Kafka Consumer configured with bootstrap servers: {} and group ID: {}", 
                kafkaProps.getBootstrapServers(), consumerProps.getGroupId());
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.kafka.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        KafkaProperties.ConsumerProperties consumerProps = messagingProperties.getKafka().getConsumer();
        
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(consumerProps.getConcurrency());
        
        // Configure container properties
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Error handling
        factory.setCommonErrorHandler(kafkaErrorHandler());
        
        log.info("Kafka Listener Container Factory configured with concurrency: {}", consumerProps.getConcurrency());
        
        return factory;
    }

    @Bean
    public KafkaErrorHandler kafkaErrorHandler() {
        return new KafkaErrorHandler(messagingProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.kafka.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaDlqHandler kafkaDlqHandler() {
        return new KafkaDlqHandler(kafkaTemplate(), messagingProperties);
    }

    private void addSecurityConfig(Map<String, Object> configProps, KafkaProperties.SecurityProperties security) {
        if (security.getProtocol() != null && !"PLAINTEXT".equals(security.getProtocol())) {
            configProps.put("security.protocol", security.getProtocol());
            
            if (security.getSaslMechanism() != null) {
                configProps.put("sasl.mechanism", security.getSaslMechanism());
                
                if (security.getSaslJaasConfig() != null) {
                    configProps.put("sasl.jaas.config", security.getSaslJaasConfig());
                } else if (security.getUsername() != null && security.getPassword() != null) {
                    String jaasConfig = String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        security.getUsername(), security.getPassword()
                    );
                    configProps.put("sasl.jaas.config", jaasConfig);
                }
            }
            
            // SSL configuration
            KafkaProperties.SecurityProperties.SslProperties ssl = security.getSsl();
            if (ssl.getTruststoreLocation() != null) {
                configProps.put("ssl.truststore.location", ssl.getTruststoreLocation());
                configProps.put("ssl.truststore.password", ssl.getTruststorePassword());
            }
            if (ssl.getKeystoreLocation() != null) {
                configProps.put("ssl.keystore.location", ssl.getKeystoreLocation());
                configProps.put("ssl.keystore.password", ssl.getKeystorePassword());
                if (ssl.getKeyPassword() != null) {
                    configProps.put("ssl.key.password", ssl.getKeyPassword());
                }
            }
        }
    }
}

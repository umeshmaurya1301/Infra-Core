package org.infra.messaging.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.infra.messaging.properties.MessagingProperties;
import org.infra.messaging.properties.RabbitMQProperties;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * RabbitMQ configuration for producer and consumer
 */
@Slf4j
@Configuration
@EnableRabbit
@RequiredArgsConstructor
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(prefix = "infra.messaging.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMQConfiguration {

    private final MessagingProperties messagingProperties;

    @Bean
    public ConnectionFactory connectionFactory() {
        RabbitMQProperties rabbitProps = messagingProperties.getRabbitmq();
        
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitProps.getHost());
        factory.setPort(rabbitProps.getPort());
        factory.setUsername(rabbitProps.getUsername());
        factory.setPassword(rabbitProps.getPassword());
        factory.setVirtualHost(rabbitProps.getVirtualHost());
        
        // Connection settings
        factory.setConnectionTimeout((int) rabbitProps.getConnectionTimeout().toMillis());
        factory.setRequestedHeartBeat((int) rabbitProps.getRequestedHeartbeat().toSeconds());
        
        // SSL configuration
        if (rabbitProps.getSsl().isEnabled()) {
            try {
                factory.getRabbitConnectionFactory().useSslProtocol(createSslContext(rabbitProps.getSsl()));
                log.info("SSL enabled for RabbitMQ connection");
            } catch (Exception e) {
                log.error("Failed to configure SSL for RabbitMQ", e);
                throw new RuntimeException("SSL configuration failed", e);
            }
        }
        
        // Publisher settings
        if (rabbitProps.getPublisher().isConfirmEnabled()) {
            factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        }
        if (rabbitProps.getPublisher().isReturnEnabled()) {
            factory.setPublisherReturns(true);
        }
        
        log.info("RabbitMQ ConnectionFactory configured for host: {}:{}", 
                rabbitProps.getHost(), rabbitProps.getPort());
        
        return factory;
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RabbitTemplate rabbitTemplate() {
        RabbitMQProperties.PublisherProperties publisherProps = messagingProperties.getRabbitmq().getPublisher();
        
        RabbitTemplate template = new RabbitTemplate(connectionFactory());
        template.setMessageConverter(messageConverter());
        
        // Publisher confirms and returns
        if (publisherProps.isConfirmEnabled()) {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                if (ack) {
                    log.debug("Message confirmed: {}", correlationData);
                } else {
                    log.error("Message not confirmed: {}, cause: {}", correlationData, cause);
                }
            });
        }
        
        if (publisherProps.isReturnEnabled()) {
            template.setReturnsCallback(returnedMessage -> {
                log.error("Message returned: {}, reply code: {}, reply text: {}, exchange: {}, routing key: {}",
                         returnedMessage.getMessage(), returnedMessage.getReplyCode(), 
                         returnedMessage.getReplyText(), returnedMessage.getExchange(), 
                         returnedMessage.getRoutingKey());
            });
        }
        
        // Retry template
        template.setRetryTemplate(createRetryTemplate(publisherProps));
        
        log.info("RabbitTemplate configured with confirms: {}, returns: {}", 
                publisherProps.isConfirmEnabled(), publisherProps.isReturnEnabled());
        
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RabbitListenerContainerFactory<SimpleMessageListenerContainer> rabbitListenerContainerFactory() {
        RabbitMQProperties.ConsumerProperties consumerProps = messagingProperties.getRabbitmq().getConsumer();
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory());
        factory.setMessageConverter(messageConverter());
        
        // Concurrency settings
        factory.setConcurrentConsumers(consumerProps.getConcurrency());
        factory.setMaxConcurrentConsumers(consumerProps.getMaxConcurrency());
        
        // Consumer settings
        factory.setPrefetchCount(consumerProps.getPrefetchCount());
        factory.setAutoStartup(consumerProps.isAutoStartup());
        factory.setReceiveTimeout(consumerProps.getReceiveTimeout().toMillis());
        factory.setRecoveryInterval(consumerProps.getRecoveryInterval().toMillis());
        
        // Acknowledge mode
        switch (consumerProps.getAcknowledgeMode().toUpperCase()) {
            case "NONE":
                factory.setAcknowledgeMode(AcknowledgeMode.NONE);
                break;
            case "MANUAL":
                factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
                break;
            case "AUTO":
            default:
                factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
                break;
        }
        
        // Error handler
        factory.setErrorHandler(rabbitErrorHandler());
        
        log.info("RabbitListenerContainerFactory configured with concurrency: {}-{}", 
                consumerProps.getConcurrency(), consumerProps.getMaxConcurrency());
        
        return factory;
    }

    @Bean
    public TopicExchange defaultExchange() {
        RabbitMQProperties.ExchangeProperties exchangeProps = messagingProperties.getRabbitmq().getExchange();
        
        TopicExchange exchange = new TopicExchange(
                exchangeProps.getDefaultName(),
                exchangeProps.isDurable(),
                exchangeProps.isAutoDelete(),
                exchangeProps.getArguments()
        );
        return exchange;
    }

    @Bean
    public Queue defaultQueue() {
        RabbitMQProperties.QueueProperties queueProps = messagingProperties.getRabbitmq().getQueue();
        
        return QueueBuilder
                .durable(queueProps.getDefaultName())
                .withArguments(queueProps.getArguments())
                .build();
    }

    @Bean
    public Binding defaultBinding() {
        return BindingBuilder
                .bind(defaultQueue())
                .to(defaultExchange())
                .with("#"); // Bind all messages
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RabbitDlqHandler rabbitDlqHandler() {
        return new RabbitDlqHandler(rabbitTemplate(), messagingProperties);
    }

    @Bean
    public RabbitErrorHandler rabbitErrorHandler() {
        return new RabbitErrorHandler(messagingProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TopicExchange dlqExchange() {
        RabbitMQProperties.ExchangeProperties exchangeProps = messagingProperties.getRabbitmq().getExchange();
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        String dlqExchangeName = exchangeProps.getDefaultName() + dlqProps.getExchangeSuffix();
        
        TopicExchange dlqExchange = new TopicExchange(
                dlqExchangeName,
                exchangeProps.isDurable(),
                exchangeProps.isAutoDelete()
        );
        return dlqExchange;
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Queue dlqQueue() {
        RabbitMQProperties.QueueProperties queueProps = messagingProperties.getRabbitmq().getQueue();
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        String dlqQueueName = queueProps.getDefaultName() + dlqProps.getQueueSuffix();
        
        return QueueBuilder
                .durable(dlqQueueName)
                .ttl((int) dlqProps.getMessageTtl().toMillis())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "infra.messaging.rabbitmq.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Binding dlqBinding() {
        RabbitMQProperties.DlqProperties dlqProps = messagingProperties.getRabbitmq().getDlq();
        
        return BindingBuilder
                .bind(dlqQueue())
                .to(dlqExchange())
                .with("#" + dlqProps.getRoutingKeySuffix());
    }

    private RetryTemplate createRetryTemplate(RabbitMQProperties.PublisherProperties publisherProps) {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(publisherProps.getRetries());
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(publisherProps.getRetryInterval().toMillis());
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(30000); // 30 seconds max
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    private SSLContext createSslContext(RabbitMQProperties.SslProperties sslProps) 
            throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext context = SSLContext.getInstance(sslProps.getAlgorithm());
        context.init(null, null, null); // Use default trust managers
        return context;
    }
}

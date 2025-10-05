# Infra Messaging Library

A comprehensive Spring Boot library for messaging with **Kafka** and **RabbitMQ** support, featuring configuration flexibility, error handling, Dead Letter Queue (DLQ) management, and production-ready features.

## Features

### 🚀 Core Features
- **Kafka Integration**: Producer/Consumer with auto-configuration
- **RabbitMQ Integration**: Publisher/Consumer with auto-configuration  
- **Dead Letter Queue (DLQ)**: Automatic error handling with retry logic
- **Security Support**: SSL/TLS, SASL authentication for both Kafka and RabbitMQ
- **Flexible Configuration**: External properties-based configuration
- **Error Handling**: Comprehensive error handling with retry mechanisms
- **Production Ready**: Monitoring, logging, and fail-safe mechanisms

### 📦 Kafka Features
- Configurable Producer and Consumer beans
- Dynamic topic configuration via external properties
- Support for JSON serialization/deserialization
- Consumer group management with configurable concurrency
- Security configurations (SASL/SSL, username-password)
- Automatic DLQ publishing on failures
- Retry logic with exponential backoff

### 🐰 RabbitMQ Features
- Configurable ConnectionFactory, Exchange, Queue, and Binding
- Publisher confirms and returns
- Multiple consumers with configurable concurrency
- DLQ configuration with TTL support
- SSL/TLS support
- RPC (Request-Reply) pattern support
- Message priority and expiration support

## Quick Start

### 1. Add Dependency

Add the infra-messaging module to your project:

```gradle
dependencies {
    implementation project(':infra-messaging')
}
```

### 2. Basic Configuration

Add to your `application.yml`:

```yaml
infra:
  messaging:
    enabled: true
    
    kafka:
      enabled: true
      bootstrap-servers: localhost:9092
      consumer:
        group-id: my-app-group
    
    rabbitmq:
      enabled: true
      host: localhost
      port: 5672
      username: guest
      password: guest
```

### 3. Usage Examples

#### Kafka Producer
```java
@Autowired
private KafkaProducerService kafkaProducer;

// Send message asynchronously
kafkaProducer.sendAsync("user-events", user);

// Send message with key
kafkaProducer.sendAsync("user-events", user.getId(), user);

// Send message synchronously
kafkaProducer.sendSync("user-events", user);
```

#### Kafka Consumer
```java
@KafkaListener(topics = "user-events")
public void handleUserEvent(User user, Acknowledgment ack) {
    try {
        // Process user event
        processUser(user);
        ack.acknowledge();
    } catch (Exception e) {
        // Error will be handled by KafkaErrorHandler
        // Message will be sent to DLQ if configured
        throw e;
    }
}
```

#### RabbitMQ Producer
```java
@Autowired
private RabbitProducerService rabbitProducer;

// Send to default exchange
rabbitProducer.send("user.created", user);

// Send to specific exchange
rabbitProducer.send("user.exchange", "user.created", user);

// Send with headers
Map<String, Object> headers = Map.of("source", "user-service");
rabbitProducer.send("user.exchange", "user.created", user, headers);

// RPC pattern
Object response = rabbitProducer.sendAndReceive("rpc.exchange", "calculate", request);
```

#### RabbitMQ Consumer
```java
@RabbitListener(queues = "user.queue")
public void handleUserMessage(User user, 
                             @Header Map<String, Object> headers,
                             Channel channel, 
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        // Process user message
        processUser(user);
        // Message will be auto-acknowledged
    } catch (Exception e) {
        // Error will be handled by RabbitErrorHandler
        // Message will be sent to DLQ if configured
        throw e;
    }
}
```

## Configuration

### Complete Configuration Example

See [application-messaging-example.yml](src/main/resources/application-messaging-example.yml) for a comprehensive configuration example with all available options.

### Key Configuration Sections

#### Kafka Configuration
```yaml
infra:
  messaging:
    kafka:
      enabled: true
      bootstrap-servers: localhost:9092
      producer:
        acks: all
        retries: 3
        batch-size: 16384
      consumer:
        group-id: my-app
        auto-offset-reset: earliest
        concurrency: 3
      security:
        protocol: SASL_SSL
        username: kafka-user
        password: kafka-password
      dlq:
        enabled: true
        max-retries: 3
        retry-interval: PT1S
```

#### RabbitMQ Configuration
```yaml
infra:
  messaging:
    rabbitmq:
      enabled: true
      host: localhost
      port: 5672
      username: guest
      password: guest
      publisher:
        confirm-enabled: true
        retries: 3
      consumer:
        concurrency: 2
        prefetch-count: 250
      dlq:
        enabled: true
        max-retries: 3
        message-ttl: PT24H
```

## Error Handling and DLQ

### Automatic Error Handling
- **Kafka**: Failed messages are automatically sent to DLQ topics (original-topic.dlq)
- **RabbitMQ**: Failed messages are sent to DLQ exchanges/queues with metadata

### DLQ Message Format
Both Kafka and RabbitMQ DLQ messages include:
- Original message content
- Error information (exception class, message)
- Timestamp of failure
- Retry attempt count
- Original routing information

### Retry Logic
- Configurable retry attempts
- Exponential backoff
- Maximum backoff intervals
- Circuit breaker patterns

## Security

### Kafka Security
- **SASL/PLAIN**: Username/password authentication
- **SASL/SCRAM**: SCRAM-SHA-256, SCRAM-SHA-512
- **SSL/TLS**: Keystore and truststore configuration
- **SASL_SSL**: Combined SASL and SSL

### RabbitMQ Security
- **Basic Auth**: Username/password
- **SSL/TLS**: Certificate-based security
- **Virtual Hosts**: Namespace isolation

## Monitoring and Logging

### Built-in Logging
- Comprehensive debug and error logging
- Configurable log levels
- Message flow tracking
- Performance metrics logging

### Recommended Logging Configuration
```yaml
logging:
  level:
    org.infra.messaging: DEBUG
    org.springframework.kafka: INFO
    org.springframework.amqp: INFO
```

## Advanced Features

### Custom Message Converters
The library uses JSON converters by default but can be customized:

```java
@Bean
public MessageConverter customMessageConverter() {
    return new MyCustomMessageConverter();
}
```

### Custom Error Handlers
```java
@Bean
public KafkaErrorHandler customKafkaErrorHandler() {
    return new MyCustomKafkaErrorHandler();
}

@Bean
public RabbitErrorHandler customRabbitErrorHandler() {
    return new MyCustomRabbitErrorHandler();
}
```

### Conditional Bean Creation
All beans are created conditionally based on configuration:
- `infra.messaging.enabled=true` - Enables the entire module
- `infra.messaging.kafka.enabled=true` - Enables Kafka components
- `infra.messaging.rabbitmq.enabled=true` - Enables RabbitMQ components

## Testing

### Test Dependencies
```gradle
testImplementation 'org.springframework.kafka:spring-kafka-test'
testImplementation 'org.testcontainers:kafka'
testImplementation 'org.testcontainers:rabbitmq'
```

### Integration Testing
Use TestContainers for integration testing:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "infra.messaging.kafka.bootstrap-servers=${kafka.bootstrapServers}",
    "infra.messaging.rabbitmq.host=${rabbitmq.host}"
})
class MessagingIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
    
    @Container  
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");
    
    // Test cases...
}
```

## Migration Guide

### From Spring Kafka/AMQP
1. Replace direct Spring Kafka/AMQP dependencies with infra-messaging
2. Update configuration to use `infra.messaging.*` properties
3. Replace `@KafkaListener`/`@RabbitListener` usage (no changes needed)
4. Update service injection to use `KafkaProducerService`/`RabbitProducerService`

### Configuration Migration
```yaml
# Old Spring Boot configuration
spring:
  kafka:
    bootstrap-servers: localhost:9092
  rabbitmq:
    host: localhost

# New infra-messaging configuration  
infra:
  messaging:
    kafka:
      bootstrap-servers: localhost:9092
    rabbitmq:
      host: localhost
```

## Troubleshooting

### Common Issues

1. **Auto-configuration not working**
   - Ensure `infra.messaging.enabled=true`
   - Check classpath for required dependencies

2. **Kafka connection issues**
   - Verify bootstrap servers configuration
   - Check network connectivity
   - Validate security settings

3. **RabbitMQ connection issues**
   - Verify host, port, credentials
   - Check virtual host configuration
   - Validate SSL settings

4. **DLQ not working**
   - Ensure `dlq.enabled=true`
   - Check DLQ topic/queue creation
   - Verify error handler configuration

### Debug Logging
Enable debug logging for troubleshooting:
```yaml
logging:
  level:
    org.infra.messaging: DEBUG
```

## License

This project is licensed under the Apache License, Version 2.0.

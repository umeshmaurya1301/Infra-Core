package org.infra.messaging.properties;

import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ configuration properties
 */
@Data
public class RabbitMQProperties {
    
    /**
     * Whether RabbitMQ is enabled
     */
    private boolean enabled = false;
    
    /**
     * RabbitMQ host
     */
    private String host = "localhost";
    
    /**
     * RabbitMQ port
     */
    private int port = 5672;
    
    /**
     * Username
     */
    private String username = "guest";
    
    /**
     * Password
     */
    private String password = "guest";
    
    /**
     * Virtual host
     */
    private String virtualHost = "/";
    
    /**
     * Connection timeout
     */
    private Duration connectionTimeout = Duration.ofSeconds(60);
    
    /**
     * Requested heartbeat
     */
    private Duration requestedHeartbeat = Duration.ofSeconds(60);
    
    /**
     * Publisher configuration
     */
    @NestedConfigurationProperty
    private PublisherProperties publisher = new PublisherProperties();
    
    /**
     * Consumer configuration
     */
    @NestedConfigurationProperty
    private ConsumerProperties consumer = new ConsumerProperties();
    
    /**
     * Exchange configuration
     */
    @NestedConfigurationProperty
    private ExchangeProperties exchange = new ExchangeProperties();
    
    /**
     * Queue configuration
     */
    @NestedConfigurationProperty
    private QueueProperties queue = new QueueProperties();
    
    /**
     * Dead Letter Queue configuration
     */
    @NestedConfigurationProperty
    private DlqProperties dlq = new DlqProperties();
    
    /**
     * SSL configuration
     */
    @NestedConfigurationProperty
    private SslProperties ssl = new SslProperties();
    
    @Data
    public static class PublisherProperties {
        /**
         * Whether publisher is enabled
         */
        private boolean enabled = true;
        
        /**
         * Whether to enable publisher confirms
         */
        private boolean confirmEnabled = true;
        
        /**
         * Publisher confirm timeout
         */
        private Duration confirmTimeout = Duration.ofSeconds(5);
        
        /**
         * Whether to enable publisher returns
         */
        private boolean returnEnabled = true;
        
        /**
         * Number of retries
         */
        private int retries = 3;
        
        /**
         * Retry interval
         */
        private Duration retryInterval = Duration.ofSeconds(1);
    }
    
    @Data
    public static class ConsumerProperties {
        /**
         * Whether consumer is enabled
         */
        private boolean enabled = true;
        
        /**
         * Concurrency level
         */
        private int concurrency = 1;
        
        /**
         * Maximum concurrency
         */
        private int maxConcurrency = 10;
        
        /**
         * Prefetch count
         */
        private int prefetchCount = 250;
        
        /**
         * Transaction size
         */
        private int txSize = 1;
        
        /**
         * Acknowledge mode (NONE, MANUAL, AUTO)
         */
        private String acknowledgeMode = "AUTO";
        
        /**
         * Auto startup
         */
        private boolean autoStartup = true;
        
        /**
         * Receive timeout
         */
        private Duration receiveTimeout = Duration.ofSeconds(1);
        
        /**
         * Recovery interval
         */
        private Duration recoveryInterval = Duration.ofSeconds(5);
    }
    
    @Data
    public static class ExchangeProperties {
        /**
         * Default exchange name
         */
        private String defaultName = "default.exchange";
        
        /**
         * Exchange type (direct, topic, fanout, headers)
         */
        private String type = "topic";
        
        /**
         * Whether exchange is durable
         */
        private boolean durable = true;
        
        /**
         * Whether to auto delete exchange
         */
        private boolean autoDelete = false;
        
        /**
         * Exchange arguments
         */
        private Map<String, Object> arguments = new HashMap<>();
    }
    
    @Data
    public static class QueueProperties {
        /**
         * Default queue name
         */
        private String defaultName = "default.queue";
        
        /**
         * Whether queue is durable
         */
        private boolean durable = true;
        
        /**
         * Whether queue is exclusive
         */
        private boolean exclusive = false;
        
        /**
         * Whether to auto delete queue
         */
        private boolean autoDelete = false;
        
        /**
         * Queue arguments
         */
        private Map<String, Object> arguments = new HashMap<>();
    }
    
    @Data
    public static class DlqProperties {
        /**
         * Whether DLQ is enabled
         */
        private boolean enabled = true;
        
        /**
         * DLQ exchange suffix
         */
        private String exchangeSuffix = ".dlx";
        
        /**
         * DLQ queue suffix
         */
        private String queueSuffix = ".dlq";
        
        /**
         * DLQ routing key suffix
         */
        private String routingKeySuffix = ".dlq";
        
        /**
         * Maximum retry attempts before sending to DLQ
         */
        private int maxRetries = 3;
        
        /**
         * Retry interval
         */
        private Duration retryInterval = Duration.ofSeconds(1);
        
        /**
         * TTL for messages in DLQ (time to live)
         */
        private Duration messageTtl = Duration.ofHours(24);
    }
    
    @Data
    public static class SslProperties {
        /**
         * Whether SSL is enabled
         */
        private boolean enabled = false;
        
        /**
         * SSL algorithm
         */
        private String algorithm = "TLSv1.2";
        
        /**
         * Keystore location
         */
        private String keystoreLocation;
        
        /**
         * Keystore password
         */
        private String keystorePassword;
        
        /**
         * Truststore location
         */
        private String truststoreLocation;
        
        /**
         * Truststore password
         */
        private String truststorePassword;
        
        /**
         * Whether to validate server certificate
         */
        private boolean validateServerCertificate = true;
        
        /**
         * Whether to verify hostname
         */
        private boolean verifyHostname = true;
    }
}

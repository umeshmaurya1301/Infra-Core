package org.infra.messaging.properties;

import lombok.Data;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration properties
 */
@Data
public class KafkaProperties {
    
    /**
     * Whether Kafka is enabled
     */
    private boolean enabled = false;
    
    /**
     * Bootstrap servers
     */
    private String bootstrapServers = "localhost:9092";
    
    /**
     * Producer configuration
     */
    @NestedConfigurationProperty
    private ProducerProperties producer = new ProducerProperties();
    
    /**
     * Consumer configuration
     */
    @NestedConfigurationProperty
    private ConsumerProperties consumer = new ConsumerProperties();
    
    /**
     * Security configuration
     */
    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();
    
    /**
     * Dead Letter Queue configuration
     */
    @NestedConfigurationProperty
    private DlqProperties dlq = new DlqProperties();
    
    /**
     * Additional Kafka properties
     */
    private Map<String, Object> properties = new HashMap<>();
    
    @Data
    public static class ProducerProperties {
        /**
         * Whether producer is enabled
         */
        private boolean enabled = true;
        
        /**
         * Key serializer class
         */
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
        
        /**
         * Value serializer class
         */
        private String valueSerializer = "org.springframework.kafka.support.serializer.JsonSerializer";
        
        /**
         * Acknowledgment mode (0, 1, all)
         */
        private String acks = "all";
        
        /**
         * Number of retries
         */
        private int retries = 3;
        
        /**
         * Batch size
         */
        private int batchSize = 16384;
        
        /**
         * Linger time in milliseconds
         */
        private long lingerMs = 5;
        
        /**
         * Buffer memory
         */
        private long bufferMemory = 33554432;
        
        /**
         * Request timeout
         */
        private Duration requestTimeout = Duration.ofSeconds(30);
        
        /**
         * Additional producer properties
         */
        private Map<String, Object> properties = new HashMap<>();
    }
    
    @Data
    public static class ConsumerProperties {
        /**
         * Whether consumer is enabled
         */
        private boolean enabled = true;
        
        /**
         * Consumer group ID
         */
        private String groupId = "default-group";
        
        /**
         * Key deserializer class
         */
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        
        /**
         * Value deserializer class
         */
        private String valueDeserializer = "org.springframework.kafka.support.serializer.JsonDeserializer";
        
        /**
         * Auto offset reset (earliest, latest, none)
         */
        private String autoOffsetReset = "earliest";
        
        /**
         * Whether to enable auto commit
         */
        private boolean enableAutoCommit = false;
        
        /**
         * Session timeout
         */
        private Duration sessionTimeout = Duration.ofSeconds(30);
        
        /**
         * Heartbeat interval
         */
        private Duration heartbeatInterval = Duration.ofSeconds(3);
        
        /**
         * Max poll records
         */
        private int maxPollRecords = 500;
        
        /**
         * Max poll interval
         */
        private Duration maxPollInterval = Duration.ofMinutes(5);
        
        /**
         * Concurrency level
         */
        private int concurrency = 1;
        
        /**
         * Additional consumer properties
         */
        private Map<String, Object> properties = new HashMap<>();
    }
    
    @Data
    public static class SecurityProperties {
        /**
         * Security protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL)
         */
        private String protocol = "PLAINTEXT";
        
        /**
         * SASL mechanism (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI)
         */
        private String saslMechanism;
        
        /**
         * SASL JAAS config
         */
        private String saslJaasConfig;
        
        /**
         * Username for authentication
         */
        private String username;
        
        /**
         * Password for authentication
         */
        private String password;
        
        /**
         * SSL configuration
         */
        @NestedConfigurationProperty
        private SslProperties ssl = new SslProperties();
        
        @Data
        public static class SslProperties {
            /**
             * SSL truststore location
             */
            private String truststoreLocation;
            
            /**
             * SSL truststore password
             */
            private String truststorePassword;
            
            /**
             * SSL keystore location
             */
            private String keystoreLocation;
            
            /**
             * SSL keystore password
             */
            private String keystorePassword;
            
            /**
             * SSL key password
             */
            private String keyPassword;
        }
    }
    
    @Data
    public static class DlqProperties {
        /**
         * Whether DLQ is enabled
         */
        private boolean enabled = true;
        
        /**
         * DLQ topic suffix
         */
        private String topicSuffix = ".dlq";
        
        /**
         * Maximum retry attempts before sending to DLQ
         */
        private int maxRetries = 3;
        
        /**
         * Retry backoff interval
         */
        private Duration retryInterval = Duration.ofSeconds(1);
        
        /**
         * Backoff multiplier
         */
        private double backoffMultiplier = 2.0;
        
        /**
         * Maximum backoff interval
         */
        private Duration maxBackoffInterval = Duration.ofMinutes(5);
    }
}

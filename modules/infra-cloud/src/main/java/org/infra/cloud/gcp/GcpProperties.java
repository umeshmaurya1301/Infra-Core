package org.infra.cloud.gcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Google Cloud Platform (GCP) Cloud Storage integration.
 * 
 * <p>This class contains all the configuration properties needed to connect to and interact
 * with GCP Cloud Storage services. The consuming project should provide these values
 * through application properties or environment variables.</p>
 * 
 * <p><strong>Example Configuration:</strong></p>
 * <pre>
 * # application.yml
 * infra:
 *   cloud:
 *     gcp:
 *       project-id: "my-gcp-project"
 *       credentials-path: "/path/to/service-account.json"
 *       default-bucket: "my-default-bucket"
 *       location: "us-central1"
 *       storage-class: "STANDARD"
 *       timeout:
 *         connection: 30000
 *         read: 60000
 * </pre>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "infra.cloud.gcp")
public class GcpProperties {

    /**
     * GCP Project ID where the Cloud Storage resources are located.
     * This is required for all GCP Cloud Storage operations.
     */
    private String projectId;

    /**
     * Path to the GCP service account credentials JSON file.
     * Alternative to using environment variables for authentication.
     */
    private String credentialsPath;

    /**
     * Base64-encoded service account credentials JSON content.
     * Alternative to using a file path for credentials.
     */
    private String credentialsJson;

    /**
     * Default bucket name to use when no specific bucket is provided.
     * This is optional but recommended for convenience.
     */
    private String defaultBucket;

    /**
     * GCP location/region for creating new buckets and resources.
     * Examples: "us-central1", "europe-west1", "asia-southeast1"
     */
    private String location = "us-central1";

    /**
     * Default storage class for objects.
     * Valid values: STANDARD, NEARLINE, COLDLINE, ARCHIVE
     */
    private String storageClass = "STANDARD";

    /**
     * Whether to enable automatic retry on failed operations.
     */
    private boolean retryEnabled = true;

    /**
     * Maximum number of retry attempts for failed operations.
     */
    private int maxRetries = 3;

    /**
     * Timeout configuration for GCP operations.
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    /**
     * Security and encryption settings.
     */
    private SecurityConfig security = new SecurityConfig();

    // Getters and Setters

    /**
     * Timeout configuration for various GCP operations.
     */
    @Getter
    @Setter
    public static class TimeoutConfig {
        
        /**
         * Connection timeout in milliseconds.
         */
        private long connection = 30000L; // 30 seconds
        
        /**
         * Read timeout in milliseconds.
         */
        private long read = 60000L; // 60 seconds
        
        /**
         * Write timeout in milliseconds.
         */
        private long write = 120000L; // 2 minutes

        // Getters and Setters
    }

    /**
     * Security and encryption configuration.
     */

    @Getter
    @Setter
    public static class SecurityConfig {
        
        /**
         * Whether to enable server-side encryption.
         */
        private boolean encryptionEnabled = true;
        
        /**
         * KMS key name for encryption (optional).
         * If not provided, Google-managed encryption keys will be used.
         */
        private String kmsKeyName;
        
        /**
         * Whether to enable uniform bucket-level access.
         */
        private boolean uniformBucketLevelAccess = true;
    }

    @Override
    public String toString() {
        return "GcpProperties{" +
                "projectId='" + projectId + '\'' +
                ", credentialsPath='" + credentialsPath + '\'' +
                ", defaultBucket='" + defaultBucket + '\'' +
                ", location='" + location + '\'' +
                ", storageClass='" + storageClass + '\'' +
                ", retryEnabled=" + retryEnabled +
                ", maxRetries=" + maxRetries +
                ", timeout=" + timeout +
                ", security=" + security +
                '}';
    }
}

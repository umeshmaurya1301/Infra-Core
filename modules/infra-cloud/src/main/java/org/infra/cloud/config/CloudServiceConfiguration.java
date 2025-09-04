package org.infra.cloud.config;

import org.infra.cloud.gcp.GcpProperties;
import org.infra.cloud.gcp.GcpStorageService;
import org.infra.cloud.gcp.GcpStorageServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for the cloud services module.
 * This configuration enables component scanning for cloud service implementations,
 * enables configuration properties, and provides bean configurations for cloud services.
 * 
 * <p>The configuration is designed to be modular and only activates services
 * when their respective properties are configured.</p>
 * 
 * @author Infrastructure Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
    GcpProperties.class
})
@ComponentScan(basePackages = {
    "org.infra.cloud",
    "org.infra.cloud.gcp"
})
public class CloudServiceConfiguration {
    
    /**
     * Creates a GCP Storage Service bean when GCP is enabled.
     * The service is only created when the GCP project ID is configured.
     * 
     * @param gcpProperties the GCP configuration properties
     * @return configured GCP storage service instance
     */
    @Bean
    @ConditionalOnProperty(name = "infra.cloud.gcp.project-id")
    public GcpStorageService gcpStorageService(GcpProperties gcpProperties) {
        GcpStorageServiceImpl service = new GcpStorageServiceImpl();
        
        // Initialize the service with properties
        try {
            service.initialize(gcpProperties);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GCP Storage Service", e);
        }
        
        return service;
    }
    
    // Future AWS and Azure bean configurations can be added here
    // when those services are implemented
    
    // @Bean
    // @ConditionalOnProperty(name = "infra.cloud.aws.access-key-id")
    // public AwsStorageService awsStorageService(AwsProperties awsProperties) {
    //     // AWS service configuration
    // }
    
    // @Bean
    // @ConditionalOnProperty(name = "infra.cloud.azure.storage-account-name")
    // public AzureStorageService azureStorageService(AzureProperties azureProperties) {
    //     // Azure service configuration
    // }
}

package org.infra.kafka.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 8 — Advanced Features & Hardening (Admin Operations).
 *
 * <p>Handles topic auto-creation if enabled via {@code infra.kafka.admin.auto-create=true}.
 * In production, it is strongly recommended to keep this disabled and manage
 * topics via IaC (Infrastructure as Code) tools like Terraform.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "infra.kafka.admin",
        name = "auto-create",
        havingValue = "true"
)
public class KafkaAdminConfig {

    private final InfraKafkaProperties properties;
    private final KafkaSecurityConfig securityConfig;

    public KafkaAdminConfig(InfraKafkaProperties properties, KafkaSecurityConfig securityConfig) {
        this.properties = properties;
        this.securityConfig = securityConfig;
    }

    /**
     * Initializes the {@link KafkaAdmin} bean which manages the creation of topics.
     * Includes security settings (SSL/SASL) so admin operations are permitted in secured clusters.
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        
        // Apply security (Phase 6) to admin client
        securityConfig.applySecurity(configs);

        log.info("[infra-kafka] KafkaAdmin initialized for topic auto-creation");
        return new KafkaAdmin(configs);
    }

    /**
     * Dynamic registration of {@link NewTopic} beans based on configuration.
     * Spring Kafka detects all {@link NewTopic} beans and creates them automatically
     * using the {@link KafkaAdmin} initialized above.
     *
     * @param admin the KafkaAdmin proxy
     * @return a proxy object to hold the dynamically registered topics
     */
    @Bean
    public KafkaTopicRegistrar kafkaTopicRegistrar(KafkaAdmin admin) {
        if (properties.getAdmin().getTopics() == null || properties.getAdmin().getTopics().isEmpty()) {
            log.warn("[infra-kafka] Admin auto-create is enabled but no topics are defined.");
            return new KafkaTopicRegistrar(admin);
        }

        properties.getAdmin().getTopics().forEach(td -> {
            NewTopic newTopic = TopicBuilder.name(td.getName())
                    .partitions(td.getPartitions())
                    .replicas(td.getReplicationFactor())
                    .build();
            admin.createOrModifyTopics(newTopic);
            log.info("[infra-kafka] Auto-created topic: {} (partitions: {}, replication: {})",
                    td.getName(), td.getPartitions(), td.getReplicationFactor());
        });

        return new KafkaTopicRegistrar(admin);
    }

    /** Dummy class as a bean target to encapsulate programmatic topic creation. */
    public static class KafkaTopicRegistrar {
        private final KafkaAdmin admin;

        public KafkaTopicRegistrar(KafkaAdmin admin) {
            this.admin = admin;
        }

        public KafkaAdmin getAdmin() {
            return admin;
        }
    }
}

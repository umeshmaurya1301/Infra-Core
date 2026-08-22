package org.infra.kafka.retry;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Non-blocking retry (Spring Kafka {@code @RetryableTopic} infrastructure).
 *
 * <p>Activated by {@code infra.kafka.retry.mode=non-blocking}. Declaring a
 * {@link RetryTopicConfiguration} bean bootstraps Spring Kafka's retry-topic feature, which
 * applies to every {@code @KafkaListener} / {@link org.infra.kafka.consumer.InfraKafkaListener}
 * in the application.
 *
 * <h3>How it differs from blocking retry</h3>
 * <p>Instead of pausing the partition and retrying the record in place, a failing record is
 * <em>forwarded</em> to a delayed retry topic and re-consumed later, so the original partition
 * keeps flowing. Topics are named:
 * <pre>
 *   original:  order-events
 *   retry:     order-events-retry-0, order-events-retry-1, ...   (infra.kafka.retry.retry-topic-suffix)
 *   DLT:       order-events-dlq                                   (infra.kafka.dlq.suffix)
 * </pre>
 *
 * <h3>Topic creation</h3>
 * <p>Retry/DLT topics must exist. When {@code infra.kafka.retry.auto-create-retry-topics=true}
 * (the default) the retry infrastructure creates them via a {@link KafkaAdmin} — this config
 * supplies one pointed at {@code infra.kafka.bootstrap-servers} (with security applied) unless
 * the application already defines a {@link KafkaAdmin}.
 *
 * <p><b>Ordering note:</b> non-blocking retry trades strict per-key ordering during failures
 * for availability — a retried record may be processed after later records from the same
 * partition. Prefer {@code BLOCKING} mode when strict ordering matters more than throughput.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "infra.kafka.retry", name = "mode", havingValue = "non-blocking")
public class NonBlockingRetryConfig {

    private final InfraKafkaProperties properties;
    private final KafkaSecurityConfig securityConfig;

    public NonBlockingRetryConfig(InfraKafkaProperties properties, KafkaSecurityConfig securityConfig) {
        this.properties = properties;
        this.securityConfig = securityConfig;
        log.info("[infra-kafka] Non-blocking retry mode ACTIVE — retry topics + DLT will be used");
    }

    /**
     * Builds the global {@link RetryTopicConfiguration}. Backoff, attempt count, retry-topic
     * suffix and DLT suffix are all taken from {@code infra.kafka.retry.*} / {@code infra.kafka.dlq.*}.
     *
     * @param template the template used to forward records to retry / DLT topics
     */
    @Bean
    @ConditionalOnMissingBean(RetryTopicConfiguration.class)
    public RetryTopicConfiguration infraKafkaRetryTopicConfiguration(
            KafkaTemplate<String, Object> template) {

        InfraKafkaProperties.RetryProperties retry = properties.getRetry();
        InfraKafkaProperties.DlqProperties   dlq   = properties.getDlq();

        RetryTopicConfigurationBuilder builder = RetryTopicConfigurationBuilder.newInstance()
                .maxAttempts(retry.getMaxAttempts())
                .exponentialBackoff(
                        retry.getBackoffInitialInterval(),
                        retry.getBackoffMultiplier(),
                        retry.getBackoffMaxInterval())
                .retryTopicSuffix(retry.getRetryTopicSuffix())
                .dltSuffix(dlq.getSuffix())
                // Mirror the blocking handler's non-retryable classification.
                .notRetryOn(IllegalArgumentException.class)
                .notRetryOn(IllegalStateException.class)
                .notRetryOn(NullPointerException.class)
                .doNotRetryOnDltFailure();

        if (retry.isAutoCreateRetryTopics()) {
            builder.autoCreateTopics(true,
                    retry.getRetryTopicPartitions(),
                    retry.getRetryTopicReplicationFactor());
        } else {
            builder.doNotAutoCreateRetryTopics();
        }

        log.info("[infra-kafka] RetryTopicConfiguration created — maxAttempts={}, retrySuffix={}, dltSuffix={}, "
                        + "autoCreate={}",
                retry.getMaxAttempts(), retry.getRetryTopicSuffix(), dlq.getSuffix(),
                retry.isAutoCreateRetryTopics());

        return builder.create(template);
    }

    /**
     * A {@link KafkaAdmin} pointed at {@code infra.kafka.bootstrap-servers} so that retry/DLT
     * topics are created on the same cluster the library is configured for. Backs off if the
     * application already provides one.
     */
    @Bean
    @ConditionalOnMissingBean(KafkaAdmin.class)
    public KafkaAdmin infraKafkaRetryTopicAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        securityConfig.applySecurity(configs);
        log.info("[infra-kafka] KafkaAdmin initialized for non-blocking retry topic creation");
        return new KafkaAdmin(configs);
    }
}

package org.infra.kafka.retry;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.error.DefaultKafkaErrorHandler;
import org.infra.kafka.error.DeserializationErrorHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring {@link Configuration} that wires the Phase 3 retry and DLQ pipeline.
 *
 * <p>This configuration is activated when:
 * <ul>
 *   <li>{@code infra.kafka.retry.enabled=true} (the default)</li>
 *   <li>A {@link KafkaTemplate} is available in the context (spring-kafka on classpath)</li>
 * </ul>
 *
 * <h3>What gets created</h3>
 * <ol>
 *   <li>{@link DeserializationErrorHandler} — routes corrupt messages directly to DLQ</li>
 *   <li>{@link DefaultKafkaErrorHandler} — handles business logic exceptions with
 *       exponential backoff retries and eventual DLQ routing</li>
 * </ol>
 *
 * <h3>DLQ Topic Naming</h3>
 * <pre>
 * Original topic:  order-events
 * DLQ topic:       order-events-dlq    (configurable via infra.kafka.dlq.suffix)
 * </pre>
 *
 * <h3>Retry Sequence</h3>
 * <pre>
 *  Attempt 1 (original) → fails
 *  Attempt 2            → backoff: 1 s
 *  Attempt 3            → backoff: 2 s
 *  ...
 *  All attempts exhausted → DLQ
 * </pre>
 *
 * <p>All beans use {@link ConditionalOnMissingBean} so consumer applications can
 * supply custom implementations when needed.
 */
@Slf4j
@Configuration
public class RetryTopicConfig {

    private final InfraKafkaProperties properties;

    public RetryTopicConfig(InfraKafkaProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates the {@link DeserializationErrorHandler} bean.
     *
     * <p>Routes any record that couldn't be deserialized directly to the DLQ,
     * bypassing the retry pipeline (retrying a corrupt message is pointless).
     *
     * @param kafkaTemplate the template used to publish to DLQ topics
     */
    @Bean
    @ConditionalOnMissingBean(DeserializationErrorHandler.class)
    public DeserializationErrorHandler infraDeserializationErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate) {

        String dlqSuffix = properties.getDlq().getSuffix();
        log.info("[infra-kafka] DeserializationErrorHandler created — dlqSuffix={}", dlqSuffix);
        return new DeserializationErrorHandler(kafkaTemplate, dlqSuffix);
    }

    /**
     * Creates the {@link DefaultKafkaErrorHandler} bean.
     *
     * <p>Wraps Spring Kafka's {@link org.springframework.kafka.listener.DefaultErrorHandler}
     * with exponential backoff and a {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}
     * so that messages are retried on transient failures and routed to the DLQ after
     * all attempts are exhausted.
     *
     * @param deadLetterTemplate the byte[]-passthrough template used to publish to DLQ topics
     */
    @Bean("infraKafkaErrorHandler")
    @ConditionalOnMissingBean(name = "infraKafkaErrorHandler")
    @ConditionalOnProperty(prefix = "infra.kafka.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DefaultKafkaErrorHandler infraKafkaErrorHandler(
            @Qualifier("infraKafkaDltTemplate") KafkaOperations<?, ?> deadLetterTemplate) {

        InfraKafkaProperties.RetryProperties retry   = properties.getRetry();
        InfraKafkaProperties.DlqProperties   dlq     = properties.getDlq();

        DefaultKafkaErrorHandler handler = new DefaultKafkaErrorHandler(
                deadLetterTemplate,
                dlq.getSuffix(),
                retry.getBackoffInitialInterval(),
                retry.getBackoffMultiplier(),
                retry.getBackoffMaxInterval(),
                retry.getMaxAttempts()
        );

        log.info("[infra-kafka] DefaultKafkaErrorHandler created — " +
                        "maxAttempts={}, initialBackoff={}ms, multiplier={}, maxBackoff={}ms, dlqSuffix={}",
                retry.getMaxAttempts(),
                retry.getBackoffInitialInterval(),
                retry.getBackoffMultiplier(),
                retry.getBackoffMaxInterval(),
                dlq.getSuffix());

        return handler;
    }

    /**
     * Creates the no-retry error handler used by the
     * {@code infraKafkaNoRetryListenerContainerFactory}.
     *
     * <p>Configured with {@code maxAttempts=1} (i.e. zero retries), so a failing record
     * is routed to the DLQ ({@code <topic><dlq-suffix>}) on the very first failure. This
     * backs listeners annotated with
     * {@link org.infra.kafka.consumer.InfraKafkaListener#NON_RETRYING_CONTAINER_FACTORY}.
     *
     * @param deadLetterTemplate the byte[]-passthrough template used to publish to DLQ topics
     */
    @Bean("infraKafkaNoRetryErrorHandler")
    @ConditionalOnMissingBean(name = "infraKafkaNoRetryErrorHandler")
    public DefaultKafkaErrorHandler infraKafkaNoRetryErrorHandler(
            @Qualifier("infraKafkaDltTemplate") KafkaOperations<?, ?> deadLetterTemplate) {

        InfraKafkaProperties.RetryProperties retry = properties.getRetry();
        InfraKafkaProperties.DlqProperties   dlq   = properties.getDlq();

        // maxAttempts=1 → zero retries → straight to DLQ on first failure.
        DefaultKafkaErrorHandler handler = new DefaultKafkaErrorHandler(
                deadLetterTemplate,
                dlq.getSuffix(),
                retry.getBackoffInitialInterval(),
                retry.getBackoffMultiplier(),
                retry.getBackoffMaxInterval(),
                1
        );

        log.info("[infra-kafka] No-retry DefaultKafkaErrorHandler created (immediate DLQ) — dlqSuffix={}",
                dlq.getSuffix());

        return handler;
    }

    /**
     * Creates the {@link DlqHandler} bean, which provides an optional listener
     * for messages that have landed in the DLQ.
     *
     * <p>The {@link DlqHandler} does <em>not</em> auto-subscribe to any DLQ topic —
     * consumer applications wire their own {@code @InfraKafkaListener} pointing at
     * {@code <topic>-dlq}. This bean is a utility for structured logging and replay
     * support.
     */
    @Bean
    @ConditionalOnMissingBean(DlqHandler.class)
    public DlqHandler infraDlqHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        log.info("[infra-kafka] DlqHandler created");
        return new DlqHandler(kafkaTemplate, properties);
    }
}

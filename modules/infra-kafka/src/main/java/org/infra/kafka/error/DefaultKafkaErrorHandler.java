package org.infra.kafka.error;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.ArrayList;
import java.util.List;

/**
 * Default error handler for the infra-kafka library.
 *
 * <p>Wraps Spring Kafka's {@link DefaultErrorHandler} with a
 * {@link DeadLetterPublishingRecoverer} to provide:
 * <ul>
 *   <li><b>Non-blocking retry</b> with configurable exponential backoff</li>
 *   <li><b>DLQ routing</b> when retries are exhausted — topic name follows
 *       the pattern {@code <original-topic><dlq-suffix>}</li>
 *   <li><b>Exception classification</b> — certain exceptions (e.g. validation
 *       errors, illegal arguments) skip retries and are sent directly to the DLQ</li>
 * </ul>
 *
 * <h3>DLQ header enrichment</h3>
 * The {@link DeadLetterPublishingRecoverer} automatically appends these headers
 * to every DLQ message:
 * <ul>
 *   <li>{@code kafka_dlt-original-topic} — source topic name</li>
 *   <li>{@code kafka_dlt-original-partition} — source partition</li>
 *   <li>{@code kafka_dlt-original-offset} — source offset</li>
 *   <li>{@code kafka_dlt-exception-fqcn} — fully-qualified exception class</li>
 *   <li>{@code kafka_dlt-exception-message} — exception message text</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * This class is instantiated by {@link org.infra.kafka.retry.RetryTopicConfig}
 * and injected into the {@link org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory}
 * via {@link org.infra.kafka.consumer.KafkaConsumerConfig}.
 */
@Slf4j
public class DefaultKafkaErrorHandler implements CommonErrorHandler {

    private final DefaultErrorHandler delegate;

    /**
     * Non-retryable exception types — these go straight to the DLQ.
     * Add application-specific exceptions via {@link #addNotRetryable(Class)}.
     */
    private final List<Class<? extends Exception>> notRetryableExceptions = new ArrayList<>(List.of(
            IllegalArgumentException.class,
            IllegalStateException.class,
            NullPointerException.class
    ));

    /**
     * Constructs the error handler with the given backoff settings and DLQ recoverer.
     *
     * @param deadLetterTemplate   template used by the recoverer to publish messages to the DLQ
     *                             topic; its value serializer must forward the record's raw
     *                             {@code byte[]} value unchanged (see {@code infraKafkaDltTemplate})
     * @param dlqSuffix            suffix appended to the original topic to form the DLQ topic name
     * @param initialIntervalMs    initial backoff delay (milliseconds)
     * @param backoffMultiplier    exponential multiplier applied after each retry
     * @param maxIntervalMs        maximum backoff delay cap (milliseconds)
     * @param maxAttempts          total number of delivery attempts (including the first)
     */
    public DefaultKafkaErrorHandler(KafkaOperations<?, ?> deadLetterTemplate,
                                    String dlqSuffix,
                                    long initialIntervalMs,
                                    double backoffMultiplier,
                                    long maxIntervalMs,
                                    int maxAttempts) {

        // Route failed messages to <topic><dlqSuffix>
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplate,
                (record, ex) -> {
                    String dlqTopic = record.topic() + dlqSuffix;
                    log.error("[infra-kafka] DLQ → topic={} partition={} offset={} exception={}",
                            dlqTopic, record.partition(), record.offset(), ex.getMessage(), ex);
                    return new TopicPartition(dlqTopic, record.partition());
                }
        );

        // Configure exponential backoff
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, backoffMultiplier);
        backOff.setMaxInterval(maxIntervalMs);
        // maxAttempts includes the first attempt; backOff maxElapsedTime drives exhaustion
        // We use maxAttempts-1 retries (DefaultErrorHandler counts delivery attempts)
        backOff.setMaxAttempts(maxAttempts - 1);

        this.delegate = new DefaultErrorHandler(recoverer, backOff);

        // Classify non-retryable exceptions upfront
        notRetryableExceptions.forEach(ex -> delegate.addNotRetryableExceptions(ex));

        log.info("[infra-kafka] DefaultKafkaErrorHandler configured — " +
                        "dlqSuffix={}, initialMs={}, multiplier={}, maxMs={}, maxAttempts={}",
                dlqSuffix, initialIntervalMs, backoffMultiplier, maxIntervalMs, maxAttempts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CommonErrorHandler delegation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean handleOne(Exception thrownException,
                             ConsumerRecord<?, ?> record,
                             org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                             MessageListenerContainer container) {
        return delegate.handleOne(thrownException, record, consumer, container);
    }

    @Override
    public void handleRemaining(Exception thrownException,
                                List<ConsumerRecord<?, ?>> records,
                                org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                                MessageListenerContainer container) {
        delegate.handleRemaining(thrownException, records, consumer, container);
    }

    @Override
    public void handleBatch(Exception thrownException,
                            org.apache.kafka.clients.consumer.ConsumerRecords<?, ?> data,
                            org.apache.kafka.clients.consumer.Consumer<?, ?> consumer,
                            MessageListenerContainer container,
                            Runnable invokeListener) {
        delegate.handleBatch(thrownException, data, consumer, container, invokeListener);
    }

    @Override
    public boolean isAckAfterHandle() {
        return delegate.isAckAfterHandle();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Marks an exception class as non-retryable.
     * Messages triggering this exception skip retry topics and are sent directly to the DLQ.
     *
     * @param exceptionClass the exception class to add
     * @return this instance for method chaining
     */
    public DefaultKafkaErrorHandler addNotRetryable(Class<? extends Exception> exceptionClass) {
        notRetryableExceptions.add(exceptionClass);
        delegate.addNotRetryableExceptions(exceptionClass);
        log.info("[infra-kafka] Added non-retryable exception: {}", exceptionClass.getName());
        return this;
    }

    /**
     * Returns the underlying Spring Kafka {@link DefaultErrorHandler} for advanced customization.
     */
    public DefaultErrorHandler getDelegate() {
        return delegate;
    }
}

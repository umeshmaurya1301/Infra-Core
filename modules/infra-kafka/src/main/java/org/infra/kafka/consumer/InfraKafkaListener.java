package org.infra.kafka.consumer;

import org.springframework.core.annotation.AliasFor;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.annotation.*;

/**
 * Meta-annotation for infra-kafka consumer methods.
 *
 * <p>Acts as a drop-in replacement for Spring's {@link KafkaListener} while
 * automatically wiring one of the library's production-grade container factories.
 * Every listener shares the same defaults:
 * <ul>
 *   <li>Manual offset commit ({@code AckMode.RECORD})</li>
 *   <li>JSON deserialization with trusted packages</li>
 *   <li>Configurable concurrency</li>
 * </ul>
 *
 * <h3>Retry / DLQ selection</h3>
 * <p>By default a listener uses {@link #RETRYING_CONTAINER_FACTORY}, which retries
 * with exponential backoff and then routes to the DLQ. To make a listener skip
 * retries and send failures <em>straight to the DLQ</em>, point it at
 * {@link #NON_RETRYING_CONTAINER_FACTORY}:
 *
 * <pre>{@code
 * @InfraKafkaListener(
 *     topics = "payment-events",
 *     groupId = "order-service",
 *     containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
 * public void handlePayment(@Payload PaymentEvent event) { ... }
 * }</pre>
 *
 * <p>(This replaces the former non-functional {@code retryable} flag, which had no
 * effect — retry behaviour is a property of the container factory, so it is now
 * selected explicitly and verifiably.)
 *
 * <h3>Basic usage</h3>
 * <pre>{@code
 * @Component
 * public class PaymentEventConsumer {
 *
 *     @InfraKafkaListener(topics = "payment-events", groupId = "order-service")
 *     public void handlePayment(@Payload PaymentEvent event) {
 *         // business logic — retries with backoff then DLQ on repeated failure
 *     }
 * }
 * }</pre>
 *
 * <p>The {@code groupId} defaults to the value of
 * {@code infra.kafka.consumer.group-id} when left empty.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@KafkaListener
public @interface InfraKafkaListener {

    /**
     * Bean name of the default container factory: retries with exponential backoff
     * (per {@code infra.kafka.retry.*}) and then routes to the DLQ.
     */
    String RETRYING_CONTAINER_FACTORY = "infraKafkaListenerContainerFactory";

    /**
     * Bean name of the no-retry container factory: on the first failure the record is
     * sent straight to the DLQ ({@code <topic><infra.kafka.dlq.suffix>}) with no retries.
     */
    String NON_RETRYING_CONTAINER_FACTORY = "infraKafkaNoRetryListenerContainerFactory";

    /**
     * One or more Kafka topic names to subscribe to.
     * Mutually exclusive with {@link #topicPattern()}.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topics")
    String[] topics() default {};

    /**
     * A regex pattern for dynamic topic subscription.
     * Mutually exclusive with {@link #topics()}.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "topicPattern")
    String topicPattern() default "";

    /**
     * Consumer group ID override for this listener.
     * When empty, inherits {@code infra.kafka.consumer.group-id}.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "groupId")
    String groupId() default "";

    /**
     * Unique listener ID. Auto-generated when left empty.
     * Useful for monitoring / management endpoints.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "id")
    String id() default "";

    /**
     * Container factory bean name. Defaults to {@link #RETRYING_CONTAINER_FACTORY}
     * (retry + DLQ). Set to {@link #NON_RETRYING_CONTAINER_FACTORY} to send failures
     * straight to the DLQ without retries.
     */
    @AliasFor(annotation = KafkaListener.class, attribute = "containerFactory")
    String containerFactory() default "infraKafkaListenerContainerFactory";
}

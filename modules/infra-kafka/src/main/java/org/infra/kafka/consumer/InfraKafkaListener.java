package org.infra.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;

import java.lang.annotation.*;

/**
 * Meta-annotation for infra-kafka consumer methods.
 *
 * <p>Acts as a drop-in replacement for Spring's {@link KafkaListener} while
 * automatically wiring the {@code infraKafkaListenerContainerFactory} defined
 * by {@link KafkaConsumerConfig}. This ensures all listeners in every
 * consuming microservice share the same production-grade defaults:
 * <ul>
 *   <li>Manual offset commit ({@code AckMode.RECORD})</li>
 *   <li>JSON deserialization with trusted packages</li>
 *   <li>Configurable concurrency</li>
 * </ul>
 *
 * <h3>Basic usage</h3>
 * <pre>{@code
 * @Component
 * public class PaymentEventConsumer {
 *
 *     @InfraKafkaListener(topics = "payment-events", groupId = "order-service")
 *     public void handlePayment(@Payload PaymentEvent event) {
 *         // business logic
 *     }
 * }
 * }</pre>
 *
 * <h3>Override group per listener</h3>
 * <pre>{@code
 * @InfraKafkaListener(
 *     topics = {"order-events", "payment-events"},
 *     groupId = "reporting-service"
 * )
 * public void handleAll(Object event) { ... }
 * }</pre>
 *
 * <p>The {@code groupId} defaults to the value of
 * {@code infra.kafka.consumer.group-id} when left empty.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@KafkaListener(
        containerFactory = "infraKafkaListenerContainerFactory",
        topics = {},
        topicPattern = "",
        groupId = "",
        id = ""
)
public @interface InfraKafkaListener {

    /**
     * One or more Kafka topic names to subscribe to.
     * Mutually exclusive with {@link #topicPattern()}.
     */
    String[] topics() default {};

    /**
     * A regex pattern for dynamic topic subscription.
     * Mutually exclusive with {@link #topics()}.
     */
    String topicPattern() default "";

    /**
     * Consumer group ID override for this listener.
     * When empty, inherits {@code infra.kafka.consumer.group-id}.
     */
    String groupId() default "";

    /**
     * Unique listener ID. Auto-generated when left empty.
     * Useful for monitoring / management endpoints.
     */
    String id() default "";

    /**
     * When {@code true}, marks this listener as participating in retry/DLQ
     * handling (Phase 3). Has no effect in Phase 2.
     */
    boolean retryable() default false;
}

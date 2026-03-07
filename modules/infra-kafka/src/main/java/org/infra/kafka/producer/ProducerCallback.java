package org.infra.kafka.producer;

import org.springframework.kafka.support.SendResult;

/**
 * Callback interface for producer send lifecycle events.
 *
 * <p>Implement this interface and register it as a Spring bean to hook into
 * producer success/failure events without extending {@link KafkaMessagePublisher}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Bean
 * public ProducerCallback myCallback() {
 *     return new ProducerCallback() {
 *
 *         @Override
 *         public void onSuccess(String topic, String key, Object payload, SendResult<String, Object> result) {
 *             metrics.increment("kafka.publish.success", "topic", topic);
 *         }
 *
 *         @Override
 *         public void onFailure(String topic, String key, Object payload, Throwable cause) {
 *             alerting.sendAlert("Kafka publish failed on " + topic, cause);
 *         }
 *     };
 * }
 * }</pre>
 */
public interface ProducerCallback {

    /**
     * Called after the broker successfully acknowledges the message.
     *
     * @param topic   the topic the message was published to
     * @param key     the partition key
     * @param payload the original payload object
     * @param result  send result containing partition and offset metadata
     */
    void onSuccess(String topic, String key, Object payload, SendResult<String, Object> result);

    /**
     * Called when the message could not be delivered to the broker after
     * all internal Kafka client retries have been exhausted.
     *
     * @param topic   the topic the message was being published to
     * @param key     the partition key
     * @param payload the original payload object
     * @param cause   the underlying exception
     */
    void onFailure(String topic, String key, Object payload, Throwable cause);
}

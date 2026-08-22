package org.infra.kafka.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link InfraKafkaListener} composes {@link KafkaListener} correctly — i.e.
 * that its attributes (and, critically, {@code containerFactory}) are merged onto the
 * meta-annotation the way Spring's {@code KafkaListenerAnnotationBeanPostProcessor} reads them.
 *
 * <p>This is the regression guard for the former no-op {@code retryable} flag: retry behaviour
 * is chosen by which container factory a listener binds to, so these tests assert that
 * selection actually flows through to the effective {@link KafkaListener#containerFactory()}.
 */
@DisplayName("InfraKafkaListener — @KafkaListener composition")
class InfraKafkaListenerTest {

    static class Sample {

        @InfraKafkaListener(topics = "payment-events", groupId = "order-service")
        void defaultsToRetryingFactory() { }

        @InfraKafkaListener(
                topics = "payment-events",
                groupId = "order-service",
                containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
        void optsOutOfRetries() { }
    }

    private KafkaListener merged(String method) throws NoSuchMethodException {
        Method m = Sample.class.getDeclaredMethod(method);
        return AnnotatedElementUtils.findMergedAnnotation(m, KafkaListener.class);
    }

    @Test
    @DisplayName("default listener resolves to the retry+DLQ container factory")
    void defaultUsesRetryingFactory() throws Exception {
        KafkaListener merged = merged("defaultsToRetryingFactory");

        assertThat(merged).isNotNull();
        assertThat(merged.containerFactory()).isEqualTo(InfraKafkaListener.RETRYING_CONTAINER_FACTORY);
        // attribute aliasing also carries topics / groupId through to @KafkaListener
        assertThat(merged.topics()).containsExactly("payment-events");
        assertThat(merged.groupId()).isEqualTo("order-service");
    }

    @Test
    @DisplayName("containerFactory override routes the listener to the no-retry factory")
    void overrideUsesNoRetryFactory() throws Exception {
        KafkaListener merged = merged("optsOutOfRetries");

        assertThat(merged).isNotNull();
        assertThat(merged.containerFactory()).isEqualTo(InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY);
        assertThat(merged.topics()).containsExactly("payment-events");
    }

    @Test
    @DisplayName("factory-name constants match the bean names declared by KafkaConsumerConfig")
    void constantsMatchBeanNames() {
        assertThat(InfraKafkaListener.RETRYING_CONTAINER_FACTORY)
                .isEqualTo("infraKafkaListenerContainerFactory");
        assertThat(InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY)
                .isEqualTo("infraKafkaNoRetryListenerContainerFactory");
    }
}

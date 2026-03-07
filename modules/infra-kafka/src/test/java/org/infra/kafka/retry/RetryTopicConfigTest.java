package org.infra.kafka.retry;

import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.infra.kafka.error.DefaultKafkaErrorHandler;
import org.infra.kafka.error.DeserializationErrorHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryTopicConfig Unit Tests")
class RetryTopicConfigTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private InfraKafkaProperties defaultProperties() {
        return new InfraKafkaProperties();
    }

    private RetryTopicConfig buildConfig(InfraKafkaProperties props) {
        return new RetryTopicConfig(props);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // infraDeserializationErrorHandler()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraDeserializationErrorHandler() creates non-null bean")
    void infraDeserializationErrorHandler_createsBean() {
        RetryTopicConfig config = buildConfig(defaultProperties());
        DeserializationErrorHandler handler =
                config.infraDeserializationErrorHandler(kafkaTemplate);

        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("infraDeserializationErrorHandler() creates bean with custom DLQ suffix")
    void infraDeserializationErrorHandler_usesCustomDlqSuffix() {
        InfraKafkaProperties props = defaultProperties();
        props.getDlq().setSuffix("-dead");

        RetryTopicConfig config = buildConfig(props);
        DeserializationErrorHandler handler =
                config.infraDeserializationErrorHandler(kafkaTemplate);

        assertThat(handler).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // infraKafkaErrorHandler()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraKafkaErrorHandler() creates non-null bean with default properties")
    void infraKafkaErrorHandler_createsBean() {
        RetryTopicConfig config = buildConfig(defaultProperties());
        DefaultKafkaErrorHandler handler = config.infraKafkaErrorHandler(kafkaTemplate);

        assertThat(handler).isNotNull();
    }

    @Test
    @DisplayName("infraKafkaErrorHandler() creates bean with custom retry settings")
    void infraKafkaErrorHandler_usesCustomRetrySettings() {
        InfraKafkaProperties props = defaultProperties();
        props.getRetry().setMaxAttempts(5);
        props.getRetry().setBackoffInitialInterval(2000L);
        props.getRetry().setBackoffMultiplier(3.0);
        props.getRetry().setBackoffMaxInterval(20000L);
        props.getDlq().setSuffix("-failed");

        RetryTopicConfig config = buildConfig(props);
        DefaultKafkaErrorHandler handler = config.infraKafkaErrorHandler(kafkaTemplate);

        assertThat(handler).isNotNull();
        assertThat(handler.getDelegate()).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // infraDlqHandler()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infraDlqHandler() creates non-null DlqHandler bean")
    void infraDlqHandler_createsBean() {
        RetryTopicConfig config = buildConfig(defaultProperties());
        DlqHandler dlqHandler = config.infraDlqHandler(kafkaTemplate);

        assertThat(dlqHandler).isNotNull();
    }
}

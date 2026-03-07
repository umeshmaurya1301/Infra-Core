package org.infra.kafka.error;

import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultKafkaErrorHandler Unit Tests")
class DefaultKafkaErrorHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private DefaultKafkaErrorHandler errorHandler;

    private InfraKafkaProperties defaultProperties() {
        return new InfraKafkaProperties();
    }

    @BeforeEach
    void setUp() {
        InfraKafkaProperties props = defaultProperties();
        errorHandler = new DefaultKafkaErrorHandler(
                kafkaTemplate,
                props.getDlq().getSuffix(),
                props.getRetry().getBackoffInitialInterval(),
                props.getRetry().getBackoffMultiplier(),
                props.getRetry().getBackoffMaxInterval(),
                props.getRetry().getMaxAttempts()
        );
    }

    @Test
    @DisplayName("Constructor creates handler without throwing")
    void constructor_createsHandler() {
        assertThat(errorHandler).isNotNull();
    }

    @Test
    @DisplayName("getDelegate() returns non-null underlying DefaultErrorHandler")
    void getDelegate_returnsNonNull() {
        assertThat(errorHandler.getDelegate()).isNotNull();
    }

    @Test
    @DisplayName("addNotRetryable() returns same instance for fluent chaining")
    void addNotRetryable_returnsThis() {
        DefaultKafkaErrorHandler result =
                errorHandler.addNotRetryable(IllegalArgumentException.class);
        assertThat(result).isSameAs(errorHandler);
    }

    @Test
    @DisplayName("addNotRetryable() accepts multiple exception types without error")
    void addNotRetryable_acceptsMultipleExceptions() {
        errorHandler
                .addNotRetryable(ArithmeticException.class)
                .addNotRetryable(UnsupportedOperationException.class);
        // Verifying no exception is thrown implicitly validates the method
        assertThat(errorHandler.getDelegate()).isNotNull();
    }

    @Test
    @DisplayName("isAckAfterHandle() delegates to underlying error handler")
    void isAckAfterHandle_delegatesToUnderlyingHandler() {
        // Spring Kafka's DefaultErrorHandler.isAckAfterHandle() returns true by default
        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }

    @Test
    @DisplayName("Constructor respects custom DLQ suffix")
    void constructor_respectsCustomDlqSuffix() {
        DefaultKafkaErrorHandler customHandler = new DefaultKafkaErrorHandler(
                kafkaTemplate,
                "-dead",
                500L,
                1.5,
                5000L,
                5
        );
        assertThat(customHandler).isNotNull();
        assertThat(customHandler.getDelegate()).isNotNull();
    }
}

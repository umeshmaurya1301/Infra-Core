package org.infra.kafka.autoconfigure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.infra.kafka.admin.KafkaAdminConfig;
import org.infra.kafka.consumer.KafkaConsumerConfig;
import org.infra.kafka.error.DefaultKafkaErrorHandler;
import org.infra.kafka.producer.KafkaMessagePublisher;
import org.infra.kafka.producer.KafkaProducerConfig;
import org.infra.kafka.security.KafkaSecurityConfig;
import org.infra.kafka.security.SaslConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.transaction.KafkaTransactionManager;

/**
 * Full-context auto-configuration tests.
 *
 * <p>These use {@link ApplicationContextRunner} to load {@link InfraKafkaAutoConfiguration}
 * exactly the way a consuming Spring Boot application would — via the auto-configuration
 * mechanism, <em>not</em> component scanning of {@code org.infra.kafka}.
 *
 * <p>They exist specifically to catch wiring bugs that per-class unit tests cannot: the
 * existing {@code KafkaProducerConfig}/{@code KafkaConsumerConfig} tests construct
 * {@link KafkaSecurityConfig} manually with {@code new}, so they never verify that Spring
 * can actually satisfy that dependency. Before the {@code @Import} list was completed,
 * {@code KafkaSecurityConfig}, {@code SaslConfigProvider}, {@code KafkaTransactionConfig},
 * {@code KafkaAdminConfig} and {@code SchemaRegistryHealthIndicator} were orphaned, so the
 * context failed to start in a real service. {@link #contextLoads_andWiresCoreBeans()} is
 * the regression guard for that.
 */
@DisplayName("InfraKafkaAutoConfiguration — full-context wiring")
class InfraKafkaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InfraKafkaAutoConfiguration.class))
            // A real app gets this from spring-boot-starter-actuator; supply one here so
            // ObservabilityConfig's Micrometer bean can be satisfied in isolation.
            .withBean(SimpleMeterRegistry.class);

    @Test
    @DisplayName("context starts and all core + security beans are wired")
    void contextLoads_andWiresCoreBeans() {
        runner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();

            // Phase 6 — the previously-orphaned beans that broke startup
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(KafkaSecurityConfig.class);
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(SaslConfigProvider.class);

            // Phase 1/2 — configs that hard-depend on KafkaSecurityConfig via constructor
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(KafkaProducerConfig.class);
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(KafkaConsumerConfig.class);

            // Public producer API + underlying factories/templates
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(KafkaMessagePublisher.class);
            // Two KafkaTemplate beans exist by design: the main producer template and a
            // separate dead-letter template used by the error handlers (KafkaProducerConfig).
            org.assertj.core.api.Assertions.assertThat(context).hasBean("infraKafkaTemplate");
            org.assertj.core.api.Assertions.assertThat(context)
                    .getBean("infraKafkaTemplate")
                    .isInstanceOf(KafkaTemplate.class);
            org.assertj.core.api.Assertions.assertThat(context).hasBean("infraKafkaDltTemplate");
            org.assertj.core.api.Assertions.assertThat(context)
                    .getBean("infraKafkaDltTemplate")
                    .isInstanceOf(KafkaTemplate.class);
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(ProducerFactory.class);
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(ConsumerFactory.class);
            org.assertj.core.api.Assertions.assertThat(context)
                    .hasBean("infraKafkaListenerContainerFactory");
            org.assertj.core.api.Assertions.assertThat(context)
                    .getBean("infraKafkaListenerContainerFactory")
                    .isInstanceOf(ConcurrentKafkaListenerContainerFactory.class);

            // Per-listener retry selection — the no-retry factory backs
            // @InfraKafkaListener(containerFactory = NON_RETRYING_CONTAINER_FACTORY)
            org.assertj.core.api.Assertions.assertThat(context)
                    .hasBean("infraKafkaNoRetryListenerContainerFactory");
            org.assertj.core.api.Assertions.assertThat(context)
                    .getBean("infraKafkaNoRetryListenerContainerFactory")
                    .isInstanceOf(ConcurrentKafkaListenerContainerFactory.class);

            // Phase 3 — both a retrying and a no-retry (immediate-DLQ) error handler exist
            org.assertj.core.api.Assertions.assertThat(context)
                    .getBeans(DefaultKafkaErrorHandler.class).hasSize(2);
            org.assertj.core.api.Assertions.assertThat(context).hasBean("infraKafkaErrorHandler");
            org.assertj.core.api.Assertions.assertThat(context).hasBean("infraKafkaNoRetryErrorHandler");
        });
    }

    @Test
    @DisplayName("transactions are OFF by default — no KafkaTransactionManager")
    void transactionManager_absentByDefault() {
        runner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(KafkaTransactionManager.class);
        });
    }

    @Test
    @DisplayName("transaction.enabled=true wires a KafkaTransactionManager (Phase 7)")
    void transactionManager_wiredWhenEnabled() {
        runner.withPropertyValues("infra.kafka.transaction.enabled=true")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(KafkaTransactionManager.class);
                });
    }

    @Test
    @DisplayName("retry is BLOCKING by default — no RetryTopicConfiguration")
    void nonBlockingRetry_absentByDefault() {
        runner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(RetryTopicConfiguration.class);
        });
    }

    @Test
    @DisplayName("retry.mode=non-blocking wires a RetryTopicConfiguration (non-blocking retry topics)")
    void nonBlockingRetry_wiredWhenModeNonBlocking() {
        runner.withPropertyValues("infra.kafka.retry.mode=non-blocking")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(RetryTopicConfiguration.class);
                    // KafkaAdmin supplied for retry/DLT topic creation
                    org.assertj.core.api.Assertions.assertThat(context)
                            .hasSingleBean(org.springframework.kafka.core.KafkaAdmin.class);
                });
    }

    @Test
    @DisplayName("admin.auto-create is OFF by default — no KafkaAdminConfig")
    void adminConfig_absentByDefault() {
        runner.run(context ->
                org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(KafkaAdminConfig.class));
    }

    @Test
    @DisplayName("infra.kafka.enabled=false disables the whole library")
    void disabled_whenPropertyFalse() {
        runner.withPropertyValues("infra.kafka.enabled=false")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(KafkaMessagePublisher.class);
                    org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean(KafkaSecurityConfig.class);
                });
    }
}

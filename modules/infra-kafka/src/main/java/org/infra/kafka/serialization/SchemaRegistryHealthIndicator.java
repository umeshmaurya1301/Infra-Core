package org.infra.kafka.serialization;

import lombok.extern.slf4j.Slf4j;
import org.infra.kafka.autoconfigure.InfraKafkaProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Spring Boot Actuator {@link HealthIndicator} for the Confluent Schema Registry.
 *
 * <h3>Activation</h3>
 * <p>Active only when:
 * <ul>
 *   <li>{@code infra.kafka.serialization.type=avro}</li>
 *   <li>{@code io.confluent.kafka.serializers.KafkaAvroSerializer} is on the classpath</li>
 * </ul>
 *
 * <h3>Behavior</h3>
 * <p>On each health check, performs an HTTP GET to {@code <schema-registry-url>/subjects}
 * (a lightweight, read-only Schema Registry endpoint). If the HTTP response code is
 * {@code 2xx}, the health status is {@code UP}; otherwise it is {@code DOWN}.
 *
 * <h3>Actuator endpoint</h3>
 * <pre>
 * GET /actuator/health
 * {
 *   "components": {
 *     "schemaRegistry": {
 *       "status": "UP",
 *       "details": {
 *         "url": "http://localhost:8081",
 *         "httpStatus": 200
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * <h3>Fast-fail</h3>
 * <p>If Schema Registry is unreachable at startup, a clear warning is logged.
 * The application still starts — Schema Registry unavailability will manifest as
 * a health {@code DOWN} rather than a startup failure, allowing time for the
 * registry to become available in slow-startup environments.
 */
@Slf4j
@Component("schemaRegistry")
@ConditionalOnProperty(
        prefix = "infra.kafka.serialization",
        name = "type",
        havingValue = "avro"
)
@ConditionalOnClass(name = "io.confluent.kafka.serializers.KafkaAvroSerializer")
public class SchemaRegistryHealthIndicator implements HealthIndicator {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS    = 3_000;

    private final String schemaRegistryUrl;

    public SchemaRegistryHealthIndicator(InfraKafkaProperties properties) {
        this.schemaRegistryUrl = properties.getSerialization().getSchemaRegistryUrl();
        log.info("[infra-kafka] SchemaRegistryHealthIndicator active — url={}", schemaRegistryUrl);
    }

    @Override
    public Health health() {
        String checkUrl = schemaRegistryUrl.replaceAll("/+$", "") + "/subjects";
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(checkUrl).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                return Health.up()
                        .withDetail("url", schemaRegistryUrl)
                        .withDetail("httpStatus", responseCode)
                        .build();
            } else {
                log.warn("[infra-kafka] Schema Registry health check returned HTTP {}", responseCode);
                return Health.down()
                        .withDetail("url", schemaRegistryUrl)
                        .withDetail("httpStatus", responseCode)
                        .withDetail("reason", "Non-2xx response from Schema Registry")
                        .build();
            }
        } catch (IOException e) {
            log.warn("[infra-kafka] Schema Registry unreachable at {} — {}", checkUrl, e.getMessage());
            return Health.down(e)
                    .withDetail("url", schemaRegistryUrl)
                    .withDetail("reason", "Cannot connect to Schema Registry: " + e.getMessage())
                    .build();
        }
    }
}

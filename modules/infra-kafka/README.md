# 📨 Infra-Kafka — Reusable Spring Boot Kafka Library Module

> **Module**: `infra-kafka` · **Group**: `org.infra` · **Version**: `1.0.0`
> **Java**: 21 · **Spring Boot**: 3.4.2 · **Kafka**: KRaft mode (no ZooKeeper)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Phase-Wise Delivery Plan](#2-phase-wise-delivery-plan)
3. [Architecture Diagram](#3-architecture-diagram)
4. [Module Structure](#4-module-structure)
5. [Features](#5-features)
6. [Kafka Concepts](#6-kafka-concepts)
7. [Consumer Project Integration](#7-consumer-project-integration)
8. [Maven & Gradle Dependency Setup](#8-maven--gradle-dependency-setup)
9. [application.yml Configuration](#9-applicationyml-configuration)
10. [Sample Producer Usage](#10-sample-producer-usage)
11. [Sample Consumer Usage](#11-sample-consumer-usage)
12. [Retry & DLQ Flow](#12-retry--dlq-flow)
13. [Error Handling & Fault Tolerance](#13-error-handling--fault-tolerance)
14. [Backward Compatibility & Schema Evolution](#14-backward-compatibility--schema-evolution)
15. [Security (SSL / SASL)](#15-security-ssl--sasl)
16. [Performance Tuning Guidelines](#16-performance-tuning-guidelines)
17. [Versioning & Upgrade Strategy](#17-versioning--upgrade-strategy)
18. [Common Pitfalls](#18-common-pitfalls)
19. [Local Setup & Testing Guide](#19-local-setup--testing-guide)
20. [FAQ](#20-faq)
21. [Example Project Structure](#21-example-project-structure)

---

## 1. Project Overview

`infra-kafka` is a **reusable, opinionated Spring Boot Kafka library** designed to be consumed as a JAR dependency by any microservice in the organization. It eliminates boilerplate by providing production-ready defaults for producers, consumers, retry/DLQ pipelines, serialization, security, and observability — while remaining fully configurable via `application.yml` or environment variables.

### Goals

| Goal | Description |
|------|-------------|
| **Zero boilerplate** | Import JAR → configure `application.yml` → produce/consume |
| **Resilient by default** | Retry with exponential backoff + DLQ out of the box |
| **Observable** | Structured logging, Micrometer metrics, correlation IDs |
| **Secure** | Optional SSL/SASL plug-and-play |
| **Evolvable** | JSON default, Avro optional, schema evolution strategy baked in |
| **KRaft native** | No ZooKeeper dependency; quorum-based controller design |

---

## 2. Phase-Wise Delivery Plan

### Phase 0 — Foundation & Project Scaffold

| Item | Detail |
|------|--------|
| **Goal** | Create the Gradle module, define dependency tree, establish package conventions |
| **Scope** | Module skeleton, `build.gradle.kts`, auto-configuration entry, base properties class |
| **Deliverables** | `infra-kafka` module compiles, publishes to Maven Local, empty auto-config loads |
| **Key Decisions** | Module lives under `modules/infra-kafka`; depends on `infra-commons` only |
| **Risks / Mitigations** | Spring Boot version drift → pin BOM via root `build.gradle.kts` |

### Phase 1 — Producer Configuration

| Item | Detail |
|------|--------|
| **Goal** | Provide a fully configurable, idempotent Kafka producer |
| **Scope** | `KafkaProducerConfig`, `KafkaMessagePublisher` service, JSON serializer, callback hooks |
| **Deliverables** | `KafkaMessagePublisher.send(topic, key, payload)` works end-to-end |
| **Key Decisions** | Idempotent producer ON by default (`enable.idempotence=true`, `acks=all`); `KafkaTemplate` wrapped |
| **Risks / Mitigations** | Broker version must support idempotent producer (≥ 0.11) → document minimum broker version |

### Phase 2 — Consumer Configuration

| Item | Detail |
|------|--------|
| **Goal** | Provide a configurable consumer with group management and manual/auto commit |
| **Scope** | `KafkaConsumerConfig`, `@InfraKafkaListener` meta-annotation, concurrency, offset strategy |
| **Deliverables** | Consumer projects annotate methods to receive messages with deserialization handled |
| **Key Decisions** | Default `AckMode = RECORD`; JSON deserializer with trusted packages; `ErrorHandler` wired |
| **Risks / Mitigations** | Poison pill messages → solved in Phase 3 (DLQ) |

### Phase 3 — Retry, DLQ & Error Handling

| Item | Detail |
|------|--------|
| **Goal** | Automated non-blocking retry with exponential backoff and dead-letter routing |
| **Scope** | `DefaultKafkaErrorHandler`, `RetryTopicConfiguration`, DLQ topic auto-creation |
| **Deliverables** | Failed messages route through `<topic>-retry-1`, `-retry-2`, … → `<topic>-dlq` |
| **Key Decisions** | Use Spring Kafka non-blocking retry (`@RetryableTopic`); max 3 retries default; multiplier 2× |
| **Risks / Mitigations** | Retry storm → cap with circuit breaker; DLQ consumer for manual replay |

### Phase 4 — Observability (Logging & Metrics)

| Item | Detail |
|------|--------|
| **Goal** | Structured logging per message, Micrometer counters/timers, correlation ID propagation |
| **Scope** | `KafkaLoggingInterceptor`, Micrometer `KafkaMetricsConfig`, MDC propagation |
| **Deliverables** | Dashboards show produce/consume rates, error rates, consumer lag |
| **Key Decisions** | Use Micrometer + Spring Boot Actuator; log at INFO on success, WARN on retry, ERROR on DLQ |
| **Risks / Mitigations** | High-throughput logging overhead → log sampling configurable |

### Phase 5 — Serialization: Avro Support (Optional)

| Item | Detail |
|------|--------|
| **Goal** | Plug-in Avro serde with Schema Registry integration |
| **Scope** | `AvroSerializer`/`AvroDeserializer`, Schema Registry URL config, `ConfluentSchemaRegistryClient` |
| **Deliverables** | Toggle between JSON and Avro via `application.yml` flag |
| **Key Decisions** | JSON is default; Avro opt-in via `infra.kafka.serialization.type=avro`; Confluent SR client |
| **Risks / Mitigations** | Schema Registry availability → fail-fast with clear error message |

### Phase 6 — Security (SSL / SASL)

| Item | Detail |
|------|--------|
| **Goal** | Zero-friction SSL/SASL configuration via properties |
| **Scope** | `KafkaSecurityConfig`, truststore/keystore path resolution, SASL/PLAIN & SCRAM support |
| **Deliverables** | Enable security with `infra.kafka.security.protocol=SASL_SSL` + credential properties |
| **Key Decisions** | Support `PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, `SASL_SSL`; keystore from classpath or file |
| **Risks / Mitigations** | Certificate rotation → document reload strategy; secret management via Vault/env |

### Phase 7 — Exactly-Once Semantics & Transactions

| Item | Detail |
|------|--------|
| **Goal** | Transactional producer + consumer-producer chaining (EOS v2) |
| **Scope** | `KafkaTransactionManager`, `@Transactional` integration, `isolation.level=read_committed` |
| **Deliverables** | Opt-in transactional mode for consume-transform-produce pipelines |
| **Key Decisions** | Transactions OFF by default (performance cost); enable via `infra.kafka.transaction.enabled=true` |
| **Risks / Mitigations** | Transaction timeout → tune `transaction.timeout.ms`; Kafka ≥ 2.5 for EOS v2 |

### Phase 8 — Advanced Features & Hardening

| Item | Detail |
|------|--------|
| **Goal** | Graceful shutdown, circuit breaker, admin operations, topic auto-creation |
| **Scope** | `KafkaAdminConfig`, `NewTopic` bean factory, `SmartLifecycle` shutdown, Resilience4j integration |
| **Deliverables** | Library handles shutdown draining, optional circuit breaker on consumer errors |
| **Key Decisions** | Topic auto-creation opt-in via config; circuit breaker off by default |
| **Risks / Mitigations** | Auto-create in prod → gate behind environment flag |

### Phase Summary Timeline

```
Phase 0  ████░░░░░░░░░░░░░░░░  Scaffold
Phase 1  ████████░░░░░░░░░░░░  Producer
Phase 2  ████████████░░░░░░░░  Consumer
Phase 3  ██████████████░░░░░░  Retry/DLQ
Phase 4  ████████████████░░░░  Observability
Phase 5  ██████████████████░░  Avro (optional)
Phase 6  ██████████████████░░  Security
Phase 7  ████████████████████  EOS
Phase 8  ████████████████████  Hardening
```

---

## 3. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Consumer Microservice                           │
│                                                                        │
│   ┌──────────────┐   ┌──────────────────┐   ┌───────────────────────┐  │
│   │ application   │   │ @InfraKafka      │   │ KafkaMessage          │  │
│   │ .yml          │──▶│ Listener         │──▶│ Publisher.send()      │  │
│   │ (properties)  │   │ (consume)        │   │ (produce)             │  │
│   └──────────────┘   └────────┬─────────┘   └──────────┬────────────┘  │
│                               │                         │               │
│   ┌───────────────────────────▼─────────────────────────▼────────────┐  │
│   │                    infra-kafka (JAR)                              │  │
│   │  ┌─────────────┐ ┌──────────────┐ ┌────────────┐ ┌───────────┐  │  │
│   │  │ Producer     │ │ Consumer     │ │ Retry/DLQ  │ │ Security  │  │  │
│   │  │ Config       │ │ Config       │ │ Pipeline   │ │ Config    │  │  │
│   │  └─────────────┘ └──────────────┘ └────────────┘ └───────────┘  │  │
│   │  ┌─────────────┐ ┌──────────────┐ ┌────────────┐ ┌───────────┐  │  │
│   │  │ Serializers  │ │ Error        │ │ Observa-   │ │ Admin     │  │  │
│   │  │ JSON / Avro  │ │ Handlers     │ │ bility     │ │ Config    │  │  │
│   │  └─────────────┘ └──────────────┘ └────────────┘ └───────────┘  │  │
│   └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Apache Kafka Cluster (KRaft)                         │
│                                                                        │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐    ┌─────────────────────┐  │
│   │ Broker 1 │  │ Broker 2 │  │ Broker 3 │    │ KRaft Controllers   │  │
│   │ (Leader) │  │(Follower)│  │(Follower)│    │ (Quorum: Raft)      │  │
│   └──────────┘  └──────────┘  └──────────┘    └─────────────────────┘  │
│                                                                        │
│   Topics: [order-events] [payment-events] [*-retry-N] [*-dlq]         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Module Structure

```
modules/infra-kafka/
├── build.gradle.kts
└── src/main/java/org/infra/kafka/
    ├── autoconfigure/
    │   ├── InfraKafkaAutoConfiguration.java
    │   └── InfraKafkaProperties.java
    ├── producer/
    │   ├── KafkaProducerConfig.java
    │   ├── KafkaMessagePublisher.java
    │   └── ProducerCallback.java
    ├── consumer/
    │   ├── KafkaConsumerConfig.java
    │   ├── InfraKafkaListener.java          // meta-annotation
    │   └── ConsumerRecordProcessor.java
    ├── retry/
    │   ├── RetryTopicConfig.java
    │   └── DlqHandler.java
    ├── error/
    │   ├── DefaultKafkaErrorHandler.java
    │   └── DeserializationErrorHandler.java
    ├── serialization/
    │   ├── JsonSerializerConfig.java
    │   └── AvroSerializerConfig.java        // optional
    ├── security/
    │   ├── KafkaSecurityConfig.java
    │   └── SaslConfigProvider.java
    ├── observability/
    │   ├── KafkaLoggingInterceptor.java
    │   └── KafkaMetricsConfig.java
    ├── admin/
    │   └── KafkaAdminConfig.java
    └── transaction/
        └── KafkaTransactionConfig.java
```

---

## 5. Features

| Feature | Default | Configurable |
|---------|---------|-------------|
| Idempotent producer | ✅ ON | `infra.kafka.producer.idempotent` |
| JSON serialization | ✅ ON | `infra.kafka.serialization.type` |
| Avro serialization | ❌ OFF | `infra.kafka.serialization.type=avro` |
| Blocking retry (in-memory backoff) | ✅ ON (3 attempts, default mode) | `infra.kafka.retry.*` |
| Non-blocking retry (retry topics) | ⚙️ Opt-in | `infra.kafka.retry.mode=non-blocking` |
| Dead Letter Queue | ✅ ON | `infra.kafka.dlq.*` |
| Structured logging | ✅ ON | `infra.kafka.logging.*` |
| Micrometer metrics | ✅ ON | `infra.kafka.metrics.enabled` |
| SSL/SASL security | ❌ OFF | `infra.kafka.security.*` |
| Transactions (EOS) | ❌ OFF | `infra.kafka.transaction.enabled` |
| Topic auto-creation | ❌ OFF | `infra.kafka.admin.auto-create` |
| Circuit breaker | ❌ OFF | `infra.kafka.circuit-breaker.enabled` |
| Graceful shutdown | ✅ ON | `infra.kafka.shutdown.*` |

---

## 6. Kafka Concepts

### 6.1 Replication Factor

Every topic partition is replicated across `N` brokers. With `replication.factor=3`, three copies exist so the cluster tolerates up to 2 broker failures.

```
Topic: order-events,  Partition 0
  Replica 0 (Broker 1) ← Leader
  Replica 1 (Broker 2) ← Follower (ISR)
  Replica 2 (Broker 3) ← Follower (ISR)
```

### 6.2 Leader / Follower

Each partition has exactly **one leader** that handles all reads and writes. Followers replicate from the leader. If the leader fails, a follower from the ISR is elected.

### 6.3 ISR (In-Sync Replicas)

The set of replicas that are fully caught up with the leader. Only ISR members are eligible for leader election. Controlled by `replica.lag.time.max.ms`.

### 6.4 Quorum & KRaft Controllers

KRaft replaces ZooKeeper with a **Raft-based quorum** of controller nodes. A majority (⌊N/2⌋+1) must agree on metadata changes. Typical setup: 3 controllers tolerating 1 failure.

```
Controller Quorum (KRaft)
  Controller 1 ← Active Controller (Leader)
  Controller 2 ← Voter
  Controller 3 ← Voter
  Majority needed: 2 out of 3
```

### 6.5 Fault Tolerance

| Scenario | Replication Factor | Tolerated Failures |
|----------|-------------------|--------------------|
| Dev | 1 | 0 |
| Staging | 2 | 1 |
| Production | 3 | 2 |

**Formula**: `tolerated failures = replication.factor - 1` (with `min.insync.replicas = replication.factor - 1`)

### 6.6 Acknowledgement Modes

| `acks` | Behavior | Durability | Latency |
|--------|----------|-----------|---------|
| `0` | Fire-and-forget | ❌ None | ⚡ Lowest |
| `1` | Leader acknowledges | ⚠️ Partial | 🔵 Medium |
| `all` | All ISR acknowledge | ✅ Full | 🔴 Highest |

**Library default**: `acks=all` for maximum durability.

### 6.7 Idempotent Producer

Prevents duplicate messages caused by producer retries. Enabled by setting `enable.idempotence=true` (requires `acks=all`, `retries > 0`). The broker assigns a **Producer ID (PID)** and **sequence number** to deduplicate.

### 6.8 Exactly-Once Semantics (EOS v2)

Builds on idempotent producer by adding **transactions**. A consume-transform-produce pipeline atomically commits offsets and output messages. Requires `transactional.id`, `isolation.level=read_committed`.

```
Consumer.poll() → Process → Producer.send() → txn.commitTransaction()
                                              └─ offsets committed atomically
```

### 6.9 Consumer Group Rebalancing

When consumers join/leave a group, partitions are redistributed. Strategies:

| Strategy | Behavior |
|----------|----------|
| `RangeAssignor` | Assigns contiguous partitions per topic |
| `RoundRobinAssignor` | Round-robin across all topics |
| `StickyAssignor` | Minimizes partition movement |
| `CooperativeStickyAssignor` | Incremental rebalance (recommended) |

**Library default**: `CooperativeStickyAssignor` — minimizes stop-the-world rebalances.

### 6.10 Partitioning Strategy

- **Key-based** (default): `hash(key) % numPartitions` — guarantees ordering per key.
- **Round-robin**: When key is `null` — maximizes throughput.
- **Custom**: Implement `Partitioner` interface for business logic routing.

---

## 7. Consumer Project Integration

### Step-by-step

1. **Add dependency** (Maven or Gradle — see §8)
2. **Configure `application.yml`** (see §9)
3. **Inject `KafkaMessagePublisher`** to produce messages
4. **Annotate methods with `@InfraKafkaListener`** to consume messages
5. **Auto-configuration** wires everything — no `@Configuration` needed in consumer project

```java
// That's it — the library handles all config, retry, DLQ, logging
@Service
public class OrderService {

    private final KafkaMessagePublisher publisher;

    public OrderService(KafkaMessagePublisher publisher) {
        this.publisher = publisher;
    }

    public void placeOrder(Order order) {
        publisher.send("order-events", order.getId(), order);
    }

    @InfraKafkaListener(topics = "payment-events", groupId = "order-service")
    public void onPayment(PaymentEvent event) {
        // process event
    }
}
```

---

## 8. Maven & Gradle Dependency Setup

### Maven

```xml
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-kafka</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Required: infra-commons (transitive, but explicit is safer) -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-commons</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Optional: Avro support -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-kafka</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.confluent</groupId>
    <artifactId>kafka-avro-serializer</artifactId>
    <version>7.6.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.infra:infra-kafka:1.0.0")
    implementation("org.infra:infra-commons:1.0.0")

    // Optional: Avro support
    // implementation("io.confluent:kafka-avro-serializer:7.6.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'org.infra:infra-kafka:1.0.0'
    implementation 'org.infra:infra-commons:1.0.0'
}
```

---

## 9. application.yml Configuration

```yaml
# ============================================================
# Consumer project application.yml — Infra-Kafka configuration
# ============================================================

infra:
  kafka:
    # ── Bootstrap ──
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094

    # ── Producer ──
    producer:
      acks: all                          # 0 | 1 | all
      retries: 3
      batch-size: 16384                  # bytes
      linger-ms: 5                       # ms to wait before sending batch
      buffer-memory: 33554432            # 32 MB
      compression-type: snappy           # none | gzip | snappy | lz4 | zstd
      idempotent: true
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

    # ── Consumer ──
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest        # earliest | latest | none
      enable-auto-commit: false
      max-poll-records: 500
      concurrency: 3                     # listener threads
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      trusted-packages: "com.yourcompany.*"   # secure by default: empty trusts NOTHING; set your model package(s). "*" is dev-only.
      partition-assignment-strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor

    # ── Retry ──
    retry:
      enabled: true
      mode: blocking                     # blocking (default) | non-blocking
      max-attempts: 3
      backoff-initial-interval: 1000     # ms
      backoff-multiplier: 2.0
      backoff-max-interval: 10000        # ms
      # Non-blocking (retry-topic) mode only:
      retry-topic-suffix: "-retry"
      auto-create-retry-topics: true
      retry-topic-partitions: 1
      retry-topic-replication-factor: 1

    # ── Dead Letter Queue ──
    dlq:
      enabled: true
      suffix: "-dlq"                     # topic naming: order-events-dlq

    # ── Serialization ──
    serialization:
      type: json                         # json | avro
      schema-registry-url: http://localhost:8081   # required if avro

    # ── Security (optional) ──
    security:
      protocol: PLAINTEXT               # PLAINTEXT | SSL | SASL_PLAINTEXT | SASL_SSL
      sasl:
        mechanism: PLAIN                 # PLAIN | SCRAM-SHA-256 | SCRAM-SHA-512
        username: ""
        password: ""
      ssl:
        truststore-location: ""
        truststore-password: ""
        keystore-location: ""
        keystore-password: ""

    # ── Transactions (optional) ──
    transaction:
      enabled: false
      id-prefix: "txn-${spring.application.name}-"

    # ── Admin / Topic creation (optional) ──
    admin:
      auto-create: false
      topics:
        - name: order-events
          partitions: 6
          replication-factor: 3
        - name: payment-events
          partitions: 3
          replication-factor: 3

    # ── Observability ──
    logging:
      enabled: true
      log-payload: false                 # true in dev, false in prod
    metrics:
      enabled: true

    # ── Shutdown ──
    shutdown:
      timeout: 30s
```

---

## 10. Sample Producer Usage

```java
import org.infra.kafka.producer.KafkaMessagePublisher;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final KafkaMessagePublisher publisher;

    public OrderService(KafkaMessagePublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publish an order event with the order ID as the partition key.
     * Ordering is guaranteed per key.
     */
    public void publishOrderCreated(Order order) {
        publisher.send("order-events", order.getId(), new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal(),
            java.time.Instant.now()
        ));
    }

    /**
     * Publish with explicit headers (e.g., correlation ID, event type).
     */
    public void publishWithHeaders(Order order) {
        publisher.send("order-events", order.getId(), new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal(),
            java.time.Instant.now()
        ), headers -> {
            headers.add("X-Correlation-Id", correlationId().getBytes());
            headers.add("X-Event-Type", "ORDER_CREATED".getBytes());
        });
    }

    /**
     * Synchronous send — blocks until broker acknowledges.
     */
    public void publishSync(Order order) {
        var result = publisher.sendAndWait("order-events", order.getId(), order);
        log.info("Sent to partition={} offset={}", 
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset());
    }
}
```

---

## 11. Sample Consumer Usage

```java
import org.infra.kafka.consumer.InfraKafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    /**
     * Basic consumer — JSON deserialization handled by library.
     */
    @InfraKafkaListener(topics = "payment-events", groupId = "order-service")
    public void handlePayment(@Payload PaymentEvent event,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received payment event: {} from partition={} offset={}",
            event.getPaymentId(), partition, offset);
        // business logic
    }

    /**
     * Consume with retry — the DEFAULT behaviour. On failure the record is retried
     * with exponential backoff (configured via infra.kafka.retry.*) and then routed
     * to the DLQ. No extra attribute is needed.
     */
    @InfraKafkaListener(topics = "inventory-events", groupId = "inventory-service")
    public void handleInventory(@Payload InventoryEvent event) {
        inventoryService.reserve(event);  // may throw → retried, then DLQ
    }

    /**
     * Opt OUT of retries — failures go straight to the DLQ on the first exception.
     * Useful for non-transient / non-retryable event types.
     */
    @InfraKafkaListener(
        topics = "audit-events",
        groupId = "inventory-service",
        containerFactory = InfraKafkaListener.NON_RETRYING_CONTAINER_FACTORY
    )
    public void handleAudit(@Payload AuditEvent event) {
        auditService.record(event);  // may throw → straight to audit-events-dlq
    }
}
```

> **Note:** retry behaviour is a property of the container factory, so it is selected
> per listener via `containerFactory`. The default (`RETRYING_CONTAINER_FACTORY`) retries
> then DLQs; `NON_RETRYING_CONTAINER_FACTORY` sends failures straight to the DLQ. (This
> replaces the earlier `retryable` flag, which had no effect.)

---

## 12. Retry & DLQ Flow

### Retry modes

The library supports two retry strategies, selected via `infra.kafka.retry.mode`:

| Mode | `infra.kafka.retry.mode` | How it works | Trade-off |
|------|--------------------------|--------------|-----------|
| **Blocking** (default) | `blocking` | The container retries the record **in place** with an in-memory exponential backoff, then routes to the DLQ. | Simple; preserves per-partition ordering. But the partition is **paused** while a record backs off, so long backoffs stall the consumer. |
| **Non-blocking** | `non-blocking` | Failed records are **forwarded to dedicated retry topics** (`<topic>-retry-0`, `-retry-1`, …) and re-consumed after a delay; exhausted records land in the DLT (`<topic>-dlq`). Built on Spring Kafka's `@RetryableTopic` infrastructure. | The original partition never blocks (higher throughput/availability). But retried records may be processed **out of order** relative to the source partition, and the cluster must allow retry/DLT topic creation. |

**Non-blocking mode** additionally creates a `KafkaAdmin` (pointed at `infra.kafka.bootstrap-servers`) to auto-create the retry/DLT topics, controllable via `infra.kafka.retry.auto-create-retry-topics` / `retry-topic-partitions` / `retry-topic-replication-factor`. The per-listener `NON_RETRYING_CONTAINER_FACTORY` opt-out (see §11) applies to **blocking** mode; in non-blocking mode the retry-topic configuration governs all listeners globally.

The diagram below illustrates the **non-blocking** retry-topic flow.

### Flow Diagram

```
                        ┌─────────────┐
  Producer ────────────▶│ order-events │
                        └──────┬──────┘
                               │
                        ┌──────▼──────┐
                        │  Consumer    │
                        │  Listener    │
                        └──────┬──────┘
                               │
                    ┌──── Success? ────┐
                    │                  │
                   YES                 NO
                    │                  │
                    ▼                  ▼
               ┌─────────┐   ┌────────────────────┐
               │ Commit   │   │ order-events-retry-1│  (delay: 1s)
               │ Offset   │   └────────┬───────────┘
               └─────────┘            │
                               ┌──── Success? ────┐
                               │                  │
                              YES                 NO
                               │                  ▼
                               ▼         ┌────────────────────┐
                          ┌─────────┐    │ order-events-retry-2│  (delay: 2s)
                          │ Commit  │    └────────┬───────────┘
                          └─────────┘            │
                                          ┌──── Success? ────┐
                                          │                  │
                                         YES                 NO
                                          │                  ▼
                                          ▼         ┌────────────────────┐
                                     ┌─────────┐    │ order-events-retry-3│ (delay: 4s)
                                     │ Commit  │    └────────┬───────────┘
                                     └─────────┘            │
                                                     ┌──── Success? ────┐
                                                     │                  │
                                                    YES                 NO
                                                     │                  ▼
                                                     ▼        ┌──────────────────┐
                                                ┌─────────┐   │ order-events-dlq │
                                                │ Commit  │   │ (Dead Letter)    │
                                                └─────────┘   └──────────────────┘
                                                               Alert + Manual Review
```

### Retry Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `infra.kafka.retry.max-attempts` | `3` | Number of retry attempts before DLQ |
| `infra.kafka.retry.backoff-initial-interval` | `1000ms` | Initial delay |
| `infra.kafka.retry.backoff-multiplier` | `2.0` | Exponential multiplier |
| `infra.kafka.retry.backoff-max-interval` | `10000ms` | Cap on delay |

### DLQ Processing

Messages in the DLQ retain all original headers plus:
- `X-Original-Topic` — source topic
- `X-Exception-Class` — exception type
- `X-Exception-Message` — error description
- `X-Retry-Count` — number of attempts before DLQ

---

## 13. Error Handling & Fault Tolerance

### Error Classification

| Error Type | Example | Strategy |
|-----------|---------|----------|
| **Transient** | Network timeout, broker unavailable | Retry with backoff |
| **Deserialization** | Corrupt message, schema mismatch | Route to DLQ immediately (no retry) |
| **Business logic** | Validation failure, null field | Retry if idempotent, else DLQ |
| **Fatal** | Out of memory, configuration error | Stop container, alert |

### Error Handler Chain

```
Message received
    │
    ▼
┌──────────────────────────┐
│ DeserializationErrorHandler │ ← Catches corrupt/malformed messages
│ → Routes directly to DLQ   │    (skips retry — retrying won't help)
└────────────┬─────────────┘
             │ (deserialization OK)
             ▼
┌──────────────────────────┐
│ Business Logic Execution   │
│ → @InfraKafkaListener body │
└────────────┬─────────────┘
             │ (exception thrown)
             ▼
┌──────────────────────────┐
│ DefaultKafkaErrorHandler   │
│ → Classify exception       │
│ → Retryable? → retry topic │
│ → Not retryable? → DLQ    │
└──────────────────────────┘
```

### Non-Retryable Exceptions (skip retry, go to DLQ)

Configure exceptions that should **never** be retried:

```java
// In your application configuration
@Bean
public InfraKafkaErrorConfig errorConfig() {
    return InfraKafkaErrorConfig.builder()
        .addNotRetryable(InvalidSchemaException.class)
        .addNotRetryable(IllegalArgumentException.class)
        .addNotRetryable(JsonParseException.class)
        .build();
}
```

### Fault Tolerance Summary

| Mechanism | Protection Against |
|-----------|--------------------|
| Idempotent producer | Duplicate messages from producer retries |
| `acks=all` + `min.insync.replicas` | Data loss on broker failure |
| Consumer offset commit after processing | Reprocessing on consumer crash |
| Non-blocking retry topics | Transient downstream failures |
| DLQ | Poison pill / unrecoverable messages |
| Graceful shutdown | In-flight message loss during deploy |
| Circuit breaker (optional) | Cascading failure from downstream |

---

## 14. Backward Compatibility & Schema Evolution

### 14.1 JSON Schema Evolution Rules

| Change | Compatible? | Notes |
|--------|------------|-------|
| Add optional field (with default) | ✅ Yes | Old consumers ignore new field |
| Remove optional field | ✅ Yes | New consumers handle missing field |
| Rename field | ❌ No | Use `@JsonAlias` for migration period |
| Change field type | ❌ No | Create new event version |
| Add required field (no default) | ❌ No | Old consumers break |

**Best Practice**: Always add new fields as **optional with defaults**. Use `@JsonIgnoreProperties(ignoreUnknown = true)` on all DTOs.

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
    String orderId,
    String customerId,
    BigDecimal total,
    Instant timestamp,
    @JsonProperty(defaultValue = "UNKNOWN") String source  // safe addition
) {}
```

### 14.2 Avro Schema Evolution (when using Avro)

| Compatibility Mode | Allowed Changes |
|-------------------|-----------------|
| `BACKWARD` | Delete fields, add optional fields |
| `FORWARD` | Add fields, delete optional fields |
| `FULL` | Add/delete only optional fields |
| `NONE` | Any change (unsafe) |

**Library default**: `BACKWARD` compatibility in Schema Registry.

### 14.3 Versioned Topics Strategy

For breaking changes, use **versioned topics**:

```
order-events-v1   ← existing consumers
order-events-v2   ← new consumers with breaking schema
```

**Migration pattern**:
1. Deploy new consumers reading from `v2`
2. Deploy producers writing to both `v1` and `v2` (dual-write period)
3. Migrate remaining consumers from `v1` to `v2`
4. Deprecate and delete `v1`

### 14.4 Library API Versioning

| Version | Status | Support |
|---------|--------|---------|
| `1.x` | Current | Active development |
| `0.x` | Deprecated | Security fixes only for 6 months |

**Deprecation policy**: Annotate with `@Deprecated(since = "1.2", forRemoval = true)` → remove in next major version.

### 14.5 Kafka Client Compatibility

| Library Kafka Client | Min Broker Version | Max Broker Version |
|---------------------|-------------------|--------------------|
| 3.7.x | 2.1 | latest |

Kafka clients are backward compatible with older brokers. The library documents and tests against the minimum supported broker version.

---

## 15. Security (SSL / SASL)

### 15.1 Protocol Options

| Protocol | Encryption | Authentication | Use Case |
|----------|-----------|----------------|----------|
| `PLAINTEXT` | ❌ | ❌ | Local dev |
| `SSL` | ✅ | ✅ (mutual TLS) | Encryption only |
| `SASL_PLAINTEXT` | ❌ | ✅ | Auth without encryption |
| `SASL_SSL` | ✅ | ✅ | **Production recommended** |

### 15.2 SASL Configuration

```yaml
infra:
  kafka:
    security:
      protocol: SASL_SSL
      sasl:
        mechanism: SCRAM-SHA-512
        username: ${KAFKA_USERNAME}
        password: ${KAFKA_PASSWORD}
      ssl:
        truststore-location: classpath:kafka-truststore.jks
        truststore-password: ${KAFKA_TRUSTSTORE_PASS}
```

### 15.3 Mutual TLS (mTLS)

```yaml
infra:
  kafka:
    security:
      protocol: SSL
      ssl:
        truststore-location: /certs/truststore.jks
        truststore-password: ${TRUSTSTORE_PASS}
        keystore-location: /certs/keystore.jks
        keystore-password: ${KEYSTORE_PASS}
        key-password: ${KEY_PASS}
```

### 15.4 Security Best Practices

- **Never** hardcode credentials — use environment variables or Vault
- Rotate certificates on a schedule; the library supports JKS and PKCS12 formats
- Use **SCRAM-SHA-512** over PLAIN (PLAIN sends credentials in cleartext)
- Enable ACLs on the Kafka cluster to restrict topic access per service

---

## 16. Performance Tuning Guidelines

### Producer Tuning

| Property | Default | Tune For Throughput | Tune For Latency |
|----------|---------|--------------------|--------------------|
| `batch.size` | 16 KB | 64–128 KB | 0 (disable batching) |
| `linger.ms` | 5 ms | 50–100 ms | 0 |
| `compression.type` | snappy | lz4 or zstd | none |
| `buffer.memory` | 32 MB | 64 MB+ | 32 MB |
| `acks` | all | 1 (if loss acceptable) | all |
| `max.in.flight.requests` | 5 | 5 | 1 (strict ordering) |

### Consumer Tuning

| Property | Default | Tune For Throughput | Tune For Latency |
|----------|---------|--------------------|--------------------|
| `fetch.min.bytes` | 1 | 50 KB+ | 1 |
| `fetch.max.wait.ms` | 500 ms | 500 ms | 100 ms |
| `max.poll.records` | 500 | 1000+ | 10–50 |
| `concurrency` | 3 | = partition count | 1–3 |
| `max.poll.interval.ms` | 300s | Increase for slow processing | Default |

### Sizing Rules of Thumb

| Decision | Guideline |
|----------|-----------|
| Partitions per topic | Start with `max(throughput_MB/s, consumer_instances)`. Over-partitioning hurts. |
| Replication factor | 3 for production, 1 for dev |
| Consumer concurrency | ≤ partition count (more threads than partitions waste resources) |
| Batch size × linger | Trade latency for throughput |

---

## 17. Versioning & Upgrade Strategy

### Semantic Versioning

The library follows **SemVer** (`MAJOR.MINOR.PATCH`):

| Version Bump | When | Example |
|-------------|------|---------|
| PATCH `1.0.x` | Bug fixes, dependency updates | Fix DLQ header encoding |
| MINOR `1.x.0` | New features, backward-compatible | Add circuit breaker support |
| MAJOR `x.0.0` | Breaking changes | Change retry topic naming convention |

### Upgrade Checklist

1. **Read CHANGELOG.md** for the target version
2. **Check deprecated APIs** — search for `@Deprecated` in your codebase
3. **Update dependency version** in `build.gradle.kts` / `pom.xml`
4. **Run tests** — integration tests with embedded Kafka
5. **Deploy to staging** — verify metrics, DLQ behavior, consumer lag
6. **Roll out canary** → blue/green → full production

### Rolling Upgrade (Zero Downtime)

```
Step 1: Deploy new version to Service Instance A  (Instance B on old version)
Step 2: Verify Instance A healthy (consumer lag, error rate)
Step 3: Deploy new version to Service Instance B
Step 4: Verify cluster-wide
```

---

## 18. Common Pitfalls

### ❌ Pitfall 1: Not setting `trusted-packages`

**Problem**: `trusted-packages` is **empty by default** (secure by default — no package is blanket-trusted). If you rely on type-info headers, `JsonDeserializer` rejects messages from untrusted packages.
**Fix**: Set `infra.kafka.consumer.trusted-packages: "com.yourcompany.*"` to your event-model package(s). Avoid `"*"` outside local dev — it trusts every package and is a deserialization-attack vector; the library logs a `SECURITY` warning at startup when it sees `"*"`.

### ❌ Pitfall 2: Consumer concurrency > partition count

**Problem**: Extra consumer threads sit idle, wasting resources.
**Fix**: Set `infra.kafka.consumer.concurrency` ≤ number of partitions for the topic.

### ❌ Pitfall 3: Long processing without increasing `max.poll.interval.ms`

**Problem**: Consumer is kicked from group → rebalance → duplicate processing.
**Fix**: Increase `max.poll.interval.ms` or reduce `max.poll.records`.

### ❌ Pitfall 4: Ignoring DLQ messages

**Problem**: DLQ fills up with unprocessed messages; data silently lost.
**Fix**: Set up DLQ monitoring alerts + scheduled DLQ consumer for review/replay.

### ❌ Pitfall 5: Using `acks=0` in production

**Problem**: Messages lost on broker failure.
**Fix**: Always use `acks=all` with `min.insync.replicas ≥ 2` in production.

### ❌ Pitfall 6: Auto-creating topics in production

**Problem**: Typos create unintended topics; no control over partitions/replication.
**Fix**: Set `infra.kafka.admin.auto-create=false` in production; manage topics via IaC.

### ❌ Pitfall 7: Not using `@JsonIgnoreProperties(ignoreUnknown = true)`

**Problem**: Adding a field to an event breaks all existing consumers.
**Fix**: Always annotate event DTOs with `@JsonIgnoreProperties(ignoreUnknown = true)`.

### ❌ Pitfall 8: Committing offsets before processing

**Problem**: Message lost if processing fails after commit.
**Fix**: Use `AckMode.RECORD` (library default) — commit **after** successful processing.

---

## 19. Local Setup & Testing Guide

### 19.1 Start Kafka (KRaft Mode) with Docker Compose

```yaml
# docker-compose.kafka.yml
version: '3.8'
services:
  kafka:
    image: apache/kafka:3.7.0
    hostname: kafka
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_LOG_DIRS: /tmp/kraft-combined-logs
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

```bash
docker compose -f docker-compose.kafka.yml up -d
```

### 19.2 Verify Kafka is Running

```bash
# Create a test topic
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --create --topic test-topic --partitions 3 --replication-factor 1 \
  --bootstrap-server localhost:9092

# List topics
docker exec kafka /opt/kafka/bin/kafka-topics.sh \
  --list --bootstrap-server localhost:9092
```

### 19.3 Integration Tests with Embedded Kafka

```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-topic", "test-topic-dlq"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
class KafkaIntegrationTest {

    @Autowired
    private KafkaMessagePublisher publisher;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishAndConsumeMessage() {
        // Given
        var event = new OrderCreatedEvent("ORD-001", "CUST-001",
            new BigDecimal("99.99"), Instant.now());

        // When
        publisher.send("test-topic", "ORD-001", event);

        // Then — verify via test consumer or ConsumerRecord
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
            consumer, "test-topic", Duration.ofSeconds(10));
        assertThat(record.value()).contains("ORD-001");
    }

    @Test
    void shouldRouteFailedMessageToDlq() {
        // Given — a message that will cause processing failure
        publisher.send("test-topic", "BAD-KEY", new PoisonPillEvent());

        // Then — verify it lands in DLQ
        ConsumerRecord<String, String> dlqRecord = KafkaTestUtils.getSingleRecord(
            dlqConsumer, "test-topic-dlq", Duration.ofSeconds(30));
        assertThat(dlqRecord).isNotNull();
        assertThat(new String(dlqRecord.headers().lastHeader("X-Original-Topic").value()))
            .isEqualTo("test-topic");
    }
}
```

### 19.4 Test Configuration

```yaml
# src/test/resources/application-test.yml
infra:
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers}
    consumer:
      group-id: test-group
      auto-offset-reset: earliest
    retry:
      max-attempts: 1
      backoff-initial-interval: 100
    dlq:
      enabled: true
```

---

## 20. FAQ

**Q: Do consumer projects need to write any `@Configuration` classes?**
A: No. The library auto-configures everything. Just add the dependency and set `application.yml`.

**Q: Can I override the library's producer/consumer beans?**
A: Yes. Define your own `@Bean` for `KafkaTemplate`, `ConsumerFactory`, etc. Spring Boot's `@ConditionalOnMissingBean` ensures the library backs off.

**Q: How do I consume from multiple topics in one listener?**
A: Use `@InfraKafkaListener(topics = {"topic-a", "topic-b"})`.

**Q: How do I replay DLQ messages?**
A: Consume from the DLQ topic, fix the issue, and re-publish to the original topic. The library provides a `DlqReplayService` utility (Phase 8).

**Q: Is Avro required?**
A: No. JSON is the default. Avro is opt-in via `infra.kafka.serialization.type=avro` and requires a Schema Registry.

**Q: Can I use this library with Kafka running in ZooKeeper mode?**
A: Yes. The library is broker-protocol agnostic. KRaft vs ZooKeeper is a server-side concern; the client uses the same bootstrap protocol.

**Q: How do I monitor consumer lag?**
A: Enable actuator (`spring-boot-starter-actuator`) and Micrometer. The library exposes `kafka.consumer.lag` metrics. Use Grafana/Prometheus for dashboards.

**Q: What happens during a rolling deployment?**
A: The library implements `SmartLifecycle` for graceful shutdown — it stops polling, finishes in-flight records, commits offsets, then shuts down. `CooperativeStickyAssignor` minimizes rebalance disruption.

**Q: Can different services use different serialization formats?**
A: Yes. Each consumer project configures its own `application.yml`. Services can independently choose JSON or Avro.

**Q: What's the minimum Kafka broker version?**
A: Kafka 2.1+ is supported. For EOS v2 (exactly-once), Kafka 2.5+ is required.

---

## 21. Example Project Structure

### Consumer Microservice — `order-service`

```
order-service/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/example/orderservice/
│   │   │   ├── OrderServiceApplication.java
│   │   │   ├── config/                          // empty — no Kafka config needed!
│   │   │   ├── domain/
│   │   │   │   ├── Order.java
│   │   │   │   └── OrderRepository.java
│   │   │   ├── event/
│   │   │   │   ├── OrderCreatedEvent.java
│   │   │   │   └── PaymentCompletedEvent.java
│   │   │   ├── producer/
│   │   │   │   └── OrderEventPublisher.java     // injects KafkaMessagePublisher
│   │   │   └── consumer/
│   │   │       └── PaymentEventConsumer.java     // @InfraKafkaListener
│   │   └── resources/
│   │       ├── application.yml                   // infra.kafka.* properties
│   │       └── application-local.yml
│   └── test/
│       ├── java/com/example/orderservice/
│       │   └── kafka/
│       │       └── KafkaIntegrationTest.java
│       └── resources/
│           └── application-test.yml
└── docker-compose.kafka.yml                      // local Kafka (KRaft)
```

### `build.gradle.kts`

```kotlin
plugins {
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    mavenLocal()      // for infra-kafka from local Maven
}

dependencies {
    // Infra libraries
    implementation("org.infra:infra-commons:1.0.0")
    implementation("org.infra:infra-kafka:1.0.0")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
```

### `OrderEventPublisher.java`

```java
package com.example.orderservice.producer;

import org.infra.kafka.producer.KafkaMessagePublisher;
import com.example.orderservice.event.OrderCreatedEvent;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class OrderEventPublisher {

    private final KafkaMessagePublisher publisher;

    public OrderEventPublisher(KafkaMessagePublisher publisher) {
        this.publisher = publisher;
    }

    public void publishOrderCreated(String orderId, String customerId) {
        var event = new OrderCreatedEvent(orderId, customerId, Instant.now());
        publisher.send("order-events", orderId, event);
    }
}
```

### `PaymentEventConsumer.java`

```java
package com.example.orderservice.consumer;

import org.infra.kafka.consumer.InfraKafkaListener;
import com.example.orderservice.event.PaymentCompletedEvent;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentEventConsumer {

    @InfraKafkaListener(
        topics = "payment-events",
        groupId = "order-service"
    )
    public void onPaymentCompleted(@Payload PaymentCompletedEvent event) {
        log.info("Payment completed for order: {}", event.orderId());
        // Update order status, trigger shipment, etc.
    }
}
```

---

## Appendix: Quick Reference Card

```
┌──────────────────────────────────────────────────────────────────────┐
│                     infra-kafka Quick Reference                      │
├──────────────────────────────────────────────────────────────────────┤
│ Add Dependency  │ implementation("org.infra:infra-kafka:1.0.0")     │
│ Produce         │ publisher.send("topic", key, payload)             │
│ Consume         │ @InfraKafkaListener(topics="topic", groupId="g")  │
│ Retry (3×)      │ Automatic — configure via infra.kafka.retry.*     │
│ DLQ             │ Automatic — <topic>-dlq                           │
│ Security        │ infra.kafka.security.protocol=SASL_SSL            │
│ Avro            │ infra.kafka.serialization.type=avro               │
│ Transactions    │ infra.kafka.transaction.enabled=true               │
│ Metrics         │ /actuator/prometheus → kafka.* counters           │
│ Local Kafka     │ docker compose -f docker-compose.kafka.yml up     │
└──────────────────────────────────────────────────────────────────────┘
```

---

> **Maintained by**: Platform Engineering Team
> **Last updated**: 2026-02-24
> **License**: Internal — Not for external distribution

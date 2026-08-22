plugins {
    // java-library (not plain java) so we can use `api(...)` and expose Spring Kafka
    // types on consumers' compile classpath — see the spring-kafka dependency below.
    id("java-library")
    id("maven-publish")
    id("jacoco")
}

description = "Reusable Spring Boot Kafka library — idempotent producer, consumer, retry/DLQ, observability, Avro"

// ── Confluent Schema Registry repository (needed for kafka-avro-serializer) ──
repositories {
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

dependencies {
    // ── Module dependencies ──────────────────────────────────────────────
    implementation(project(":infra-commons"))

    // ── Spring Kafka ──────────────────────────────────────────────────────
    // Exposed as `api` (not `implementation`): the library's public surface returns
    // and accepts Spring Kafka / kafka-clients types — e.g. KafkaMessagePublisher.send(..)
    // returns CompletableFuture<SendResult<..>> and accepts Consumer<Headers>, and
    // @InfraKafkaListener is meta-annotated with @KafkaListener. Consumers must be able to
    // `import org.springframework.kafka.*` / `org.apache.kafka.*` without redeclaring it.
    // spring-kafka transitively brings kafka-clients and spring-messaging (@Payload/@Header).
    api("org.springframework.kafka:spring-kafka")

    // ── Spring Boot ───────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // KafkaMetricsConfig/ObservabilityConfig (io.micrometer.core.instrument.*) and
    // SchemaRegistryHealthIndicator (org.springframework.boot.health.contributor.*).
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ── JSON ──────────────────────────────────────────────────────────────
    implementation("tools.jackson.core:jackson-databind")

    // ── Phase 5: Avro (optional — compileOnly so consumers opt in explicitly) ──
    // Consumer projects must add:  implementation("io.confluent:kafka-avro-serializer:7.9.0")
    compileOnly("io.confluent:kafka-avro-serializer:7.9.0")
    testImplementation("io.confluent:kafka-avro-serializer:7.9.0")
    testImplementation("org.apache.avro:avro:1.12.0")

    // ── Lombok ────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
}

tasks.jar {
    enabled = true
    archiveClassifier = ""
}

// ── Javadoc doclint ───────────────────────────────────────────────────────
// The published javadoc jar (root applies withJavadocJar() to every module) runs
// the `javadoc` task on `build`. Keep the doclint groups that catch genuine breakage
// — `reference` (broken {@link}/{@code} targets) and `syntax` (malformed tags) — but
// don't fail the build on `html`/`accessibility` style nits (heading levels, table
// captions) or `missing` comment warnings. This is the same posture the Spring
// Framework uses for its own javadoc.
tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:all,-html,-accessibility,-missing", "-quiet")
    }
}

// Configure publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            pom {
                name.set(project.name)
                description.set(project.description ?: "Reusable Spring Boot Kafka library module")
                url.set("https://github.com/yourusername/infra-core")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("yourusername")
                        name.set("Your Name")
                        email.set("your.email@example.com")
                    }
                }
            }
        }
    }
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

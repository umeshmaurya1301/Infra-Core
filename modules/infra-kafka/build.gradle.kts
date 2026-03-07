plugins {
    id("java")
    id("maven-publish")
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
    implementation("org.springframework.kafka:spring-kafka")

    // ── Spring Boot ───────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // ── JSON ──────────────────────────────────────────────────────────────
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ── Phase 5: Avro (optional — compileOnly so consumers opt in explicitly) ──
    // Consumer projects must add:  implementation("io.confluent:kafka-avro-serializer:7.9.0")
    compileOnly("io.confluent:kafka-avro-serializer:7.9.0")
    testImplementation("io.confluent:kafka-avro-serializer:7.9.0")
    testImplementation("org.apache.avro:avro:1.12.0")

    // ── Lombok ────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.mockito:mockito-core:${property("mockitoVersion")}")
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
}

tasks.jar {
    enabled = true
    archiveClassifier = ""
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

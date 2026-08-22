plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "Micrometer timers, percentile histograms, OpenTelemetry spans, and rolling per-provider P99. Ported from payorch's observability-starter."

dependencies {
    // Explicit version, not left to the Boot BOM - see the comment in
    // infra-chaos/build.gradle.kts on why an unversioned `api` dep fails
    // generateMetadataFileForMavenPublication. Matches this module's own
    // Boot version, as starter versions always do.
    api("org.springframework.boot:spring-boot-starter-opentelemetry:4.1.1")

    compileOnly("io.micrometer:micrometer-tracing")
    compileOnly("io.micrometer:micrometer-observation")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.16.0-alpha")

    testImplementation("io.micrometer:micrometer-tracing")
    testImplementation("io.micrometer:micrometer-tracing-bridge-otel")
    testImplementation("io.micrometer:micrometer-observation")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.16.0-alpha")
}

tasks.jar {
    enabled = true
    archiveClassifier = ""
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
}

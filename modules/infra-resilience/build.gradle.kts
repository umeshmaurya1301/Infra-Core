plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "Deadline budget, retry, circuit breaker, bulkhead, rate limiters. Ported from payorch's resilience-starter."

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-web")

    compileOnly("io.micrometer:micrometer-core")
    testImplementation("io.micrometer:micrometer-core")

    api("io.github.resilience4j:resilience4j-circuitbreaker:2.4.0")

    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")

    compileOnly("io.micrometer:context-propagation")
    testImplementation("io.micrometer:context-propagation")
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

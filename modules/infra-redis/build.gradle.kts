plugins {
    // java-library (not plain java) so we can use `api(...)` and expose Jedis
    // types on consumers' compile classpath.
    id("java-library")
    id("maven-publish")
}

description = "Reusable Redis client library — exposes Jedis for consumer modules"

dependencies {
    // ── Jedis ─────────────────────────────────────────────────────────────
    // Exposed as `api` so any project depending on infra-redis can directly
    // `import redis.clients.jedis.*` without redeclaring Jedis itself.
    // Version verified against Maven Central search API (latest stable as of pin).
    api("redis.clients:jedis:${property("jedisVersion")}")

    // Optional connection pooling helpers (Jedis already bundles commons-pool2,
    // but pinning the version explicitly avoids surprises across upgrades).
    implementation("org.apache.commons:commons-pool2")

    // ── Spring Boot (compile-only so non-Spring consumers can use this too) ──
    compileOnly("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // ── Lombok ────────────────────────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.assertj:assertj-core")
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

            pom {
                name.set(project.name)
                description.set(project.description ?: "Reusable Redis client library module")
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

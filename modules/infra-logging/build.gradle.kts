plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "Structured JSON logging + PII masking, ported from payorch's logging-starter."

dependencies {
    // `api`, not `implementation`: consumers write code against Jackson and the
    // encoder's StructuredArguments, so these are part of our public surface.
    api("net.logstash.logback:logstash-logback-encoder:9.0")
    // jackson-databind and logback-classic are already declared in the root
    // subprojects block (implementation), which is sufficient to compile and run.

    // Logback -> OpenTelemetry bridge, so log lines reach a collector attached
    // to the trace that produced them. `api` because logback-payorch.xml names
    // this appender class directly.
    api("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.16.0-alpha")
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
                description.set(project.description ?: "Infrastructure library module")
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

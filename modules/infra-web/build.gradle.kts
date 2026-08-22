plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "Shared HTTP concerns: RFC-7807 error model and the correlation-ID filter. Ported from payorch's web-starter."

dependencies {
    api(project(":infra-logging"))

    compileOnly("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
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

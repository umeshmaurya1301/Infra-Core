plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "Idempotency keys, request fingerprinting, in-flight markers, response replay. Ported from payorch's idempotency-starter."

dependencies {
    compileOnly("org.slf4j:slf4j-api")
    testImplementation("org.slf4j:slf4j-api")
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

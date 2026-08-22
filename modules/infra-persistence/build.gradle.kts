plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "UUIDv7 identifiers and their BINARY(16) representation. Ported from payorch's persistence-starter."

dependencies {
    api("com.github.f4b6a3:uuid-creator:6.1.1")
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

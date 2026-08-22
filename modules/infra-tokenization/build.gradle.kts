plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "The token vault: PAN tokenization at the edge and the single audited detokenization path. Ported from payorch's tokenization-starter."

dependencies {
    api(project(":infra-logging"))

    compileOnly("org.springframework:spring-jdbc")
    compileOnly("com.zaxxer:HikariCP")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("com.zaxxer:HikariCP")
    testImplementation("com.h2database:h2")
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

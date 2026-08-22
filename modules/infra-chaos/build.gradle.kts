plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

description = "The chaos layer that lives inside the JVM: bean-level assaults, plus bespoke seams. Ported from payorch's chaos-core."

dependencies {
    // Explicit versions here (not left to the Boot BOM), because
    // io.spring.dependency-management resolves versions for compilation but
    // does not record them in published Gradle Module Metadata - an unversioned
    // `api` dependency fails generateMetadataFileForMavenPublication with
    // "dependencies without versions". Pinned to what spring-boot-dependencies
    // 4.1.1 actually resolves; bump alongside springBootVersion in the root build.
    api("org.springframework:spring-aop:7.0.9")
    api("org.aspectj:aspectjweaver:1.9.25.1")

    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
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

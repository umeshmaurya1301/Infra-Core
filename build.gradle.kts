plugins {
    id("java")
    id("org.springframework.boot") version "3.4.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

// ===========================================
// CENTRALIZED VERSION MANAGEMENT
// ===========================================
extra["springBootVersion"] = "3.4.2"
extra["springCloudVersion"] = "2024.0.0"
extra["springSecurityVersion"] = "6.4.1"
extra["junitVersion"] = "5.11.3"
extra["mockitoVersion"] = "5.14.2"
extra["assertjVersion"] = "3.26.3"
extra["jacksonVersion"] = "2.18.2"
extra["lombokVersion"] = "1.18.36"
extra["slf4jVersion"] = "2.0.16"
extra["logbackVersion"] = "1.5.12"
extra["postgresqlVersion"] = "42.7.4"
extra["h2Version"] = "2.3.232"
extra["jjwtVersion"] = "0.12.6"
extra["bouncyCastleVersion"] = "1.79"
extra["commonsCodecVersion"] = "1.17.1"
extra["commonsLang3Version"] = "3.17.0"
extra["commonsCollections4Version"] = "4.4"
extra["commonsIoVersion"] = "2.18.0"
extra["guavaVersion"] = "33.3.1-jre"
extra["mapstructVersion"] = "1.6.3"

allprojects {
    group = "org.infra"
    version = "1.0.0"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {
    apply(plugin = "java")

    // Apply Spring Boot plugin only to certain modules
    if (project.name !in listOf("infra-commons", "infra-core-module", "infra-cryptography")) {
        apply(plugin = "org.springframework.boot")
    }
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        withJavadocJar()
        withSourcesJar()
    }

    // Use stable API to avoid @Incubating warning
    val compileOnlyConfig = configurations.getByName("compileOnly")
    val annotationProcessorConfig = configurations.getByName("annotationProcessor")
    compileOnlyConfig.extendsFrom(annotationProcessorConfig)

    dependencies {
        // ======================
        // Implementation
        // ======================
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.springframework.boot:spring-boot-starter-validation")

        implementation("org.slf4j:slf4j-api:${property("slf4jVersion")}")
        implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
        implementation("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")

        // ======================
        // Compile Only / Annotation Processing
        // ======================
        compileOnly("org.projectlombok:lombok:${property("lombokVersion")}")
        annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")

        // ======================
        // Testing
        // ======================
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        testImplementation("org.mockito:mockito-core:${property("mockitoVersion")}")
        testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
}

// Root project should not produce a JAR
tasks.jar {
    enabled = false
}

// ======================
// Publish all modules to Maven local
// ======================
tasks.register("publishAllToMavenLocal") {
    group = "Publishing"
    description = "Publishes all modules to Maven local repository"

    subprojects.forEach { sub ->
        dependsOn("${sub.path}:publishToMavenLocal")
    }
}

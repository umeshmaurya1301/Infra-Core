plugins {
    id("java")
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// ===========================================
// CENTRALIZED VERSION MANAGEMENT
// ===========================================
extra["springBootVersion"] = "4.1.1"
extra["springSecurityVersion"] = "6.4.1"
extra["slf4jVersion"] = "2.0.16"
extra["logbackVersion"] = "1.5.14"
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
extra["jedisVersion"] = "6.0.0"

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

    // Apply Spring Boot plugin only to modules that need it (applications, not libraries)
    if (project.name in listOf("infra-core")) {
        apply(plugin = "org.springframework.boot")
    }
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
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
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")


        implementation("org.slf4j:slf4j-api")
        implementation("ch.qos.logback:logback-classic")
        implementation("tools.jackson.core:jackson-databind")

        // ======================
        // Compile Only / Annotation Processing
        // ======================
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")

        // ======================
        // Testing
        // ======================
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito:mockito-core")
        testImplementation("org.assertj:assertj-core")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
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

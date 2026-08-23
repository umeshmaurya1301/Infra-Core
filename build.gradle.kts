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
extra["logbackVersion"] = "1.5.38" // matches what spring-boot-starter-logging:4.1.1 actually requires
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
        //
        // Deliberately NO blanket spring-boot-starter/-web/-actuator/-validation/
        // -data-jpa here. Every module below scopes its own Boot-starter needs as
        // `compileOnly` (matching the payorch originals this was ported from) so
        // a consumer only gets what it actually asked for. A forced `implementation`
        // here previously leaked spring-boot-starter-data-jpa onto the runtime
        // classpath of every consumer of every starter - including services with
        // no JPA at all - and Spring Boot tried to auto-configure a DataSource for
        // them at startup and failed. See infra-web/infra-resilience/infra-chaos/
        // infra-observability's own build.gradle.kts for the real per-module list.
        //
        // spring-boot-autoconfigure IS kept, universally - every starter here is
        // an autoconfiguration provider (@AutoConfiguration, @Bean, @Value,
        // @ConditionalOnProperty classes), and this is the one artifact that
        // supplies those types at compile time without pulling in anything that
        // actually activates at runtime (no @ConditionalOnClass here matches
        // unless the consumer's own classpath already has the real starter).
        implementation("org.springframework.boot:spring-boot-autoconfigure")
        annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

        // Explicit versions here (using the extras already declared above),
        // unlike the Boot starters: a module whose ENTIRE published dependency
        // set is unversioned fails generateMetadataFileForMavenPublication with
        // "Publication only contains dependencies ... without a version" - hit
        // by infra-idempotency, which has no module-specific published deps of
        // its own beyond what is declared here.
        implementation("org.slf4j:slf4j-api:${property("slf4jVersion")}")
        implementation("ch.qos.logback:logback-classic:${property("logbackVersion")}")
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

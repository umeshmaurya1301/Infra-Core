# Infra Core

A modular infrastructure library project built with Spring Boot and Gradle.

## Project Structure

The project consists of several modules:

- **infra-commons**: Shared utilities and common components
- **infra-core-module**: Core business logic and services
- **infra-security**: Security configuration and authentication services
- **infra-cryptography**: Cryptography utilities and encryption services

## Building and Publishing

### Build the Project

```bash
./gradlew clean build
```

### Publish to Maven Local

To publish all modules to your local Maven repository (`~/.m2`):

```bash
./gradlew clean build publishToMavenLocal
```

Or use the convenience task:

```bash
./gradlew publishAllToMavenLocal
```

### Individual Module Publishing

You can also publish individual modules:

```bash
./gradlew :infra-commons:publishToMavenLocal
./gradlew :infra-core-module:publishToMavenLocal
./gradlew :infra-security:publishToMavenLocal
./gradlew :infra-cryptography:publishToMavenLocal
```

## Using as a Library

After publishing to Maven local, you can use these libraries in other projects by adding them as dependencies:

### Maven

```xml
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-commons</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-core-module</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-security</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-cryptography</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'org.infra:infra-commons:1.0.0'
    implementation 'org.infra:infra-core-module:1.0.0'
    implementation 'org.infra:infra-security:1.0.0'
    implementation 'org.infra:infra-cryptography:1.0.0'
}
```

## Published Artifacts

Each module publishes the following artifacts to Maven local:

- **Main JAR**: The compiled classes and resources
- **Sources JAR**: Source code files
- **Javadoc JAR**: API documentation
- **POM**: Project metadata and dependencies

## Configuration

The project uses centralized version management in `build.gradle.kts` and applies Spring Boot only to modules that need it. Pure library modules (infra-commons, infra-core-module, infra-cryptography) are configured without Spring Boot to avoid conflicts.

## Requirements

- Java 21
- Gradle 8.x+
- Spring Boot 3.4.2

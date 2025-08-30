# Dependency Management Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [File Structure](#file-structure)
4. [Version Management Strategy](#version-management-strategy)
5. [Spring Boot BOM Integration](#spring-boot-bom-integration)
6. [Module Dependencies](#module-dependencies)
7. [Library Publishing Configuration](#library-publishing-configuration)
8. [Configuration Details](#configuration-details)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Maintenance Guide](#maintenance-guide)

## Overview

This document describes the dependency management system for the `infra-core` project. The system follows a **centralized approach** where all dependency versions are managed in the root project, while leveraging Spring Boot's BOM (Bill of Materials) for automatic version management of Spring-related dependencies. The project is now configured as a **library project** that can be published to Maven repositories.

### Key Principles
- **Single Source of Truth**: All versions defined in one place
- **Spring Boot BOM First**: Let Spring Boot manage compatible versions
- **Explicit Control**: Only manage versions for non-Spring dependencies
- **Module Isolation**: Clear dependency boundaries between modules
- **Library Publishing**: All modules can be published as Maven libraries
- **Conditional Spring Boot**: Spring Boot applied only where needed

## Architecture

### Dependency Management Flow
```
Root Project (build.gradle.kts)
├── Version Definitions (20+ centralized versions)
├── Spring Boot BOM Imports
├── Common Dependencies
├── Common Configurations
└── Conditional Spring Boot Application
    ↓
Subprojects (modules/*/build.gradle.kts)
├── Inherit Common Configurations
├── Reference Centralized Versions
├── Declare Module Dependencies
├── Add Module-Specific Dependencies
├── Maven Publishing Configuration
└── Library-Specific Settings
```

### **New Modular Architecture (2024)**
```
infra-core (Application Entry Point)
├── infra-commons (Foundation Layer)
│   ├── BaseEntity, BaseException, DTOs
│   ├── Utility classes, Constants
│   └── No dependencies (foundation)
├── infra-security (Security Framework)
│   ├── Spring Security, JWT
│   └── Depends on: infra-commons
├── infra-cryptography (Encryption)
│   ├── Crypto utilities, Key management
│   └── Depends on: infra-commons
├── infra-audit (Logging & Audit)
│   ├── API logging, Correlation IDs
│   └── Depends on: infra-commons
└── infra-validation (Validation)
    ├── Custom validators, Annotations
    └── Depends on: infra-commons
```

### Benefits of This Architecture
1. **Reduced Maintenance**: Update versions in one place
2. **Version Consistency**: All modules use same versions
3. **Automatic Compatibility**: Spring Boot ensures tested combinations
4. **Clear Dependencies**: Explicit module relationships
5. **Security Updates**: Automatic patches through Spring Boot updates
6. **Library Reusability**: Modules can be used as dependencies in other projects
7. **Flexible Deployment**: Choose between library and application modes
8. **Single Responsibility**: Each module has one clear purpose
9. **Independent Development**: Teams can work on different modules
10. **Selective Dependencies**: Only include what you need
11. **Better Testing**: Test each concern in isolation
12. **Easier Maintenance**: Changes isolated to specific modules

## File Structure

### Core Files
```
infra-core/
├── build.gradle.kts                    # Root dependency management + publishing config
├── settings.gradle.kts                 # Module discovery and structure
├── gradle/libs.versions.toml          # Centralized version catalog
└── modules/
    ├── infra-commons/
    │   └── build.gradle.kts           # Foundation layer + maven-publish
    ├── infra-core/
    │   └── build.gradle.kts           # Application entry point + maven-publish
    ├── infra-security/
    │   └── build.gradle.kts           # Security framework + maven-publish
    ├── infra-cryptography/
    │   └── build.gradle.kts           # Encryption services + maven-publish
    ├── infra-audit/
    │   └── build.gradle.kts           # Logging & audit + maven-publish
    └── infra-validation/
        └── build.gradle.kts           # Validation framework + maven-publish
```

### File Responsibilities

| File | Purpose | Key Responsibilities |
|------|---------|---------------------|
| `build.gradle.kts` | **Root Configuration** | Version definitions, BOM imports, common configs, conditional Spring Boot |
| `settings.gradle.kts` | **Project Structure** | Module inclusion, project path mapping |
| `modules/*/build.gradle.kts` | **Module Configuration** | Module-specific deps, inter-module refs, maven-publish config |

## Version Management Strategy

### Version Categories

#### 1. **Spring Boot Managed (Auto)**
```kotlin
// These versions are automatically managed by Spring Boot BOM
// NO manual version needed
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.projectlombok:lombok")
implementation("com.fasterxml.jackson.core:jackson-databind")
implementation("org.slf4j:slf4j-api")
```

#### 2. **Centralized Manual Management**
```kotlin
// These versions are manually managed in root build.gradle.kts
extra["springBootVersion"] = "3.4.2"
extra["springCloudVersion"] = "2024.0.0"
extra["postgresqlVersion"] = "42.7.4"
extra["jjwtVersion"] = "0.12.6"
extra["bouncyCastleVersion"] = "1.79"
extra["commonsLang3Version"] = "3.17.0"
extra["guavaVersion"] = "33.3.1-jre"
```

### Version Access Pattern
```kotlin
// In module build files, use property() function
implementation("org.postgresql:postgresql:${property("postgresqlVersion")}")
implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
```

## Spring Boot BOM Integration

### BOM Configuration
```kotlin
the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}
```

### Conditional Spring Boot Application
```kotlin
subprojects {
    apply(plugin = "java")
    
    // Only apply Spring Boot to modules that need it
    if (project.name in listOf("infra-commons", "infra-cryptography")) {
        // Pure library modules - no Spring Boot
    } else {
        apply(plugin = "org.springframework.boot")
    }
    apply(plugin = "io.spring.dependency-management")
    
    // ... rest of configuration
}
```

### What Spring Boot BOM Manages
- **Spring Boot Starters**: All starter dependencies
- **Core Libraries**: Lombok, Jackson, SLF4J, Logback
- **Testing Framework**: JUnit, Mockito, AssertJ
- **Transitive Dependencies**: Automatically resolved compatible versions

### Benefits of BOM Approach
1. **Tested Compatibility**: All versions tested together
2. **Security Updates**: Automatic security patches
3. **Reduced Conflicts**: No version mismatches
4. **Simplified Updates**: Update Spring Boot, get all compatible versions
5. **Library Mode**: Pure library modules don't have Spring Boot overhead

## Module Dependencies

### Dependency Graph
```
infra-commons (Foundation Layer)
    ↑
infra-security, infra-cryptography, infra-audit, infra-validation
    ↑
infra-core (Application Entry Point)
```

### Module Dependencies Detail

#### 1. **infra-commons**
- **Purpose**: Foundation layer with shared utilities and common components
- **Dependencies**: None (foundation layer)
- **Exports**: `BaseEntity`, `BaseException`, `ApiResponse<T>`, `PageResponse<T>`, utility classes
- **Spring Boot**: ❌ Not applied (pure library)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

#### 2. **infra-core**
- **Purpose**: Application entry point and module orchestrator
- **Dependencies**: All other modules (`infra-commons`, `infra-security`, `infra-cryptography`, `infra-audit`, `infra-validation`)
- **Usage**: Main Spring Boot application, configuration aggregation
- **Spring Boot**: ✅ Applied (main application)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

#### 3. **infra-security**
- **Purpose**: Security framework and authentication services
- **Dependencies**: `infra-commons`
- **Usage**: Spring Security configuration, JWT handling, role management
- **Spring Boot**: ✅ Applied (needs Spring Security features)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

#### 4. **infra-cryptography**
- **Purpose**: Encryption and cryptography services
- **Dependencies**: `infra-commons`
- **Usage**: Crypto utilities, key management, hashing
- **Spring Boot**: ❌ Not applied (pure library)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

#### 5. **infra-audit**
- **Purpose**: Centralized logging and audit services
- **Dependencies**: `infra-commons`
- **Usage**: API request/response logging, correlation ID management, performance monitoring
- **Spring Boot**: ✅ Applied (needs Spring Web features)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

#### 6. **infra-validation**
- **Purpose**: Custom validation framework and utilities
- **Dependencies**: `infra-commons`
- **Usage**: Custom validation annotations, validators, validation utilities
- **Spring Boot**: ✅ Applied (needs Spring Validation features)
- **Publishing**: ✅ Maven library with JAR, sources, javadoc

### Dependency Declaration Examples
```kotlin
// Module dependency
implementation(project(":infra-commons"))

// External dependency with centralized version
implementation("org.postgresql:postgresql:${property("postgresqlVersion")}")
```

## Library Publishing Configuration

### Maven Publish Plugin Setup

#### Root Project Configuration
```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "3.4.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    // maven-publish is a core Gradle plugin, no need to declare
}

// Convenience task to publish all modules
tasks.register("publishAllToMavenLocal") {
    dependsOn(":infra-commons:publishToMavenLocal")
    dependsOn(":infra-core-module:publishToMavenLocal")
    dependsOn(":infra-security:publishToMavenLocal")
    dependsOn(":infra-cryptography:publishToMavenLocal")
    
    description = "Publishes all modules to Maven local repository"
}
```

#### Module Publishing Configuration
```kotlin
plugins {
    id("java")
    id("maven-publish")
}

// Configure publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            // Set artifact coordinates
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            // Add POM information
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
```

### Publishing Commands

#### Publish All Modules
```bash
./gradlew clean build publishToMavenLocal
./gradlew publishAllToMavenLocal
```

#### Publish Individual Modules
```bash
./gradlew :infra-commons:publishToMavenLocal
./gradlew :infra-core-module:publishToMavenLocal
./gradlew :infra-security:publishToMavenLocal
./gradlew :infra-cryptography:publishToMavenLocal
./gradlew :infra-audit:publishToMavenLocal
./gradlew :infra-validation:publishToMavenLocal
```

### Published Artifacts

Each module publishes the following artifacts to Maven local (`~/.m2/repository/org/infra/`):

- **Main JAR**: `{module}-{version}.jar` - Compiled classes and resources
- **Sources JAR**: `{module}-{version}-sources.jar` - Source code files
- **Javadoc JAR**: `{module}-{version}-javadoc.jar` - API documentation
- **POM**: `{module}-{version}.pom` - Project metadata and dependencies
- **Module Metadata**: `{module}-{version}.module` - Gradle module metadata

### Using Published Libraries

After publishing to Maven local, these libraries can be used in other projects:

#### Maven
```xml
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-commons</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Gradle
```gradle
dependencies {
    // Foundation layer - always needed
    implementation 'org.infra:infra-commons:1.0.0'
    
    // Application entry point
    implementation 'org.infra:infra-core-module:1.0.0'
    
    // Security framework
    implementation 'org.infra:infra-security:1.0.0'
    
    // Encryption services
    implementation 'org.infra:infra-cryptography:1.0.0'
    
    // Centralized logging
    implementation 'org.infra:infra-audit:1.0.0'
    
    // Validation framework
    implementation 'org.infra:infra-validation:1.0.0'
}
```

## Configuration Details

### Root Project Configuration
```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "3.4.2" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

// Centralized versions
extra["springBootVersion"] = "3.4.2"
extra["springCloudVersion"] = "2024.0.0"
// ... more versions

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
    
    // Only apply Spring Boot to modules that need it
    if (project.name in listOf("infra-commons", "infra-cryptography")) {
        // Pure library modules - no Spring Boot
    } else {
        apply(plugin = "org.springframework.boot")
    }
    apply(plugin = "io.spring.dependency-management")
    
    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        
        // Enable Javadoc and sources JARs
        withJavadocJar()
        withSourcesJar()
    }
    
    // Common dependencies for all modules
    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-web")
        // ... more common deps
    }
}
```

### Module Configuration Pattern
```kotlin
plugins {
    id("java")
    id("maven-publish")
}

description = "Module description"

dependencies {
    // Inter-module dependencies
    implementation(project(":infra-commons"))
    
    // External dependencies with centralized versions
    implementation("external:library:${property("versionProperty")}")
    
    // Module-specific dependencies
    implementation("org.springframework.boot:spring-boot-starter-security")
}

// Library-specific configurations
tasks.jar {
    enabled = true
    archiveClassifier = ""
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // ... POM configuration
        }
    }
}
```

## Best Practices

### 1. **Version Management**
- ✅ Use Spring Boot BOM for Spring-related dependencies
- ✅ Only manage versions for non-Spring dependencies
- ✅ Keep all versions in root `build.gradle.kts`
- ❌ Don't duplicate version definitions in modules

### 2. **Module Dependencies**
- ✅ Declare explicit module dependencies
- ✅ Use project names from `settings.gradle.kts`
- ✅ Keep modules focused on their responsibilities
- ❌ Don't create circular dependencies

### 3. **Configuration Inheritance**
- ✅ Inherit common configs from root project
- ✅ Override only when necessary
- ✅ Use consistent patterns across modules
- ❌ Don't duplicate common configurations

### 4. **Dependency Declaration**
- ✅ Use `property()` function for centralized versions
- ✅ Group dependencies by type (Spring, external, module)
- ✅ Add clear comments for dependency purposes
- ❌ Don't hardcode versions in module files

### 5. **Library Publishing**
- ✅ Apply maven-publish plugin to all modules
- ✅ Configure proper POM metadata
- ✅ Use consistent artifact naming
- ✅ Include license and developer information
- ❌ Don't publish unnecessary artifacts

### 6. **Spring Boot Usage**
- ✅ Apply Spring Boot only to modules that need it
- ✅ Use pure library configuration for utility modules
- ✅ Leverage Spring Boot BOM for dependency management
- ❌ Don't force Spring Boot on pure library modules

## Troubleshooting

### Common Issues and Solutions

#### 1. **"Cannot get non-null extra property" Error**
```bash
Cannot get non-null extra property 'versionProperty' as it does not exist
```
**Solution**: Use `property("versionProperty")` instead of `extra["versionProperty"]`

#### 2. **"Project with path could not be found" Error**
```bash
Project with path ':modules:infra-commons' could not be found
```
**Solution**: Use project names from `settings.gradle.kts` (e.g., `:infra-commons`)

#### 3. **Version Conflicts**
```bash
Multiple versions of the same dependency found
```
**Solution**: Let Spring Boot BOM manage versions, only override when necessary

#### 4. **Build Order Issues**
```bash
Module dependency not found during compilation
```
**Solution**: Ensure proper dependency declarations in `settings.gradle.kts`

#### 5. **Publishing Conflicts**
```bash
Invalid publication 'maven': multiple artifacts with the identical extension and classifier
```
**Solution**: Remove duplicate artifact declarations (Javadoc/sources JARs are created automatically by Spring Boot dependency management)

#### 6. **Spring Boot Plugin Issues**
```bash
Unresolved reference: bootJar
```
**Solution**: Only reference Spring Boot tasks in modules where the plugin is applied

### Debug Commands
```bash
# Show dependency tree
./gradlew dependencies

# Show project structure
./gradlew projects

# Build with debug info
./gradlew build --debug

# Clean and rebuild
./gradlew clean build

# Test publishing
./gradlew publishToMavenLocal --dry-run

# Check published artifacts
ls -la ~/.m2/repository/org/infra/
```

## Maintenance Guide

### Regular Maintenance Tasks

#### 1. **Spring Boot Updates**
```kotlin
// Update in root build.gradle.kts
extra["springBootVersion"] = "3.4.3" // New version
extra["springCloudVersion"] = "2024.0.1" // Compatible version
```

#### 2. **External Dependency Updates**
```kotlin
// Update specific versions as needed
extra["postgresqlVersion"] = "42.7.5"
extra["jjwtVersion"] = "0.12.7"
```

#### 3. **Version Compatibility Checks**
- Verify Spring Boot compatibility matrix
- Test with new versions before updating
- Check for breaking changes in major updates

#### 4. **Publishing Verification**
- Test publishing to Maven local after updates
- Verify POM metadata is correct
- Check that all artifacts are generated properly

### Update Process
1. **Update Spring Boot version** in root `build.gradle.kts`
2. **Update compatible Spring Cloud version**
3. **Test build** with `./gradlew clean build`
4. **Test publishing** with `./gradlew publishToMavenLocal`
5. **Update external dependencies** if needed
6. **Verify all tests pass**
7. **Commit changes** with clear version update message

### Monitoring and Alerts
- **Security Updates**: Monitor Spring Boot security advisories
- **Version Compatibility**: Check Spring Boot compatibility matrix
- **Dependency Vulnerabilities**: Use tools like OWASP Dependency Check
- **Build Health**: Monitor CI/CD pipeline for dependency issues
- **Publishing Health**: Verify Maven local artifacts are generated correctly

## **New Modules (2024 Update)**

### **infra-audit** - Centralized Logging & Audit
- **Purpose**: API request/response logging, audit trails, performance monitoring
- **Key Features**: 
  - `ApiLoggingInterceptor` for automatic request/response logging
  - Correlation ID management for request tracking
  - Performance monitoring and metrics
  - Sensitive data sanitization
- **Dependencies**: `infra-commons`
- **Spring Boot**: ✅ Applied (needs Spring Web features)

### **infra-validation** - Custom Validation Framework
- **Purpose**: Extensible validation system with custom annotations and validators
- **Key Features**:
  - `@ValidEmail` annotation and validator
  - Extensible validation infrastructure
  - Integration with Jakarta Validation
- **Dependencies**: `infra-commons`
- **Spring Boot**: ✅ Applied (needs Spring Validation features)

### **Enhanced infra-commons** - Foundation Layer
- **New Components**:
  - `BaseEntity` with automatic audit fields
  - Exception hierarchy (`BaseException`, `BusinessException`, `TechnicalException`)
  - Standard DTOs (`ApiResponse<T>`, `PageResponse<T>`)
  - Utility classes (`StringUtils`, `DateUtils`)
- **Dependencies**: None (pure foundation)

## Conclusion

This dependency management system provides a robust, maintainable approach to managing dependencies in a multi-module Spring Boot project that can also function as a library project. By leveraging Spring Boot's BOM, centralizing version management, and providing flexible Spring Boot application, it reduces maintenance overhead while ensuring version compatibility, security, and reusability.

The **2024 modular architecture update** introduces:
- **Clean separation of concerns** with dedicated modules for each major functionality
- **Single responsibility principle** - each module has one clear purpose
- **Independent development** - teams can work on different modules
- **Selective dependencies** - only include what you need
- **Better testing** - test each concern in isolation
- **Easier maintenance** - changes isolated to specific modules

The key to success is following the established patterns and maintaining consistency across all modules. The addition of Maven publishing capabilities makes the project more valuable as it can be used as a dependency in other projects. Regular updates and monitoring ensure the system remains secure, up-to-date, and publishable.

The system now supports both application and library modes, making it versatile for different use cases while maintaining the benefits of centralized dependency management. The new modular architecture provides a solid foundation for scalable, maintainable infrastructure code while keeping concerns properly separated and dependencies clean.

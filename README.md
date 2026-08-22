# Infrastructure Core

A modern, modular infrastructure framework built with Spring Boot and Gradle, designed for enterprise applications with clean separation of concerns.

## 🏗️ **Architecture Overview**

The project follows a clean, modular architecture where each module has a single responsibility:

```
infra-core (Application Entry Point)
├── infra-commons (Foundation Layer)
├── infra-security (Security & Authentication)
├── infra-cryptography (Encryption Services)
├── infra-audit (Centralized Logging & Audit)
└── infra-validation (Validation Framework)
```

## 📦 **Module Structure**

### **🏗️ infra-core** - Application Entry Point
- **Purpose**: Main application launcher and module orchestrator
- **Dependencies**: All other modules
- **Contents**: Spring Boot main class, configuration aggregation

### **🔧 infra-commons** - Foundation Layer
- **Purpose**: Shared utilities and common components
- **Dependencies**: None (foundation layer)
- **Contents**: 
  - `BaseEntity` with audit fields (created_date, updated_date, created_by, updated_by)
  - Exception hierarchy (`BaseException`, `BusinessException`, `TechnicalException`)
  - Common DTOs (`ApiResponse<T>`, `PageResponse<T>`)
  - Utility classes (`StringUtils`, `DateUtils`)
  - Constants and configuration

### **🔐 infra-security** - Security Framework
- **Purpose**: Authentication, authorization, and security utilities
- **Dependencies**: infra-commons
- **Contents**: Spring Security configuration, JWT handling, role management

### **🔒 infra-cryptography** - Encryption Services
- **Purpose**: Encryption, decryption, and cryptographic utilities
- **Dependencies**: infra-commons
- **Contents**: Crypto utilities, key management, hashing

### **📊 infra-audit** - Centralized Logging
- **Purpose**: API logging, audit trails, and monitoring
- **Dependencies**: infra-commons
- **Contents**: Request/response logging, correlation ID management, performance monitoring

### **✅ infra-validation** - Validation Framework
- **Purpose**: Custom validators and validation utilities
- **Dependencies**: infra-commons
- **Contents**: Custom validation annotations, validators, validation utilities

## 🚀 **Building and Publishing**

### **Build the Project**

```bash
./gradlew clean build
```

### **Publish to Maven Local**

To publish all modules to your local Maven repository (`~/.m2`):

```bash
./gradlew clean build publishToMavenLocal
```

### **Individual Module Publishing**

You can also publish individual modules:

```bash
./gradlew :infra-commons:publishToMavenLocal
./gradlew :infra-core-module:publishToMavenLocal
./gradlew :infra-security:publishToMavenLocal
./gradlew :infra-cryptography:publishToMavenLocal
./gradlew :infra-audit:publishToMavenLocal
./gradlew :infra-validation:publishToMavenLocal
```

## 📚 **Using as a Library**

After publishing to Maven local, you can use these libraries in other projects by adding them as dependencies:

### **Maven**

```xml
<!-- Foundation layer - always needed -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-commons</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Application entry point -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-core-module</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Security framework -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-security</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Encryption services -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-cryptography</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Centralized logging -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-audit</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Validation framework -->
<dependency>
    <groupId>org.infra</groupId>
    <artifactId>infra-validation</artifactId>
    <version>1.0.0</version>
</dependency>
```

### **Gradle**

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

## 📦 **Published Artifacts**

Each module publishes the following artifacts to Maven local:

- **Main JAR**: The compiled classes and resources
- **Sources JAR**: Source code files
- **Javadoc JAR**: API documentation
- **POM**: Project metadata and dependencies

## ⚙️ **Configuration**

The project uses centralized version management in `gradle/libs.versions.toml` and applies Spring Boot only to modules that need it. Pure library modules are configured without Spring Boot to avoid conflicts.

## 🔧 **Requirements**

- Java 25+
- Gradle 9.x+
- Spring Boot 4.1.1+

## 🎯 **Key Features**

### **✅ Audit & Logging**
- Automatic audit fields in entities
- Centralized API request/response logging
- Correlation ID tracking
- Performance monitoring

### **✅ Exception Handling**
- Hierarchical exception framework
- Business vs. technical exception separation
- Error code management

### **✅ Validation Framework**
- Custom validation annotations
- Extensible validator system
- Built-in email validation

### **✅ Utility Classes**
- String manipulation and validation
- Date/time utilities
- Common helper functions

### **✅ Security & Cryptography**
- Spring Security integration
- JWT support
- Encryption/decryption services

## 🚀 **Getting Started**

1. **Clone the repository**
2. **Build the project**: `./gradlew clean build`
3. **Include needed modules** in your project dependencies
4. **Extend BaseEntity** for audit fields
5. **Use ApiResponse** for consistent API responses
6. **Implement custom validators** as needed

## 📖 **Documentation**

For detailed architecture information, see [ARCHITECTURE.md](./ARCHITECTURE.md)

## 🤝 **Contributing**

This project follows a modular architecture. When adding new functionality:
1. Identify the appropriate module for your feature
2. Follow the existing patterns and dependencies
3. Ensure proper separation of concerns
4. Add comprehensive tests

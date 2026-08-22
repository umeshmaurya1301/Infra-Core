plugins {
    id("java")
    id("maven-publish")
}

description = "Shared utilities and common components"

dependencies {
    // Additional commons-specific dependencies using centralized versions
    implementation("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
    implementation("org.apache.commons:commons-collections4:${property("commonsCollections4Version")}")
    implementation("commons-io:commons-io:${property("commonsIoVersion")}")
    implementation("com.google.guava:guava:${property("guavaVersion")}")
    
    // JPA and Persistence
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("jakarta.validation:jakarta.validation-api")
    
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // JSON processing
    implementation("tools.jackson.core:jackson-databind")
    
    // Configuration properties
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}



tasks.jar {
    enabled = true
    archiveClassifier = ""
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
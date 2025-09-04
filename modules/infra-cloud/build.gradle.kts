plugins {
    id("java")
    id("maven-publish")
}

description = "Cloud services abstraction layer providing common interfaces for AWS, GCP, and Azure"

dependencies {
    // Inter-module dependencies
    implementation(project(":infra-commons"))
    
    // Spring Framework
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-context")
    
    // GCP Cloud Storage SDK
    implementation("com.google.cloud:google-cloud-storage:2.40.1")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
    implementation("commons-io:commons-io:${property("commonsIoVersion")}")
    
    // Logging
    implementation("org.slf4j:slf4j-api")
    
    // Testing using centralized versions
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core:${property("mockitoVersion")}")
}

// This module should not create a fat JAR since it's a library
// Note: bootJar task is not available since Spring Boot plugin is not applied

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

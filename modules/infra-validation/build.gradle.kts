plugins {
    id("java")
    id("maven-publish")
}

description = "Validation framework and utilities"

dependencies {
    // Module dependencies
    implementation(project(":infra-commons"))
    
    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.hibernate.validator:hibernate-validator")
    
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
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
                description.set(project.description ?: "Infrastructure validation module")
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

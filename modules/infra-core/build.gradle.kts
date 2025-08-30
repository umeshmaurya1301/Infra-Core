plugins {
    id("java")
    id("maven-publish")
}

description = "Core business logic and services"

dependencies {
    // Module dependencies - include all other modules
    implementation(project(":infra-commons"))
    implementation(project(":infra-security"))
    implementation(project(":infra-cryptography"))
    implementation(project(":infra-audit"))
    implementation(project(":infra-validation"))
    
    // Spring Boot starter for main application
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
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

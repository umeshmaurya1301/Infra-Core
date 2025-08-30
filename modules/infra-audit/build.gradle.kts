plugins {
    id("java")
    id("maven-publish")
}

description = "Centralized logging and audit infrastructure"

dependencies {
    // Module dependencies
    implementation(project(":infra-commons"))
    
    // Spring Boot Web for interceptors and filters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    
    // Logging
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
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
                description.set(project.description ?: "Infrastructure audit module")
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

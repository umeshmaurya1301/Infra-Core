plugins {
    id("java")
    id("maven-publish")
}

description = "Security configuration and authentication services"

dependencies {
    // Inter-module dependencies
    implementation(project(":infra-commons"))
    
    // Spring Security using centralized versions
    implementation("org.springframework.boot:spring-boot-starter-security")
    // SpringSecurityAuditorAware implements AuditorAware, from spring-data-commons.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    
    // JWT using centralized versions
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")
    
    // Testing using centralized versions
    testImplementation("org.springframework.security:spring-security-test")
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
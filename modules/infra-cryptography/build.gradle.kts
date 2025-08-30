plugins {
    id("java")
    id("maven-publish")
}

description = "Cryptography utilities and encryption services"

dependencies {
    // Inter-module dependencies
    implementation(project(":infra-commons"))
    
    // Cryptography libraries using centralized versions
    implementation("org.bouncycastle:bcprov-jdk18on:${property("bouncyCastleVersion")}")
    implementation("org.bouncycastle:bcpkix-jdk18on:${property("bouncyCastleVersion")}")
    
    // Additional crypto utilities using centralized versions
    implementation("commons-codec:commons-codec:${property("commonsCodecVersion")}")
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
rootProject.name = "infra-core"

// Include all submodules
include(
    "infra-commons",
    "infra-core-module", 
    "infra-security",
    "infra-cryptography"
)

// Set project directories
project(":infra-commons").projectDir = file("modules/infra-commons")
project(":infra-core-module").projectDir = file("modules/infra-core")
project(":infra-security").projectDir = file("modules/infra-security")
project(":infra-cryptography").projectDir = file("modules/infra-cryptography")
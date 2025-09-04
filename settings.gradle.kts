rootProject.name = "infra-core"

// Include all submodules
include(
    "infra-commons",
    "infra-core-module", 
    "infra-security",
    "infra-cryptography",
    "infra-audit",
    "infra-validation",
    "infra-cloud"
)

// Set project directories
project(":infra-commons").projectDir = file("modules/infra-commons")
project(":infra-core-module").projectDir = file("modules/infra-core")
project(":infra-security").projectDir = file("modules/infra-security")
project(":infra-cryptography").projectDir = file("modules/infra-cryptography")
project(":infra-audit").projectDir = file("modules/infra-audit")
project(":infra-validation").projectDir = file("modules/infra-validation")
project(":infra-cloud").projectDir = file("modules/infra-cloud")
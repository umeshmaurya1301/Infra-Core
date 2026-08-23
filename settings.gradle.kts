rootProject.name = "infra-core"

// Include all submodules
include(
    "infra-commons",
    "infra-security",
    "infra-cryptography",
    "infra-audit",
    "infra-validation",
    "infra-kafka",
    "infra-redis",
    "infra-logging",
    "infra-web",
    "infra-persistence",
    "infra-tokenization",
    "infra-chaos",
    "infra-resilience",
    "infra-idempotency",
    "infra-observability"
)

// Set project directories
project(":infra-commons").projectDir = file("modules/infra-commons")
project(":infra-security").projectDir = file("modules/infra-security")
project(":infra-cryptography").projectDir = file("modules/infra-cryptography")
project(":infra-audit").projectDir = file("modules/infra-audit")
project(":infra-validation").projectDir = file("modules/infra-validation")
project(":infra-kafka").projectDir = file("modules/infra-kafka")
project(":infra-redis").projectDir = file("modules/infra-redis")
project(":infra-logging").projectDir = file("modules/infra-logging")
project(":infra-web").projectDir = file("modules/infra-web")
project(":infra-persistence").projectDir = file("modules/infra-persistence")
project(":infra-tokenization").projectDir = file("modules/infra-tokenization")
project(":infra-chaos").projectDir = file("modules/infra-chaos")
project(":infra-resilience").projectDir = file("modules/infra-resilience")
project(":infra-idempotency").projectDir = file("modules/infra-idempotency")
project(":infra-observability").projectDir = file("modules/infra-observability")
rootProject.name = "mini-stripe"

include(
    "services:payment-service",
    "services:wallet-service",
    "services:ledger-service",
    "services:saga-coordinator"
)

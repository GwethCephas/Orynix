package com.cephCoding.orynix.papertrading.domain

// A single reachable value this milestone - every rejection (insufficient buying power, stale
// price, invalid transition, etc.) is a thrown exception with nothing persisted, so there is no
// SUBMITTED/REJECTED row to model. Mirrors CandleTimeframe's "only the value actually used exists"
// precedent rather than speculatively modeling states this engine can never produce.
enum class OrderStatus {
    FILLED,
}

package com.cephCoding.orynix.papertrading.domain

// Disclosed on every valuation, never silently substituted - OK means the price used was within
// the configured freshness window; STALE/UNAVAILABLE mean a position's mark falls back to its
// last known acceptable price (if any), flagged rather than blanked. See CLAUDE.md Section 22.
enum class PriceStatus {
    OK,
    STALE,
    UNAVAILABLE,
}

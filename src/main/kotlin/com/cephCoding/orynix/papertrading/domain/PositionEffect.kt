package com.cephCoding.orynix.papertrading.domain

// Explicit on every order so a SELL is never assumed to open a short or close a long (or vice
// versa for BUY) without the caller stating which they mean - see CLAUDE.md Section 22.
enum class PositionEffect {
    OPEN,
    CLOSE,
}

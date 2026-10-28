package com.cephCoding.orynix.papertrading.dto

import org.springframework.data.domain.Page

// The first pagination wrapper in this codebase - no existing precedent anywhere else in
// Orynix's REST surface to follow (see CLAUDE.md Section 22). A small, explicit DTO rather than
// serializing a raw Spring Data Page directly, which needs extra Jackson configuration to avoid
// deprecation warnings on recent Spring Data versions and would otherwise leak Page's own
// internal shape as an implicit API contract.
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        // org.springframework.data.domain is @NullMarked (JSpecify) - Spring Data 4's Page<T>
        // therefore carries a non-null upper bound Kotlin enforces directly; S must be declared
        // `: Any` explicitly here since an unconstrained Kotlin type parameter defaults to a
        // nullable upper bound (Any?), which would otherwise fail that bound (CLAUDE.md's
        // existing JSpecify note, same root cause, a new call site).
        fun <S : Any, D> from(page: Page<S>, mapper: (S) -> D): PagedResponse<D> = PagedResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}

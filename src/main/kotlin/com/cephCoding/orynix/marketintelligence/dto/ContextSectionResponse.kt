package com.cephCoding.orynix.marketintelligence.dto

import com.cephCoding.orynix.marketintelligence.domain.ContextSection
import java.time.Instant

data class ContextSectionResponse<T>(
    val status: String,
    val data: T?,
    val message: String?,
    val retrievedAt: Instant,
) {
    companion object {
        fun <T, D> from(section: ContextSection<D>, mapData: (D) -> T): ContextSectionResponse<T> =
            ContextSectionResponse(
                status = section.status.name,
                data = section.data?.let(mapData),
                message = section.message,
                retrievedAt = section.retrievedAt,
            )
    }
}

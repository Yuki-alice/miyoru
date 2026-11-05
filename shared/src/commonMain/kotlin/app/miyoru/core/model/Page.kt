package app.miyoru.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Page<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

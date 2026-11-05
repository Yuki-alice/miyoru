package app.miyoru.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class ContentUnit(
    val unitId: String,
    val title: String,
    val index: Long,
)

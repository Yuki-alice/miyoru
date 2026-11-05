package app.miyoru.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaDetail(
    val summary: MediaSummary,
    val sourceName: String,
    val attributes: Map<String, String> = emptyMap(),
)

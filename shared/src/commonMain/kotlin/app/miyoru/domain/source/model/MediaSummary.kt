package app.miyoru.domain.source.model

import app.miyoru.core.model.MediaKey
import kotlinx.serialization.Serializable

@Serializable
data class MediaSummary(
    val key: MediaKey,
    val title: String,
    val coverUrl: String? = null,
    val author: String? = null,
)

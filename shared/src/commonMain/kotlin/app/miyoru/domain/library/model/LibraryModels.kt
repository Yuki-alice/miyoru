package app.miyoru.domain.library.model

import app.miyoru.core.model.MediaKey
import kotlinx.serialization.Serializable

@Serializable
data class Favorite(val key: MediaKey, val createdAt: Long)

@Serializable
data class HistoryEntry(val key: MediaKey, val lastOpenedAt: Long)

@Serializable
data class MediaProgress(
    val key: MediaKey,
    val unitId: String,
    val position: Long,
    val total: Long,
    val updatedAt: Long,
)

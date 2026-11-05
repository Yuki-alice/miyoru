package app.miyoru.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class SourceId(val value: String)

@JvmInline
@Serializable
value class RemoteId(val value: String)

@Serializable
enum class MediaType { VIDEO, NOVEL, COMIC }

@Serializable
data class MediaKey(
    val sourceId: SourceId,
    val mediaType: MediaType,
    val remoteId: RemoteId,
)

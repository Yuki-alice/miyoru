package app.miyoru.domain.source.model

import kotlinx.serialization.Serializable

@Serializable
data class ImageResource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAt: Long? = null,
)

@Serializable
data class SubtitleTrack(
    val url: String,
    val label: String? = null,
    val language: String? = null,
)

@Serializable
data class StreamResource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val qualityLabel: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val expiresAt: Long? = null,
)

@Serializable
sealed interface ResolvedContent {
    @Serializable
    data class Novel(val text: String) : ResolvedContent

    @Serializable
    data class Comic(val pages: List<ImageResource>) : ResolvedContent

    @Serializable
    data class Video(val streams: List<StreamResource>) : ResolvedContent
}

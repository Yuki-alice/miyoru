package app.miyoru.data.source.model

import app.miyoru.core.common.AppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.source.model.ContentUnit
import app.miyoru.domain.source.model.MediaSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 插件 search/detail 返回的原始 JSON 形状，字段缺失/类型错误在此拦截，不进 Domain。 */
@Serializable
data class MediaSummaryDto(
    val id: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val author: String? = null,
)

@Serializable
data class ContentUnitDto(
    val id: String? = null,
    val title: String? = null,
    val index: Long? = null,
)

private val jsonLenient = Json { ignoreUnknownKeys = true; isLenient = true }

fun parseMediaSummaries(raw: String): List<MediaSummaryDto> =
    jsonLenient.decodeFromString(raw)

fun parseContentUnits(raw: String): List<ContentUnitDto> =
    jsonLenient.decodeFromString(raw)

fun MediaSummaryDto.toDomain(sourceId: SourceId, type: MediaType): MediaSummary {
    val remote = id?.takeIf { it.isNotBlank() }
        ?: throw AppError.Parse("summary missing id")
    val name = title?.takeIf { it.isNotBlank() }
        ?: throw AppError.Parse("summary missing title")
    if (cover != null && !(cover.startsWith("http://") || cover.startsWith("https://"))) {
        throw AppError.Parse("summary has non-http cover")
    }
    return MediaSummary(MediaKey(sourceId, type, RemoteId(remote)), name, cover, author)
}

fun ContentUnitDto.toDomain(): ContentUnit {
    val uid = id?.takeIf { it.isNotBlank() }
        ?: throw AppError.Parse("unit missing id")
    return ContentUnit(
        unitId = uid,
        title = title?.takeIf { it.isNotBlank() } ?: uid,
        index = index ?: 0,
    )
}

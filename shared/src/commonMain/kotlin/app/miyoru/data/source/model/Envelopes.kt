package app.miyoru.data.source.model

import kotlinx.serialization.Serializable

/**
 * 插件 search/detail/units/resolve 的 JSON 信封约定（契约测试锁定）。
 * items 元素复用 MediaSummaryDto/ContentUnitDto 的校验路径。
 */
@Serializable
data class SearchEnvelope(
    val items: List<MediaSummaryDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class DetailEnvelope(
    val title: String? = null,
    val cover: String? = null,
    val author: String? = null,
    val description: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)

@Serializable
data class UnitsEnvelope(val units: List<ContentUnitDto> = emptyList())

@Serializable
data class NovelEnvelope(val text: String? = null)

@Serializable
data class PagesEnvelope(val pages: List<String> = emptyList())

@Serializable
data class StreamsEnvelope(val streams: List<String> = emptyList())

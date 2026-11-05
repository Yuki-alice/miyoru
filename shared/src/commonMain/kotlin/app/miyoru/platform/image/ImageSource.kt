package app.miyoru.platform.image

/**
 * 短期资源（漫画页/封面/视频流海报）的可加载描述。
 * 敏感 Header 只经 [ImageRequestFactory] 注入 Coil，不进日志与数据库。
 */
data class ImageSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAt: Long? = null,
)

interface ImageRequestFactory {
    fun networkHeaders(source: ImageSource): coil3.network.NetworkHeaders
}

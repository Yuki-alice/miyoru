package app.miyoru.domain.local.model

import app.miyoru.core.model.MediaKey
import kotlinx.serialization.Serializable

enum class LocalFormat { TXT, EPUB, CBZ }

@Serializable
data class LocalImportDescriptor(
    val fileName: String,
    val format: LocalFormat,
    val sizeBytes: Long,
    /** 相对应用数据根目录的路径，不存绝对路径。 */
    val relativePath: String,
)

@Serializable
data class LocalMedia(
    val key: MediaKey,
    val title: String,
    val format: LocalFormat,
    val importedAt: Long,
)

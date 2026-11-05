package app.miyoru.domain.local.usecase

import app.miyoru.core.common.AppError
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import app.miyoru.domain.local.repository.LocalImportRepository

/**
 * 本地文件导入校验（P0：TXT/EPUB/CBZ）。解析在 Data 层做，这里只做形状校验。
 */
class ImportLocalFile(
    private val repository: LocalImportRepository,
    private val maxBytes: Long = 200L * 1024 * 1024,
) {
    suspend operator fun invoke(descriptor: LocalImportDescriptor): LocalMedia {
        val ext = descriptor.fileName.substringAfterLast('.', "").lowercase()
        val expected = when (descriptor.format) {
            LocalFormat.TXT -> "txt"
            LocalFormat.EPUB -> "epub"
            LocalFormat.CBZ -> "cbz"
        }
        if (ext != expected) {
            throw AppError.InvalidInput("extension .$ext does not match format ${descriptor.format}")
        }
        if (descriptor.sizeBytes <= 0) throw AppError.InvalidInput("empty file: ${descriptor.fileName}")
        if (descriptor.sizeBytes > maxBytes) {
            throw AppError.InvalidInput("file too large: ${descriptor.sizeBytes} > $maxBytes")
        }
        if (descriptor.relativePath.isBlank()) throw AppError.InvalidInput("empty relative path")
        return repository.import(descriptor)
    }
}

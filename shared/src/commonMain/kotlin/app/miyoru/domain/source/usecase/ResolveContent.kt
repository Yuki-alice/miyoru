package app.miyoru.domain.source.usecase

import app.miyoru.core.common.toAppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.RemoteId
import app.miyoru.domain.source.model.ResolvedContent
import app.miyoru.domain.source.repository.SourceRepository

/** 章节/剧集资源解析：异常统一映射为 AppError，取消直接传播。 */
class ResolveContent(private val repository: SourceRepository) {
    suspend operator fun invoke(unitKey: MediaKey, unitId: RemoteId): ResolvedContent {
        try {
            return repository.resolve(unitKey, unitId)
        } catch (e: Exception) {
            throw e.toAppError("resolve")
        }
    }
}

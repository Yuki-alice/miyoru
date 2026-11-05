package app.miyoru.domain.library.usecase

import app.miyoru.domain.library.model.MediaProgress
import app.miyoru.domain.library.repository.LibraryRepository

/**
 * 进度保存：位置钳制到 [0, total]，调用方负责节流（播放/阅读中最多 5s 一次，离开页面立即写）。
 */
class UpdateProgress(private val repository: LibraryRepository) {
    suspend operator fun invoke(progress: MediaProgress): MediaProgress {
        val total = progress.total.coerceAtLeast(0)
        val clamped = progress.copy(
            total = total,
            position = progress.position.coerceIn(0, total),
        )
        repository.saveProgress(clamped)
        return clamped
    }
}

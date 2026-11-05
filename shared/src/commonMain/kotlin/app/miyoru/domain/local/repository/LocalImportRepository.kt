package app.miyoru.domain.local.repository

import app.miyoru.core.model.MediaKey
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import kotlinx.coroutines.flow.Flow

interface LocalImportRepository {
    fun observeLocalMedia(): Flow<List<LocalMedia>>
    suspend fun import(descriptor: LocalImportDescriptor): LocalMedia
    suspend fun remove(key: MediaKey)
}

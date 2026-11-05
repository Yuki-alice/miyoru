package app.miyoru.domain.cache.repository

import kotlinx.coroutines.flow.Flow

interface CacheRepository {
    fun observeSizeBytes(): Flow<Long>
    suspend fun clear()
}

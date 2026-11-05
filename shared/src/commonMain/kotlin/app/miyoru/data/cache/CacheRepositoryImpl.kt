package app.miyoru.data.cache

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.miyoru.db.MiyoruDatabase
import app.miyoru.domain.cache.repository.CacheRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CacheRepositoryImpl(
    private val db: MiyoruDatabase,
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : CacheRepository {
    override fun observeSizeBytes(): Flow<Long> =
        db.cacheQueries.totalSize().asFlow().mapToList(io).map { it.firstOrNull() ?: 0L }

    override suspend fun clear(): Unit = withContext(io) {
        db.cacheQueries.clearAll()
    }
}

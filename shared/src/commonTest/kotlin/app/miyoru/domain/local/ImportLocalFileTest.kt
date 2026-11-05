package app.miyoru.domain.local

import app.miyoru.core.common.AppError
import app.miyoru.core.model.MediaKey
import app.miyoru.core.model.MediaType
import app.miyoru.core.model.RemoteId
import app.miyoru.core.model.SourceId
import app.miyoru.domain.local.model.LocalFormat
import app.miyoru.domain.local.model.LocalImportDescriptor
import app.miyoru.domain.local.model.LocalMedia
import app.miyoru.domain.local.repository.LocalImportRepository
import app.miyoru.domain.local.usecase.ImportLocalFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeLocal : LocalImportRepository {
    var last: LocalImportDescriptor? = null
    override fun observeLocalMedia(): Flow<List<LocalMedia>> = flowOf(emptyList())
    override suspend fun import(descriptor: LocalImportDescriptor): LocalMedia {
        last = descriptor
        return LocalMedia(MediaKey(SourceId("local"), MediaType.NOVEL, RemoteId(descriptor.fileName)), descriptor.fileName, descriptor.format, 1)
        Unit
    }
    override suspend fun remove(key: MediaKey) = Unit
}

class ImportLocalFileTest {
    @Test
    fun `valid txt passes`() = runBlocking {
        val repo = FakeLocal()
        val out = ImportLocalFile(repo)(
            LocalImportDescriptor("book.txt", LocalFormat.TXT, 100, "import/book.txt"),
        )
        assertEquals("book.txt", out.title)
        Unit
    }

    @Test
    fun `extension mismatch rejected`() = runBlocking {
        assertFailsWith<AppError.InvalidInput> {
            ImportLocalFile(FakeLocal())(
                LocalImportDescriptor("book.pdf", LocalFormat.TXT, 100, "import/book.pdf"),
            )
        }
        Unit
    }

    @Test
    fun `oversize rejected`() = runBlocking {
        assertFailsWith<AppError.InvalidInput> {
            ImportLocalFile(FakeLocal(), maxBytes = 10)(
                LocalImportDescriptor("big.cbz", LocalFormat.CBZ, 11, "import/big.cbz"),
            )
        }
        Unit
    }

    @Test
    fun `empty file rejected`() = runBlocking {
        assertFailsWith<AppError.InvalidInput> {
            ImportLocalFile(FakeLocal())(
                LocalImportDescriptor("e.epub", LocalFormat.EPUB, 0, "import/e.epub"),
            )
        }
        Unit
    }
}

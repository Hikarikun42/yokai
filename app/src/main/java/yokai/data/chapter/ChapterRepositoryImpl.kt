package yokai.data.chapter

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.util.system.toInt
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.chapter.ChapterRepository
import yokai.domain.chapter.models.ChapterUpdate

class ChapterRepositoryImpl(private val handler: DatabaseHandler) : ChapterRepository {
    override suspend fun getChapter(chapterId: Long): Chapter? =
        handler.awaitOneOrNull { chaptersQueries.find(chapterId, Chapter::mapper) }
    override suspend fun getChapter(url: String): Chapter? =
        handler.awaitOneOrNull { chaptersQueries.findByUrl(url, Chapter::mapper) }

    override suspend fun getChapters(mangaId: Long, filterScanlators: Boolean): List<Chapter> =
        handler.awaitList { chaptersQueries.getChaptersByMangaId(mangaId, filterScanlators.toInt().toLong(), Chapter::mapper) }

    override fun getChaptersAsFlow(mangaId: Long, filterScanlators: Boolean): Flow<List<Chapter>> =
        handler.subscribeToList { chaptersQueries.getChaptersByMangaId(mangaId, filterScanlators.toInt().toLong(), Chapter::mapper) }

    override suspend fun getScanlatorsByChapter(mangaId: Long): List<String> =
        handler.awaitList { chaptersQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() } }

    override fun getScanlatorsByChapterAsFlow(mangaId: Long): Flow<List<String>> =
        handler.subscribeToList { chaptersQueries.getScanlatorsByMangaId(mangaId) { it.orEmpty() } }

    override suspend fun delete(chapter: Chapter) =
        try {
            partialDelete(chapter)
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to delete chapter with id '${chapter.id}'" }
            false
        }

    override suspend fun deleteAll(chapters: List<Chapter>) =
        try {
            partialDelete(*chapters.toTypedArray())
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bulk delete chapters" }
            false
        }

    private suspend fun partialDelete(vararg chapters: Chapter) {
        handler.await(inTransaction = true) {
            chapters.forEach { chapter ->
                if (chapter.id == null) return@forEach
                chaptersQueries.delete(chapter.id!!)
            }
        }
    }

    override suspend fun update(update: ChapterUpdate): Boolean =
        try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to update chapter with id '${update.id}'" }
            false
        }

    override suspend fun updateAll(updates: List<ChapterUpdate>): Boolean =
        try {
            partialUpdate(*updates.toTypedArray())
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bulk update chapters" }
            false
        }

    private suspend fun partialUpdate(vararg updates: ChapterUpdate) {
        handler.await(inTransaction = true) {
            updates.forEach { update ->
                chaptersQueries.update(
                    chapterId = update.id,
                    mangaId = update.mangaId,
                    url = update.url,
                    name = update.name,
                    scanlator = update.scanlator,
                    read = update.read,
                    bookmark = update.bookmark,
                    lastPageRead = update.lastPageRead,
                    pagesLeft = update.pagesLeft,
                    chapterNumber = update.chapterNumber,
                    sourceOrder = update.sourceOrder,
                    dateFetch = update.dateFetch,
                    dateUpload = update.dateUpload
                )
            }
        }
    }

    override suspend fun insert(chapter: Chapter): Long? {
        if (chapter.manga_id == null) return null

        return handler.awaitOneOrNullExecutable(inTransaction = true) {
            chaptersQueries.insert(
                mangaId = chapter.manga_id!!,
                url = chapter.url,
                name = chapter.name,
                scanlator = chapter.scanlator,
                read = chapter.read,
                bookmark = chapter.bookmark,
                lastPageRead = chapter.last_page_read.toLong(),
                pagesLeft = chapter.pages_left.toLong(),
                chapterNumber = chapter.chapter_number.toDouble(),
                sourceOrder = chapter.source_order.toLong(),
                dateFetch = chapter.date_fetch,
                dateUpload = chapter.date_upload,
            )
            chaptersQueries.selectLastInsertedRowId()
        }
    }
}

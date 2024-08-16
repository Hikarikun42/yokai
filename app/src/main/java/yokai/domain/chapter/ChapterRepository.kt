package yokai.domain.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.MangaChapter
import kotlinx.coroutines.flow.Flow
import yokai.domain.chapter.models.ChapterUpdate

interface ChapterRepository {
    suspend fun getChapters(mangaId: Long, filterScanlators: Boolean): List<Chapter>
    fun getChaptersAsFlow(mangaId: Long, filterScanlators: Boolean): Flow<List<Chapter>>

    suspend fun getChaptersByUrl(url: String, filterScanlators: Boolean): List<Chapter>

    suspend fun getRecents(filterScanlators: Boolean, search: String = "", limit: Long = 25L, offset: Long = 0L): List<MangaChapter>

    suspend fun getScanlatorsByChapter(mangaId: Long): List<String>
    fun getScanlatorsByChapterAsFlow(mangaId: Long): Flow<List<String>>

    suspend fun delete(chapter: Chapter): Boolean
    suspend fun deleteAll(chapters: List<Chapter>): Boolean

    suspend fun update(update: ChapterUpdate): Boolean
    suspend fun updateAll(updates: List<ChapterUpdate>): Boolean

    suspend fun insert(chapter: Chapter): Long?
}

package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.resolvers.ChapterKnownBackupPutResolver
import eu.kanade.tachiyomi.data.database.resolvers.ChapterProgressPutResolver
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.domain.manga.models.Manga

interface ChapterQueries : DbProvider {

    fun getChapters(manga: Manga) = getChapters(manga.id)

    fun getChapters(mangaId: Long?) = db.get()
        .listOfObjects(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_MANGA_ID} = ?")
                .whereArgs(mangaId)
                .build(),
        )
        .prepare()

    fun getChapter(id: Long) = db.get()
        .`object`(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_ID} = ?")
                .whereArgs(id)
                .build(),
        )
        .prepare()

    fun getChapter(url: String) = db.get()
        .`object`(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_URL} = ?")
                .whereArgs(url)
                .build(),
        )
        .prepare()

    fun getChapters(url: String) = db.get()
        .listOfObjects(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_URL} = ?")
                .whereArgs(url)
                .build(),
        )
        .prepare()

    fun getChapter(url: String, mangaId: Long) = db.get()
        .`object`(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_URL} = ? AND ${ChapterTable.COL_MANGA_ID} = ?")
                .whereArgs(url, mangaId)
                .build(),
        )
        .prepare()

    // FIXME: Migrate to SQLDelight, on halt: in StorIO transaction
    fun insertChapters(chapters: List<Chapter>) = db.put().objects(chapters).prepare()

    fun updateKnownChaptersBackup(chapters: List<Chapter>) = db.put()
        .objects(chapters)
        .withPutResolver(ChapterKnownBackupPutResolver())
        .prepare()

    fun updateChapterProgress(chapter: Chapter) = db.put()
        .`object`(chapter)
        .withPutResolver(ChapterProgressPutResolver())
        .prepare()

    fun updateChaptersProgress(chapters: List<Chapter>) = db.put()
        .objects(chapters)
        .withPutResolver(ChapterProgressPutResolver())
        .prepare()
}

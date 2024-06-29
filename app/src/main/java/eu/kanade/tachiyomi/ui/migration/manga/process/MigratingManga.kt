package eu.kanade.tachiyomi.ui.migration.manga.process

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.view.DeferredField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.models.Manga
import kotlin.coroutines.CoroutineContext

class MigratingManga(
    private val getManga: GetManga,
    private val sourceManager: SourceManager,
    val mangaId: Long,
    parentContext: CoroutineContext,
) {
    val searchResult = DeferredField<Long?>()

    // <MAX, PROGRESS>
    val progress = MutableStateFlow(1 to 0)

    val migrationJob = parentContext + SupervisorJob() + Dispatchers.Default

    var migrationStatus: Int = MigrationStatus.RUNNUNG

    @Volatile
    private var manga: Manga? = null
    suspend fun manga(): Manga? {
        if (manga == null) manga = getManga.awaitById(mangaId)
        return manga
    }

    suspend fun mangaSource(): Source {
        return sourceManager.getOrStub(manga()?.source ?: -1)
    }

    fun toModal(): MigrationProcessItem {
        // Create the model object.
        return MigrationProcessItem(this)
    }
}

class MigrationStatus {
    companion object {
        val RUNNUNG = 0
        val MANGA_FOUND = 1
        val MANGA_NOT_FOUND = 2
    }
}

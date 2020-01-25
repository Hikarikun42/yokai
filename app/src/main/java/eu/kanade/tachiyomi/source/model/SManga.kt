package eu.kanade.tachiyomi.source.model

import java.io.Serializable

interface SManga : Serializable {

    var url: String

    var title: String

    var artist: String?

    var author: String?

    var description: String?

    var genre: String?

    var lang_flag: String?

    var status: Int

    var follow_status: FollowStatus?

    var thumbnail_url: String?

    var initialized: Boolean

    var anilist_id: String?

    var my_anime_list_id: String?

    var kitsu_id: String?

    var manga_updates_id: String?

    var anime_planet_id: String?

    fun copyFrom(other: SManga) {

        if (url.isEmpty()) {
            url = other.url
        }
        if (title.isEmpty()) {
            title = other.title
        }

        if (other.author != null)
            author = other.author

        if (other.artist != null)
            artist = other.artist

        if (other.description != null)
            description = other.description

        if (other.lang_flag != null)
            lang_flag = other.lang_flag

        if (other.genre != null)
            genre = other.genre

        if (other.thumbnail_url != null)
            thumbnail_url = other.thumbnail_url

        if (other.follow_status != null)
            follow_status = other.follow_status

        if (other.anilist_id != null)
            anilist_id = other.anilist_id

        if (other.kitsu_id != null)
            kitsu_id = other.kitsu_id

        if (other.my_anime_list_id != null)
            my_anime_list_id = other.my_anime_list_id

        if (other.anime_planet_id != null)
            anime_planet_id = other.anime_planet_id

        if (other.manga_updates_id != null)
            manga_updates_id = other.manga_updates_id

        status = other.status

        if (!initialized)
            initialized = other.initialized
    }

    enum class FollowStatus {
        UNFOLLOWED,
        READING,
        COMPLETED,
        ON_HOLD,
        PLAN_TO_READ,
        DROPPED,
        RE_READING;

        companion object {}
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLICATION_COMPLETE = 4
        const val CANCELLED = 5
        const val HIATUS = 6

        fun create(): SManga {
            return SMangaImpl()
        }
    }

}
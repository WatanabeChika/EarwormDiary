package com.example.earwormdiary.data.model

import android.net.Uri
import androidx.core.net.toUri
import java.time.LocalDate

object RemoteSongSource {
    const val NETEASE = "NETEASE"
    const val NETEASE_PODCAST = "NETEASE_PODCAST"
    const val QQ_MUSIC = "QQ_MUSIC"
}

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val uri: Uri,
    val albumArtUri: Uri,
    val lastModified: Long = 0L,
    val remoteSource: String? = null,
    val remoteId: String? = null
) {
    val isNone: Boolean
        get() = uri.toString() == "app://none"

    val isText: Boolean
        get() = uri.toString() == "app://text"

    val isRemote: Boolean
        get() = !remoteSource.isNullOrBlank() || albumArtUri.toString().startsWith("http")

    val displayRemoteId: String?
        get() = when {
            remoteId.isNullOrBlank() && isRemote -> id.toString()
            remoteId.isNullOrBlank() -> null
            else -> remoteId
        }

    companion object {
        fun createNone(): LocalSong {
            return LocalSong(
                id = -1L,
                title = "无",
                artist = "无",
                albumId = -1L,
                uri = "app://none".toUri(),
                albumArtUri = Uri.EMPTY
            )
        }

        fun createText(text: String): LocalSong {
            return LocalSong(
                id = text.hashCode().toLong(),
                title = text,
                artist = "无",
                albumId = -1L,
                uri = "app://text".toUri(),
                albumArtUri = Uri.EMPTY
            )
        }
    }
}

data class RecordEntry(
    val song: LocalSong,
    val categoryId: String? = null
)

data class DailyRecord(
    val date: LocalDate,
    val entries: List<RecordEntry>
) {
    init {
        require(entries.isNotEmpty()) { "DailyRecord must contain at least one song entry." }
        require(entries.size <= MAX_SONGS_PER_DAY) { "DailyRecord can contain at most $MAX_SONGS_PER_DAY songs." }
    }

    val songCount: Int
        get() = entries.size

    val canAddMore: Boolean
        get() = entries.size < MAX_SONGS_PER_DAY

    val songs: List<LocalSong>
        get() = entries.map { it.song }

    val primaryEntry: RecordEntry
        get() = entries.first()

    fun addSong(song: LocalSong): DailyRecord {
        require(canAddMore) { "DailyRecord already contains $MAX_SONGS_PER_DAY songs." }
        return copy(entries = entries + RecordEntry(song))
    }

    fun replaceSong(index: Int, song: LocalSong): DailyRecord {
        require(index in entries.indices) { "Song index out of bounds: $index" }
        return copy(
            entries = entries.mapIndexed { currentIndex, entry ->
                if (currentIndex == index) entry.copy(song = song) else entry
            }
        )
    }

    fun removeSong(index: Int): DailyRecord? {
        require(index in entries.indices) { "Song index out of bounds: $index" }
        val updatedEntries = entries.filterIndexed { currentIndex, _ -> currentIndex != index }
        return if (updatedEntries.isEmpty()) null else copy(entries = updatedEntries)
    }

    fun updateCategory(index: Int, categoryId: String?): DailyRecord {
        require(index in entries.indices) { "Song index out of bounds: $index" }
        return copy(
            entries = entries.mapIndexed { currentIndex, entry ->
                if (currentIndex == index) entry.copy(categoryId = categoryId) else entry
            }
        )
    }

    companion object {
        const val MAX_SONGS_PER_DAY = 3

        fun single(date: LocalDate, song: LocalSong, categoryId: String? = null): DailyRecord {
            return DailyRecord(
                date = date,
                entries = listOf(RecordEntry(song = song, categoryId = categoryId))
            )
        }
    }
}

data class Category(
    val id: String,
    val name: String,
    val archived: Boolean = false
)

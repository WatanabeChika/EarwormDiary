package com.example.earwormdiary.data.local

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.LocalSong
import com.example.earwormdiary.data.model.RecordEntry
import com.example.earwormdiary.data.model.RemoteSongSource
import com.example.earwormdiary.utils.loadMusicFromCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

object RecordStorage {
    private const val FILE_NAME = "daily_records.json"

    fun saveRecords(context: Context, records: Map<LocalDate, DailyRecord>) {
        try {
            val jsonArray = JSONArray()
            records.forEach { (date, record) ->
                val jsonObj = JSONObject().apply {
                    put("date", date.toString())
                    put("entries", JSONArray().apply {
                        record.entries.forEach { entry ->
                            put(
                                JSONObject().apply {
                                    put("song", songToJson(entry.song))
                                    if (entry.categoryId != null) {
                                        put("categoryId", entry.categoryId)
                                    }
                                }
                            )
                        }
                    })
                }
                jsonArray.put(jsonObj)
            }
            File(context.filesDir, FILE_NAME).writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadRecords(context: Context): Map<LocalDate, DailyRecord> {
        val resultMap = mutableMapOf<LocalDate, DailyRecord>()
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return resultMap

        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val date = LocalDate.parse(obj.getString("date"))

                val entries = if (obj.has("entries")) {
                    val entriesArray = obj.getJSONArray("entries")
                    buildList {
                        for (entryIndex in 0 until entriesArray.length()) {
                            val entryObj = entriesArray.getJSONObject(entryIndex)
                            val songObj = entryObj.getJSONObject("song")
                            add(
                                RecordEntry(
                                    song = songFromJson(songObj),
                                    categoryId = entryObj.optString("categoryId").ifBlank { null }
                                )
                            )
                        }
                    }
                } else {
                    val categoryId = obj.optString("categoryId").ifBlank { null }
                    val songObj = obj.getJSONObject("song")
                    listOf(
                        RecordEntry(
                            song = songFromJson(songObj),
                            categoryId = categoryId
                        )
                    )
                }

                if (entries.isNotEmpty()) {
                    resultMap[date] = DailyRecord(date = date, entries = entries.take(DailyRecord.MAX_SONGS_PER_DAY))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultMap
    }

    fun exportDataToUri(
        context: Context,
        uri: Uri,
        records: Map<LocalDate, DailyRecord>,
        categories: List<Category>
    ): Boolean {
        return try {
            val jsonArray = JSONArray()
            records.toSortedMap().forEach { (date, record) ->
                val jsonObj = JSONObject().apply {
                    put("date", date.toString())
                    put("songCount", record.songCount)

                    record.entries.forEachIndexed { index, entry ->
                        val suffix = exportFieldSuffix(index)
                        put("title$suffix", entry.song.title)
                        put("artist$suffix", entry.song.artist)

                        val uriStr = entry.song.uri.toString()
                        val artStr = entry.song.albumArtUri.toString()
                        val remoteSource = entry.song.remoteSource
                        val remoteId = entry.song.displayRemoteId

                        when {
                            !remoteSource.isNullOrBlank() || uriStr.startsWith("http") || artStr.startsWith("http") -> {
                                put("sourceType$suffix", remoteSource ?: RemoteSongSource.NETEASE)
                                put("remoteId$suffix", remoteId ?: entry.song.id.toString())
                                put("uri$suffix", uriStr)
                                put("albumArtUri$suffix", artStr)
                            }
                            entry.song.isText -> put("sourceType$suffix", "TEXT")
                            entry.song.isNone -> put("sourceType$suffix", "NONE")
                            else -> put("sourceType$suffix", "LOCAL")
                        }

                        val categoryName = categories.find { it.id == entry.categoryId }?.name
                        if (categoryName != null) {
                            put("category$suffix", categoryName)
                        }
                    }
                }
                jsonArray.put(jsonObj)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonArray.toString(4).toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDataFromUri(
        context: Context,
        uri: Uri
    ): Triple<Map<LocalDate, DailyRecord>, List<Category>, List<String>> {
        val resultMap = mutableMapOf<LocalDate, DailyRecord>()
        val currentCategories = CategoryStorage.loadCategories(context).toMutableList()
        val warningMessages = mutableListOf<String>()
        var categoriesChanged = false

        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: return Triple(emptyMap(), currentCategories, emptyList())

            if (jsonString.isBlank()) return Triple(emptyMap(), currentCategories, emptyList())

            val allSongs = loadMusicFromCache(context)
            val localSongGroups = allSongs.groupBy { it.title }

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (!obj.has("date") || !hasAnySongTitle(obj)) continue

                val date = LocalDate.parse(obj.getString("date"))
                val importedEntries = mutableListOf<RecordEntry>()

                val songCount = detectImportedSongCount(obj)
                for (songIndex in 0 until songCount) {
                    val suffix = exportFieldSuffix(songIndex)
                    val title = obj.optString("title$suffix").trim()
                    if (title.isBlank()) continue

                    val artist = obj.optString("artist$suffix", "")
                    val categoryName = obj.optString("category$suffix", "")
                    val categoryId = resolveCategoryId(
                        categoryName = categoryName,
                        currentCategories = currentCategories
                    ).also { categoriesChanged = categoriesChanged || it.second }.first

                    val song = resolveImportedSong(
                        obj = obj,
                        title = title,
                        artist = artist,
                        songIndex = songIndex,
                        date = date,
                        localSongGroups = localSongGroups,
                        warningMessages = warningMessages
                    )

                    importedEntries.add(RecordEntry(song = song, categoryId = categoryId))
                    if (importedEntries.size == DailyRecord.MAX_SONGS_PER_DAY) break
                }

                if (importedEntries.isNotEmpty()) {
                    resultMap[date] = DailyRecord(date = date, entries = importedEntries)
                }
            }

            if (categoriesChanged) {
                CategoryStorage.saveCategories(context, currentCategories)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            warningMessages.add("Import failed: ${e.message}")
        }

        return Triple(resultMap, currentCategories, warningMessages)
    }

    private fun songToJson(song: LocalSong): JSONObject {
        return JSONObject().apply {
            put("id", song.id)
            put("title", song.title)
            put("artist", song.artist)
            put("albumId", song.albumId)
            put("uri", song.uri.toString())
            put("albumArtUri", song.albumArtUri.toString())
            put("lastModified", song.lastModified)
            if (!song.remoteSource.isNullOrBlank()) {
                put("remoteSource", song.remoteSource)
            }
            if (!song.remoteId.isNullOrBlank()) {
                put("remoteId", song.remoteId)
            }
        }
    }

    private fun songFromJson(songObj: JSONObject): LocalSong {
        val remoteSource = songObj.optString("remoteSource").ifBlank { null }
        val remoteId = songObj.optString("remoteId").ifBlank { null }
        return LocalSong(
            id = songObj.getLong("id"),
            title = songObj.getString("title"),
            artist = songObj.getString("artist"),
            albumId = songObj.getLong("albumId"),
            uri = songObj.getString("uri").toUri(),
            albumArtUri = songObj.getString("albumArtUri").toUri(),
            lastModified = songObj.optLong("lastModified", 0L),
            remoteSource = remoteSource,
            remoteId = remoteId
        )
    }

    private fun exportFieldSuffix(index: Int): String {
        return if (index == 0) "" else "${index + 1}"
    }

    private fun hasAnySongTitle(obj: JSONObject): Boolean {
        return (0 until DailyRecord.MAX_SONGS_PER_DAY).any { index ->
            obj.optString("title${exportFieldSuffix(index)}").isNotBlank()
        }
    }

    private fun detectImportedSongCount(obj: JSONObject): Int {
        val explicitCount = obj.optInt("songCount", 0)
        if (explicitCount > 0) {
            return explicitCount.coerceIn(1, DailyRecord.MAX_SONGS_PER_DAY)
        }

        var detectedCount = 0
        for (index in 0 until DailyRecord.MAX_SONGS_PER_DAY) {
            val suffix = exportFieldSuffix(index)
            if (obj.optString("title$suffix").isNotBlank()) {
                detectedCount = index + 1
            }
        }
        return detectedCount.coerceAtLeast(1)
    }

    private fun resolveCategoryId(
        categoryName: String,
        currentCategories: MutableList<Category>
    ): Pair<String?, Boolean> {
        if (categoryName.isBlank()) return Pair(null, false)

        val existingCategory = currentCategories.find { it.name == categoryName }
        if (existingCategory != null) {
            return Pair(existingCategory.id, false)
        }

        val newId = UUID.randomUUID().toString()
        currentCategories.add(Category(id = newId, name = categoryName))
        return Pair(newId, true)
    }

    private fun resolveImportedSong(
        obj: JSONObject,
        title: String,
        artist: String,
        songIndex: Int,
        date: LocalDate,
        localSongGroups: Map<String, List<LocalSong>>,
        warningMessages: MutableList<String>
    ): LocalSong {
        val matches = localSongGroups[title]

        if (!matches.isNullOrEmpty()) {
            if (matches.size == 1) {
                return matches[0]
            }

            val artistMatch = matches.find {
                it.artist.equals(artist, ignoreCase = true) ||
                    (artist.isNotBlank() && it.artist.contains(artist, ignoreCase = true))
            }

            if (artistMatch != null) {
                return artistMatch
            }

            warningMessages.add(
                "Date $date song ${songIndex + 1}: [$title] matched multiple local files. Defaulted to ${matches[0].artist}."
            )
            return matches[0]
        }

        val suffix = exportFieldSuffix(songIndex)
        val sourceType = obj.optString("sourceType$suffix", "")
        val remoteId = obj.optString("remoteId$suffix").ifBlank { null }
        return when (sourceType) {
            RemoteSongSource.NETEASE,
            RemoteSongSource.NETEASE_PODCAST,
            RemoteSongSource.QQ_MUSIC -> {
                val resolvedRemoteId = remoteId ?: obj.optLong("remoteId$suffix", 0L).takeIf { it != 0L }?.toString()
                LocalSong(
                    id = resolvedRemoteId?.toLongOrNull() ?: resolvedRemoteId?.hashCode()?.toLong() ?: 0L,
                    title = title,
                    artist = artist.ifBlank { "Unknown" },
                    albumId = 0L,
                    uri = obj.optString("uri$suffix", "").toUri(),
                    albumArtUri = obj.optString("albumArtUri$suffix", "").toUri(),
                    lastModified = System.currentTimeMillis(),
                    remoteSource = sourceType,
                    remoteId = resolvedRemoteId
                )
            }
            "NONE" -> LocalSong.createNone()
            else -> LocalSong.createText(title)
        }
    }
}

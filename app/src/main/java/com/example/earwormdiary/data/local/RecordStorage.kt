package com.example.earwormdiary.data.local

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.LocalSong
import com.example.earwormdiary.utils.loadMusicFromCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

object RecordStorage {
    private const val FILE_NAME = "daily_records.json"

    // 保存记录
    fun saveRecords(context: Context, records: Map<LocalDate, DailyRecord>) {
        try {
            val jsonArray = JSONArray()
            records.forEach { (date, record) ->
                val jsonObj = JSONObject().apply {
                    put("date", date.toString())
                    if (record.categoryId != null) put("categoryId", record.categoryId)

                    val songObj = JSONObject().apply {
                        put("id", record.song.id)
                        put("title", record.song.title)
                        put("artist", record.song.artist)
                        put("albumId", record.song.albumId)
                        put("uri", record.song.uri.toString())
                        put("albumArtUri", record.song.albumArtUri.toString())
                        put("lastModified", record.song.lastModified)
                    }
                    put("song", songObj)
                }
                jsonArray.put(jsonObj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 加载记录
    fun loadRecords(context: Context): Map<LocalDate, DailyRecord> {
        val resultMap = mutableMapOf<LocalDate, DailyRecord>()
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return resultMap

        try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val date = LocalDate.parse(obj.getString("date"))
                val categoryId = if (obj.has("categoryId")) obj.getString("categoryId") else null
                val songObj = obj.getJSONObject("song")

                val song = LocalSong(
                    id = songObj.getLong("id"),
                    title = songObj.getString("title"),
                    artist = songObj.getString("artist"),
                    albumId = songObj.getLong("albumId"),
                    uri = songObj.getString("uri").toUri(),
                    albumArtUri = songObj.getString("albumArtUri").toUri(),
                    lastModified = songObj.optLong("lastModified", 0L)
                )
                resultMap[date] = DailyRecord(date, song, categoryId)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return resultMap
    }

    // 导出数据
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
                    put("title", record.song.title)
                    put("artist", record.song.artist)

                    val uriStr = record.song.uri.toString()
                    val artStr = record.song.albumArtUri.toString()

                    // 判断来源：如果是 http 开头，说明是网络歌曲
                    if (uriStr.startsWith("http") || artStr.startsWith("http")) {
                        put("sourceType", "NETEASE")
                        put("remoteId", record.song.id)
                        put("uri", uriStr)
                        put("albumArtUri", artStr)
                    } else if (record.song.isText) {
                        put("sourceType", "TEXT")
                    } else if (record.song.isNone) {
                        put("sourceType", "NONE")
                    } else {
                        put("sourceType", "LOCAL")
                    }

                    // 导出分类名称
                    val categoryName = categories.find { it.id == record.categoryId }?.name
                    if (categoryName != null) {
                        put("category", categoryName)
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

    // 导入数据
    suspend fun importDataFromUri(context: Context, uri: Uri): Triple<Map<LocalDate, DailyRecord>, List<Category>, List<String>> {
        val resultMap = mutableMapOf<LocalDate, DailyRecord>()
        val currentCategories = CategoryStorage.loadCategories(context).toMutableList()
        val warningMessages = mutableListOf<String>()
        var categoriesChanged = false

        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().use { it.readText() }
            } ?: return Triple(emptyMap(), currentCategories, emptyList())

            if (jsonString.isBlank()) return Triple(emptyMap(), currentCategories, emptyList())

            // 1. 加载本地歌曲库，使用 groupBy 处理重名情况
            val allSongs = loadMusicFromCache(context)
            val localSongGroups = allSongs.groupBy { it.title }

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (!obj.has("date") || !obj.has("title")) continue

                val date = LocalDate.parse(obj.getString("date"))
                val title = obj.getString("title")
                val jsonArtist = obj.optString("artist", "")

                val categoryName = if (obj.has("category")) obj.getString("category") else ""
                var categoryId: String? = null
                if (categoryName.isNotBlank()) {
                    val existingCat = currentCategories.find { it.name == categoryName }
                    if (existingCat != null) {
                        categoryId = existingCat.id
                    } else {
                        val newId = UUID.randomUUID().toString()
                        val newCat = Category(newId, categoryName)
                        currentCategories.add(newCat)
                        categoriesChanged = true
                        categoryId = newId
                    }
                }

                val sourceType = obj.optString("sourceType", "")
                var finalSong: LocalSong? = null

                // 尝试在本地库中查找同名歌曲
                val matches = localSongGroups[title]

                if (matches != null && matches.isNotEmpty()) {
                    if (matches.size == 1) {
                        // A. 只有一首同名歌曲 -> 直接使用
                        finalSong = matches[0]
                    } else {
                        // B. 有多首同名歌曲 -> 尝试匹配歌手
                        // 尝试找到歌手名包含 jsonArtist 或者被 jsonArtist 包含的记录 (模糊匹配)
                        val artistMatch = matches.find {
                            it.artist.equals(jsonArtist, ignoreCase = true) ||
                                    (jsonArtist.isNotBlank() && it.artist.contains(jsonArtist, ignoreCase = true))
                        }

                        if (artistMatch != null) {
                            finalSong = artistMatch
                        } else {
                            // Fallback: 默认选第一个
                            finalSong = matches[0]

                            val conflictMsg = if (jsonArtist.isNotBlank()) {
                                "日期 $date: 歌曲 [$title] 本地有多个版本，且未找到歌手 [$jsonArtist]。已默认选择: ${finalSong.artist}"
                            } else {
                                "日期 $date: 歌曲 [$title] 本地有多个版本(纯文字记录)。已默认选择: ${finalSong.artist}"
                            }
                            warningMessages.add(conflictMsg)
                        }
                    }
                }

                // 最终决策
                val song = // 1. 即使原记录是 NETEASE 或 TEXT，只要本地找到了同名歌，就优先用本地的
                    finalSong
                        ?: // 2. 本地没找到，按原 sourceType 恢复
                        when (sourceType) {
                            "NETEASE" -> {
                                LocalSong(
                                    id = obj.optLong("remoteId", 0L),
                                    title = title,
                                    artist = jsonArtist.ifBlank { "Unknown" },
                                    albumId = 0,
                                    uri = obj.optString("uri", "").toUri(),
                                    albumArtUri = obj.optString("albumArtUri", "").toUri(),
                                    lastModified = System.currentTimeMillis()
                                )
                            }

                            "NONE" -> LocalSong.createNone()
                            // TEXT 或 LOCAL(但本地文件已丢失) -> 降级为纯文字
                            else -> LocalSong.createText(title)
                        }

                resultMap[date] = DailyRecord(date, song, categoryId)
            }

            if (categoriesChanged) {
                CategoryStorage.saveCategories(context, currentCategories)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            warningMessages.add("导入过程中发生严重错误: ${e.message}")
        }

        return Triple(resultMap, currentCategories, warningMessages)
    }
}
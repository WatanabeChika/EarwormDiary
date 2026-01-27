package com.example.earwormdiary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import androidx.core.net.toUri

object RecordStorage {
    private const val FILE_NAME = "daily_records.json"

    // 保存记录到文件
    fun saveRecords(context: Context, records: Map<LocalDate, DailyRecord>) {
        try {
            val jsonArray = JSONArray()
            records.forEach { (date, record) ->
                val jsonObj = JSONObject().apply {
                    put("date", date.toString()) // 2026-01-26

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

            // 写入文件
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 从文件读取记录
    fun loadRecords(context: Context): Map<LocalDate, DailyRecord> {
        val resultMap = mutableMapOf<LocalDate, DailyRecord>()
        val file = File(context.filesDir, FILE_NAME)

        if (!file.exists()) return resultMap

        try {
            val jsonString = file.readText()
            if (jsonString.isBlank()) return resultMap

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val dateStr = obj.getString("date")
                val date = LocalDate.parse(dateStr)

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

                resultMap[date] = DailyRecord(date, song)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return resultMap
    }
}
package com.example.earwormdiary.data.network

import androidx.core.net.toUri
import com.example.earwormdiary.data.model.LocalSong
import com.example.earwormdiary.data.model.RemoteSongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object QqMusicApi {
    private const val SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val DETAIL_API = "https://u.y.qq.com/cgi-bin/musicu.fcg"

    suspend fun searchSongs(keyword: String, limit: Int): List<LocalSong> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()

        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val urlString = "$SEARCH_API?format=json&p=1&n=$limit&w=$encodedKeyword"
        val response = request(urlString) ?: return@withContext emptyList()
        val jsonObject = JSONObject(stripJsonp(response))
        val songs = jsonObject.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return@withContext emptyList()

        return@withContext buildList {
            for (index in 0 until songs.length()) {
                runCatching {
                    parseSongJsonObject(songs.getJSONObject(index))
                }.getOrNull()?.let(::add)
            }
        }
    }

    suspend fun getSongDetail(songMid: String): LocalSong? = withContext(Dispatchers.IO) {
        val requestJson = """
            {
              "songinfo": {
                "module": "music.pf_song_detail_svr",
                "method": "get_song_detail_yqq",
                "param": {
                  "song_mid": "$songMid",
                  "song_type": 0
                }
              }
            }
        """.trimIndent()

        val response = request("$DETAIL_API?format=json&data=${URLEncoder.encode(requestJson, "UTF-8")}")
            ?: return@withContext null
        val trackInfo = JSONObject(stripJsonp(response))
            .optJSONObject("songinfo")
            ?.optJSONObject("data")
            ?.optJSONObject("track_info")

        trackInfo?.let {
            return@withContext runCatching {
                parseSongJsonObject(it)
            }.getOrNull()
        }

        return@withContext searchSongs(songMid, limit = 5).firstOrNull { song ->
            song.displayRemoteId.equals(songMid, ignoreCase = true)
        }
    }

    private fun parseSongJsonObject(item: JSONObject): LocalSong {
        val songMid = item.optString("songmid").ifBlank {
            item.optString("mid")
        }
        val songName = item.optString("songname").ifBlank {
            item.optString("name")
        }
        val singers = item.optJSONArray("singer")
        val artist = buildList {
            if (singers != null) {
                for (index in 0 until singers.length()) {
                    singers.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.joinToString(" / ").ifBlank { "Unknown" }

        val albumMid = item.optString("albummid").ifBlank {
            item.optJSONObject("album")?.optString("mid").orEmpty()
        }
        val artUrl = when {
            albumMid.isBlank() -> ""
            else -> "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
        }

        return LocalSong(
            id = songMid.hashCode().toLong(),
            title = songName,
            artist = artist,
            albumId = 0L,
            uri = "https://y.qq.com/n/ryqq/songDetail/$songMid".toUri(),
            albumArtUri = artUrl.toUri(),
            lastModified = System.currentTimeMillis(),
            remoteSource = RemoteSongSource.QQ_MUSIC,
            remoteId = songMid
        )
    }

    private fun stripJsonp(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val openBracketIndex = trimmed.indexOf('(')
        val closeBracketIndex = trimmed.lastIndexOf(')')
        return if (openBracketIndex in 1..<closeBracketIndex) {
            trimmed.substring(openBracketIndex + 1, closeBracketIndex)
        } else {
            trimmed
        }
    }

    private fun request(urlString: String): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.setRequestProperty("Referer", "https://y.qq.com/")
            connection.setRequestProperty("Origin", "https://y.qq.com")

            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }
}

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

object NeteaseApi {
    private const val SEARCH_API = "https://music.163.com/api/cloudsearch/pc"
    private const val SONG_DETAIL_API = "https://music.163.com/api/song/detail"
    private const val PODCAST_DETAIL_API = "https://music.163.com/api/dj/program/detail"

    suspend fun searchSongs(keyword: String, limit: Int): List<LocalSong> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()

        val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
        val urlString = "$SEARCH_API?s=$encodedKeyword&type=1&offset=0&limit=$limit"
        val response = request(urlString, method = "POST") ?: return@withContext emptyList()
        val jsonObj = JSONObject(response)

        if (jsonObj.optInt("code") != 200) {
            return@withContext emptyList()
        }

        val songs = jsonObj.optJSONObject("result")?.optJSONArray("songs") ?: return@withContext emptyList()
        return@withContext buildList {
            for (index in 0 until songs.length()) {
                runCatching {
                    parseSongJsonObject(songs.getJSONObject(index))
                }.getOrNull()?.let(::add)
            }
        }
    }

    suspend fun getSongDetail(id: String): LocalSong? = withContext(Dispatchers.IO) {
        val urlString = "$SONG_DETAIL_API?id=$id&ids=[$id]"
        val response = request(urlString) ?: return@withContext null
        val jsonObj = JSONObject(response)

        if (jsonObj.optInt("code") != 200) {
            return@withContext null
        }

        val songs = jsonObj.optJSONArray("songs") ?: return@withContext null
        if (songs.length() == 0) {
            return@withContext null
        }

        return@withContext runCatching {
            parseSongJsonObject(songs.getJSONObject(0))
        }.getOrNull()
    }

    suspend fun getPodcastDetail(id: String): LocalSong? = withContext(Dispatchers.IO) {
        val response = request("$PODCAST_DETAIL_API?id=$id") ?: return@withContext null
        val jsonObj = JSONObject(response)

        if (jsonObj.optInt("code") != 200) {
            return@withContext null
        }

        val program = jsonObj.optJSONObject("program") ?: return@withContext null
        val mainSong = program.optJSONObject("mainSong")
        val title = mainSong?.optString("name").orEmpty().ifBlank {
            program.optString("name", "")
        }
        if (title.isBlank()) {
            return@withContext null
        }

        val artist = buildPodcastArtist(program, mainSong)
        val coverUrl = normalizeImageUrl(
            program.optString("coverUrl").ifBlank {
                mainSong?.optJSONObject("album")?.optString("picUrl", "").orEmpty()
            }
        )

        return@withContext LocalSong(
            id = id.toLongOrNull() ?: id.hashCode().toLong(),
            title = title,
            artist = artist,
            albumId = 0L,
            uri = "https://music.163.com/#/program?id=$id".toUri(),
            albumArtUri = coverUrl.toUri(),
            lastModified = System.currentTimeMillis(),
            remoteSource = RemoteSongSource.NETEASE_PODCAST,
            remoteId = id
        )
    }

    private fun buildPodcastArtist(program: JSONObject, mainSong: JSONObject?): String {
        val artists = mainSong?.optJSONArray("artists")
        if (artists != null && artists.length() > 0) {
            val names = buildList {
                for (index in 0 until artists.length()) {
                    artists.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            if (names.isNotEmpty()) {
                return names.joinToString(" / ")
            }
        }

        val radioName = program.optJSONObject("radio")?.optString("name").orEmpty()
        if (radioName.isNotBlank()) {
            return radioName
        }

        val djName = program.optJSONObject("dj")?.optString("nickname").orEmpty()
        return djName.ifBlank { "Unknown" }
    }

    private fun parseSongJsonObject(item: JSONObject): LocalSong {
        val id = item.getLong("id")
        val name = item.getString("name")

        val artistName = when {
            item.has("ar") -> item.optJSONArray("ar")?.optJSONObject(0)?.optString("name").orEmpty()
            item.has("artists") -> item.optJSONArray("artists")?.optJSONObject(0)?.optString("name").orEmpty()
            else -> ""
        }.ifBlank { "Unknown" }

        val picUrl = normalizeImageUrl(
            when {
                item.has("al") -> item.optJSONObject("al")?.optString("picUrl", "").orEmpty()
                item.has("album") -> item.optJSONObject("album")?.optString("picUrl", "").orEmpty()
                else -> ""
            }
        )

        return LocalSong(
            id = id,
            title = name,
            artist = artistName,
            albumId = 0,
            uri = "http://music.163.com/song/media/outer/url?id=$id.mp3".toUri(),
            albumArtUri = picUrl.toUri(),
            lastModified = System.currentTimeMillis(),
            remoteSource = RemoteSongSource.NETEASE,
            remoteId = id.toString()
        )
    }

    private fun normalizeImageUrl(url: String): String {
        return when {
            url.startsWith("http://") -> url.replace("http://", "https://")
            else -> url
        }
    }

    private fun request(urlString: String, method: String = "GET"): String? {
        return try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            connection.setRequestProperty("Referer", "https://music.163.com/")
            connection.setRequestProperty("Cookie", "os=pc")

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

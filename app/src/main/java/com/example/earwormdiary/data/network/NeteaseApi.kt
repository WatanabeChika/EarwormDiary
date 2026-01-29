package com.example.earwormdiary.data.network

import androidx.core.net.toUri
import com.example.earwormdiary.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object NeteaseApi {
    private const val SEARCH_API = "https://music.163.com/api/cloudsearch/pc"

    suspend fun searchOnline(keyword: String): List<LocalSong> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LocalSong>()
        if (keyword.isBlank()) return@withContext list

        try {
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val urlString = "$SEARCH_API?s=$encodedKeyword&type=1&offset=0&limit=20"

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            // 伪装 User-Agent 防止被反爬拦截
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.setRequestProperty("Referer", "https://music.163.com/")
            conn.setRequestProperty("Cookie", "os=pc")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonStr)

                // 错误检查
                val code = jsonObj.optInt("code")
                if (code != 200) {
                    return@withContext list
                }

                if (jsonObj.has("result") && !jsonObj.isNull("result")) {
                    val result = jsonObj.getJSONObject("result")

                    if (result.has("songs")) {
                        val songs = result.getJSONArray("songs")

                        for (i in 0 until songs.length()) {
                            try {
                                val item = songs.getJSONObject(i)
                                val id = item.getLong("id")
                                val name = item.getString("name")

                                var artistName = "Unknown"
                                if (item.has("ar")) {
                                    val artists = item.getJSONArray("ar")
                                    if (artists.length() > 0) {
                                        artistName = artists.getJSONObject(0).getString("name")
                                    }
                                } else if (item.has("artists")) {
                                    val artists = item.getJSONArray("artists")
                                    if (artists.length() > 0) {
                                        artistName = artists.getJSONObject(0).getString("name")
                                    }
                                }

                                var picUrl = ""
                                if (item.has("al")) {
                                    val album = item.getJSONObject("al")
                                    picUrl = album.optString("picUrl", "")
                                } else if (item.has("album")) {
                                    val album = item.getJSONObject("album")
                                    picUrl = album.optString("picUrl", "")
                                }

                                // 强制将 http 替换为 https 以适配 Android 9+ 安全策略
                                if (picUrl.startsWith("http://")) {
                                    picUrl = picUrl.replace("http://", "https://")
                                }

                                val musicUrl =
                                    "http://music.163.com/song/media/outer/url?id=$id.mp3"

                                list.add(
                                    LocalSong(
                                        id = id,
                                        title = name,
                                        artist = artistName,
                                        albumId = 0,
                                        uri = musicUrl.toUri(),
                                        albumArtUri = picUrl.toUri(),
                                        lastModified = System.currentTimeMillis()
                                    )
                                )
                            } catch (e: Exception) {
                                // 忽略单个解析失败，继续解析下一个
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext list
    }
}
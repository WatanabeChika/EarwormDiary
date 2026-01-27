package com.example.earwormdiary

import android.net.Uri
import java.time.LocalDate
import androidx.core.net.toUri

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val uri: Uri,
    val albumArtUri: Uri,
    val lastModified: Long = 0L
) {
    // 辅助属性：判断是否是“无”记录
    val isNone: Boolean
        get() = uri.toString() == "app://none"

    // 辅助属性：判断是否是“纯文字”记录
    val isText: Boolean
        get() = uri.toString() == "app://text"

    companion object {
        // 创建一个“无”记录
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

        // 创建一个“纯文字”记录
        fun createText(text: String): LocalSong {
            return LocalSong(
                id = text.hashCode().toLong(), // 用文字哈希做ID
                title = text,
                artist = "无",
                albumId = -1L,
                uri = "app://text".toUri(),
                albumArtUri = Uri.EMPTY
            )
        }
    }
}

data class DailyRecord(
    val date: LocalDate,
    val song: LocalSong
)
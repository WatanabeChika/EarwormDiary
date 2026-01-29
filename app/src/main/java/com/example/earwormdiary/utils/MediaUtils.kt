package com.example.earwormdiary.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import androidx.core.net.toUri
import com.example.earwormdiary.data.model.LocalSong

private const val CACHE_FILE_NAME = "music_index_cache.txt"
private const val TAG = "MusicIndex"

data class SimpleMediaFile(
    val uri: Uri,
    val name: String,
    val lastModified: Long
)

suspend fun buildMusicIndex(context: Context, folderUris: List<Uri>): List<LocalSong> {
    val startTime = System.currentTimeMillis()

    // A. 读取旧缓存
    val oldList = loadMusicFromCache(context)
    val oldCacheMap = oldList.associateBy { Uri.decode(it.uri.toString()) }
    Log.d(TAG, "启动极速扫描... 旧缓存: ${oldList.size}")

    // B. 收集文件
    val collectStartTime = System.currentTimeMillis()

    // 这里不需要并发收集，因为 ContentResolver 的瓶颈在于 IPC，单线程查询最稳定
    val allFiles = withContext(Dispatchers.IO) {
        val list = mutableListOf<SimpleMediaFile>()
        for (treeUri in folderUris) {
            try {
                // 获取根目录的 Document ID
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                // 开始递归
                collectFilesRecursiveFast(context, treeUri, rootDocId, list)
            } catch (e: Exception) {
                Log.e(TAG, "无法访问文件夹: $treeUri", e)
            }
        }
        list
    }

    Log.d(TAG, "文件收集完毕. 数量: ${allFiles.size}. 耗时: ${System.currentTimeMillis() - collectStartTime}ms")

    // C. 并发处理元数据 (这里依然需要并发，因为 setDataSource 是 IO 阻塞操作)
    val semaphore = Semaphore(5)
    var hitCount = 0
    var missCount = 0

    val newSongList = withContext(Dispatchers.IO) {
        allFiles.map { file ->
            async {
                semaphore.withPermit {
                    val result = processFileFast(context, file, oldCacheMap)
                    if (result.second) hitCount++ else missCount++
                    result.first
                }
            }
        }.awaitAll().filterNotNull()
    }

    Log.d(TAG, "索引建立完毕. 命中: $hitCount, 解析: $missCount. 总耗时: ${System.currentTimeMillis() - startTime}ms")

    // D. 保存
    saveCache(context, newSongList)
    return newSongList
}

// 基于 DocId 的递归
private fun collectFilesRecursiveFast(
    context: Context,
    treeUri: Uri,
    parentDocId: String,
    resultList: MutableList<SimpleMediaFile>
) {
    // 构建查询子文件的 URI
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )

    val cursor = try {
        context.contentResolver.query(childrenUri, projection, null, null, null)
    } catch (e: Exception) {
        null
    }

    cursor?.use { c ->
        val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val dateCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        while (c.moveToNext()) {
            // 注意：某些 Provider 可能返回 null，做好防护
            val docId = if (idCol >= 0) c.getString(idCol) else continue
            val name = if (nameCol >= 0) c.getString(nameCol) ?: "Unknown" else "Unknown"
            val mimeType = if (mimeCol >= 0) c.getString(mimeCol) else ""
            val lastModified = if (dateCol >= 0) c.getLong(dateCol) else 0L
            val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

            if (isDir) {
                // 递归关键：使用当前的 treeUri 和 新发现的 docId
                collectFilesRecursiveFast(context, treeUri, docId, resultList)
            } else {
                if (isAudioFile(name)) {
                    // 构建该文件的完整 URI
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    resultList.add(SimpleMediaFile(fileUri, name, lastModified))
                }
            }
        }
    }
}

private fun processFileFast(
    context: Context,
    file: SimpleMediaFile,
    oldCache: Map<String, LocalSong>
): Pair<LocalSong?, Boolean> {
    val fileUriStr = Uri.decode(file.uri.toString())
    val currentLastModified = file.lastModified

    val cachedSong = oldCache[fileUriStr]
    if (cachedSong != null) {
        if (cachedSong.lastModified == currentLastModified && currentLastModified > 0) {
            return Pair(cachedSong, true)
        }
    }

    val retriever = MediaMetadataRetriever()
    val song = try {
        retriever.setDataSource(context, file.uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: file.name.substringBeforeLast(".")
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: "Unknown Artist"

        LocalSong(
            id = file.uri.toString().hashCode().toLong(),
            title = title,
            artist = artist,
            albumId = 0,
            uri = file.uri,
            albumArtUri = file.uri,
            lastModified = currentLastModified
        )
    } catch (e: Exception) {
        LocalSong(
            id = file.uri.toString().hashCode().toLong(),
            title = file.name,
            artist = "Unknown",
            albumId = 0,
            uri = file.uri,
            albumArtUri = file.uri,
            lastModified = currentLastModified
        )
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }

    return Pair(song, false)
}

private fun saveCache(context: Context, songs: List<LocalSong>) {
    val file = File(context.filesDir, CACHE_FILE_NAME)
    try {
        file.bufferedWriter().use { out ->
            songs.forEach { song ->
                val cleanTitle = song.title.replace("|", "")
                val cleanArtist = song.artist.replace("|", "")
                out.write("${song.id}|$cleanTitle|$cleanArtist|${song.uri}|${song.lastModified}\n")
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
}

suspend fun loadMusicFromCache(context: Context): List<LocalSong> {
    return withContext(Dispatchers.IO) {
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists()) return@withContext emptyList()
        val list = mutableListOf<LocalSong>()
        try {
            file.forEachLine { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val id = parts[0].toLongOrNull() ?: 0L
                    val title = parts[1]
                    val artist = parts[2]
                    val uri = parts[3].toUri()
                    val lastMod = parts[4].toLongOrNull() ?: 0L
                    list.add(LocalSong(id, title, artist, 0, uri, uri, lastMod))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        list
    }
}

private fun isAudioFile(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".mp3") || lower.endsWith(".flac") ||
            lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a")
}
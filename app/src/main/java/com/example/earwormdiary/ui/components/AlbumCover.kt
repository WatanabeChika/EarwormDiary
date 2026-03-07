package com.example.earwormdiary.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.earwormdiary.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 保留专为本地音乐封面准备的内存缓存
val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
val bitmapCache = object : LruCache<String, ImageBitmap>(cacheSize) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return (value.width * value.height * 4) / 1024
    }
}

// 提取本地音频封面并压缩的工具方法 (供组件和预加载使用)
suspend fun loadLocalAudioCover(context: android.content.Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val embedPic = retriever.embeddedPicture
        if (embedPic != null) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(embedPic, 0, embedPic.size, options)
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false
            val decoded = BitmapFactory.decodeByteArray(embedPic, 0, embedPic.size, options)
            return@withContext decoded?.asImageBitmap()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }
    return@withContext null
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

@Composable
fun AlbumCover(
    song: LocalSong,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (song.isNone) {
        Box(modifier = modifier.background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Block, contentDescription = "无", tint = Color.Gray, modifier = Modifier.fillMaxSize(0.5f))
        }
        return
    }

    if (song.isText) {
        val colorHash = song.title.hashCode()
        val color1 = Color(0xFF80DEEA.toInt() + (colorHash % 0x002222))
        val color2 = Color(0xFFFFF59D.toInt() - (colorHash % 0x001111))
        Box(modifier = modifier.background(brush = Brush.linearGradient(listOf(color1, color2))), contentAlignment = Alignment.Center) {
            Text(text = song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp), maxLines = 1)
        }
        return
    }

    val context = LocalContext.current
    val isNetwork = song.albumArtUri.toString().startsWith("http")

    Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
        if (isNetwork) {
            // ================= 方案A：网络图片交由 Coil 处理 =================
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri.toString())
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // ================= 方案B：本地音乐走自定义缓存机制 =================
            val cacheKey = song.uri.toString()
            var bitmap by remember(song.id) { mutableStateOf(bitmapCache.get(cacheKey)) }

            LaunchedEffect(song.id) {
                if (bitmap == null) {
                    val loadedBitmap = loadLocalAudioCover(context, song.uri)
                    if (loadedBitmap != null) {
                        bitmapCache.put(cacheKey, loadedBitmap)
                        bitmap = loadedBitmap
                    }
                }
            }

            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap!!),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
            }
        }
    }
}
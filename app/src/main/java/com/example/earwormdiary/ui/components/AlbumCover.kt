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
import com.example.earwormdiary.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// 缓存
val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
val bitmapCache = object : LruCache<String, ImageBitmap>(cacheSize) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return (value.width * value.height * 4) / 1024
    }
}

@Composable
fun AlbumCover(
    song: LocalSong,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // 1. 处理“无”记录
    if (song.isNone) {
        Box(
            modifier = modifier.background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Block, // 禁止图标
                contentDescription = "无",
                tint = Color.Gray,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
        return
    }

    // 2. 处理“纯文字”记录
    if (song.isText) {
        // 生成一个基于文字哈希的伪随机渐变色，让不同的文字有不同的背景
        val colorHash = song.title.hashCode()
        val color1 = Color(0xFF80DEEA.toInt() + (colorHash % 0x002222)) // 随机微调颜色
        val color2 = Color(0xFFFFF59D.toInt() - (colorHash % 0x001111))

        Box(
            modifier = modifier.background(
                brush = Brush.linearGradient(listOf(color1, color2))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
                maxLines = 1 // 防止文字太长溢出
            )
        }
        return
    }

    // 3. 处理普通歌曲（本地 & 网络）
    val context = LocalContext.current
    val uri = song.uri
    val artUri = song.albumArtUri

    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.id) { // key 使用 ID，因为 artUri 可能相同
        val cacheKey = if (artUri != Uri.EMPTY) artUri.toString() else uri.toString()
        val cached = bitmapCache.get(cacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val artUriString = artUri.toString()
                // A. 处理网络图片 (http/https)
                if (artUriString.startsWith("http")) {
                    val url = URL(artUriString)
                    val stream = url.openStream()
                    val downloadedBitmap = BitmapFactory.decodeStream(stream)
                    val imageBitmap = downloadedBitmap?.asImageBitmap()

                    if (imageBitmap != null) {
                        bitmapCache.put(cacheKey, imageBitmap)
                        bitmap = imageBitmap
                    }
                    stream.close()
                }
                // B. 处理本地文件
                else {
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
                            val imageBitmap = decoded?.asImageBitmap()

                            if (imageBitmap != null) {
                                bitmapCache.put(cacheKey, imageBitmap)
                                bitmap = imageBitmap
                            }
                        }
                    } catch (e: Exception) {
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = modifier.background(Color.LightGray), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap!!),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

// 压缩算法
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
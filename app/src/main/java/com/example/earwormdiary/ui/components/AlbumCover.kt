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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.earwormdiary.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
val cacheSize = maxMemory / 8
val bitmapCache = object : LruCache<String, ImageBitmap>(cacheSize) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return (value.width * value.height * 4) / 1024
    }
}

suspend fun loadLocalAudioCover(
    context: android.content.Context,
    uri: Uri
): ImageBitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val embeddedPicture = retriever.embeddedPicture
        if (embeddedPicture != null) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size, options)
            options.inSampleSize = calculateInSampleSize(options, 200, 200)
            options.inJustDecodeBounds = false
            return@withContext BitmapFactory
                .decodeByteArray(embeddedPicture, 0, embeddedPicture.size, options)
                ?.asImageBitmap()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
    null
}

fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height, width) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
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
    contentDescription: String? = null,
    cropAlignment: Alignment = Alignment.Center,
    compactTextCover: Boolean = false
) {
    if (song.isNone) {
        Box(
            modifier = modifier.background(Color(0xFFE0E0E0)),
            contentAlignment = cropAlignment
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = "无",
                tint = Color.Gray,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
        return
    }

    if (song.isText) {
        val colorHash = song.title.hashCode()
        val color1 = Color(0xFF80DEEA.toInt() + (colorHash % 0x002222))
        val color2 = Color(0xFFFFF59D.toInt() - (colorHash % 0x001111))
        val displayText = if (compactTextCover) {
            firstDisplayCharacter(song.title)
        } else {
            song.title
        }
        Box(
            modifier = modifier.background(Brush.linearGradient(listOf(color1, color2))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                style = if (compactTextCover) {
                    MaterialTheme.typography.titleMedium.copy(lineHeight = 16.sp)
                } else {
                    MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(if (compactTextCover) 2.dp else 10.dp),
                maxLines = if (compactTextCover) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val context = LocalContext.current
    val isNetwork = song.albumArtUri.toString().startsWith("http")

    Box(
        modifier = modifier.background(Color.LightGray),
        contentAlignment = cropAlignment
    ) {
        if (isNetwork) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(song.albumArtUri.toString())
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = cropAlignment
            )
        } else {
            val cacheKey = song.uri.toString()
            var bitmap by remember(song.id) { mutableStateOf(bitmapCache.get(cacheKey)) }

            LaunchedEffect(song.id) {
                if (bitmap == null) {
                    loadLocalAudioCover(context, song.uri)?.let {
                        bitmapCache.put(cacheKey, it)
                        bitmap = it
                    }
                }
            }

            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap!!),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = cropAlignment
                )
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private fun firstDisplayCharacter(text: String): String {
    return text.firstOrNull { !it.isWhitespace() }?.toString() ?: "?"
}

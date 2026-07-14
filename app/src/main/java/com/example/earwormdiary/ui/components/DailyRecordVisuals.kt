package com.example.earwormdiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.RecordEntry
import com.example.earwormdiary.ui.screens.getCategoryColor
import kotlin.math.abs

@Composable
fun DailyRecordCover(
    record: DailyRecord,
    modifier: Modifier = Modifier,
    compactTextCovers: Boolean = false
) {
    Box(modifier = modifier) {
        when (record.songCount) {
            1 -> AlbumCover(
                song = record.primaryEntry.song,
                modifier = Modifier.fillMaxSize(),
                compactTextCover = compactTextCovers
            )

            2, 3 -> {
                record.entries.forEachIndexed { index, entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(SongSegmentShape(record.songCount, index))
                    ) {
                        AlbumCover(
                            song = entry.song,
                            modifier = Modifier.fillMaxSize(),
                            cropAlignment = segmentCropAlignment(record.songCount, index),
                            compactTextCover = compactTextCovers
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedCategoryStrip(
    entries: List<RecordEntry>,
    categories: List<Category>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "+ 分类",
    compact: Boolean = false,
    slanted: Boolean = false,
    onSegmentClick: ((Int) -> Unit)? = null
) {
    if (slanted && entries.size > 1) {
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceVariantColor)
                .drawBehind {
                    entries.forEachIndexed { index, entry ->
                        val category = categories.find { it.id == entry.categoryId }
                        val showCategory = !entry.song.isNone
                        val backgroundColor = category?.let { getCategoryColor(it.id).copy(alpha = 0.92f) }
                            ?: if (showCategory) surfaceVariantColor else Color.Transparent

                        if (backgroundColor.alpha > 0f) {
                            drawPath(
                                path = buildGlobalSlashSegmentPath(size, entries.size, index),
                                color = backgroundColor
                            )
                        }
                    }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                entries.forEachIndexed { index, entry ->
                    val category = categories.find { it.id == entry.categoryId }
                    val showCategory = !entry.song.isNone
                    val text = when {
                        !showCategory -> ""
                        category != null -> category.name
                        else -> emptyLabel
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .then(
                                if (onSegmentClick != null && showCategory) {
                                    Modifier.clickable { onSegmentClick(index) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(
                                horizontal = if (compact) 4.dp else 8.dp,
                                vertical = if (compact) 4.dp else 10.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.fillMaxWidth(),
                            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        return
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val category = categories.find { it.id == entry.categoryId }
            val showCategory = !entry.song.isNone
            val backgroundColor = category?.let { getCategoryColor(it.id).copy(alpha = 0.22f) }
                ?: if (showCategory) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            val text = when {
                !showCategory -> ""
                category != null -> category.name
                else -> emptyLabel
            }

            val segmentModifier = Modifier
                .weight(1f)
                .clip(
                    if (slanted && entries.size > 1) {
                        SlashCategorySegmentShape(entries.size, index)
                    } else {
                        RoundedCornerShape(0.dp)
                    }
                )
                .background(backgroundColor)
                .padding(
                    horizontal = if (compact) 4.dp else 8.dp,
                    vertical = if (compact) 4.dp else 10.dp
                )

            Box(
                modifier = if (onSegmentClick != null && showCategory) {
                    segmentModifier.clickable { onSegmentClick(index) }
                } else {
                    segmentModifier
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun segmentCropAlignment(songCount: Int, index: Int): BiasAlignment {
    val center = segmentNormalizedCenter(songCount, index)
    return BiasAlignment(
        horizontalBias = center.x * 2f - 1f,
        verticalBias = center.y * 2f - 1f
    )
}

private fun segmentNormalizedCenter(songCount: Int, index: Int): Offset {
    return when (songCount) {
        2 -> {
            if (index == 0) Offset(1f / 3f, 1f / 3f) else Offset(2f / 3f, 2f / 3f)
        }

        3 -> polygonCentroid(segmentPolygon(songCount, index))
        else -> Offset(0.5f, 0.5f)
    }
}

private fun segmentPolygon(songCount: Int, index: Int): List<Offset> {
    if (songCount == 2) {
        return if (index == 0) {
            listOf(
                Offset(0f, 0f),
                Offset(1f, 0f),
                Offset(0f, 1f)
            )
        } else {
            listOf(
                Offset(1f, 0f),
                Offset(1f, 1f),
                Offset(0f, 1f)
            )
        }
    }

    val sectorAngles = listOf(
        -90f to 30f,
        30f to 150f,
        150f to 270f
    )
    val (startAngle, endAngle) = sectorAngles[index]
    val center = Offset(0.5f, 0.5f)
    val startPoint = rayToUnitSquare(startAngle)
    val endPoint = rayToUnitSquare(endAngle)

    val polygon = mutableListOf(center, startPoint)
    polygon += cornersBetweenClockwise(startPoint, endPoint)
    polygon += endPoint
    return polygon
}

private fun cornersBetweenClockwise(start: Offset, end: Offset): List<Offset> {
    val corners = listOf(
        Offset(1f, 0f),
        Offset(1f, 1f),
        Offset(0f, 1f),
        Offset(0f, 0f)
    )
    val result = mutableListOf<Offset>()
    val position = perimeterProgress(start)
    val target = perimeterProgress(end)

    corners.forEach { corner ->
        val cornerProgress = perimeterProgress(corner)
        val normalizedCorner = if (cornerProgress < position) cornerProgress + 4f else cornerProgress
        val normalizedTarget = if (target < position) target + 4f else target
        if (normalizedCorner in position..normalizedTarget) {
            result += corner
        }
    }
    return result
}

private fun perimeterProgress(point: Offset): Float {
    return when {
        approximately(point.y, 0f) -> point.x
        approximately(point.x, 1f) -> 1f + point.y
        approximately(point.y, 1f) -> 2f + (1f - point.x)
        else -> 3f + (1f - point.y)
    }
}

private fun polygonCentroid(points: List<Offset>): Offset {
    var areaTimesTwo = 0f
    var centroidX = 0f
    var centroidY = 0f

    points.indices.forEach { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        val cross = current.x * next.y - next.x * current.y
        areaTimesTwo += cross
        centroidX += (current.x + next.x) * cross
        centroidY += (current.y + next.y) * cross
    }

    if (abs(areaTimesTwo) < 0.0001f) return Offset(0.5f, 0.5f)
    return Offset(
        x = centroidX / (3f * areaTimesTwo),
        y = centroidY / (3f * areaTimesTwo)
    )
}

private fun rayToUnitSquare(angleDegrees: Float): Offset {
    val center = Offset(0.5f, 0.5f)
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = kotlin.math.cos(radians).toFloat()
    val dy = kotlin.math.sin(radians).toFloat()

    val tx = when {
        dx > 0f -> (1f - center.x) / dx
        dx < 0f -> -center.x / dx
        else -> Float.POSITIVE_INFINITY
    }
    val ty = when {
        dy > 0f -> (1f - center.y) / dy
        dy < 0f -> -center.y / dy
        else -> Float.POSITIVE_INFINITY
    }
    val t = minOf(tx, ty)
    return Offset(center.x + dx * t, center.y + dy * t)
}

private fun approximately(a: Float, b: Float): Boolean = abs(a - b) < 0.001f

private fun buildGlobalSlashSegmentPath(size: Size, segmentCount: Int, index: Int): Path {
    val segmentWidth = size.width / segmentCount
    val slant = minOf(segmentWidth * 0.24f, size.height * 0.3f)
    val leftTop = if (index == 0) {
        0f
    } else {
        index * segmentWidth + slant / 2f
    }
    val leftBottom = if (index == 0) {
        0f
    } else {
        index * segmentWidth - slant / 2f
    }
    val rightTop = if (index == segmentCount - 1) {
        size.width
    } else {
        (index + 1) * segmentWidth + slant / 2f
    }
    val rightBottom = if (index == segmentCount - 1) {
        size.width
    } else {
        (index + 1) * segmentWidth - slant / 2f
    }

    return Path().apply {
        moveTo(leftTop.coerceIn(0f, size.width), 0f)
        lineTo(rightTop.coerceIn(0f, size.width), 0f)
        lineTo(rightBottom.coerceIn(0f, size.width), size.height)
        lineTo(leftBottom.coerceIn(0f, size.width), size.height)
        close()
    }
}

private class SlashCategorySegmentShape(
    private val segmentCount: Int,
    private val index: Int
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(buildGlobalSlashSegmentPath(size, segmentCount, index))
    }
}

private class SongSegmentShape(
    private val songCount: Int,
    private val index: Int
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val normalizedPolygon = segmentPolygon(songCount, index)
        val path = Path()
        normalizedPolygon.forEachIndexed { pointIndex, point ->
            val mappedPoint = Offset(point.x * size.width, point.y * size.height)
            if (pointIndex == 0) {
                path.moveTo(mappedPoint.x, mappedPoint.y)
            } else {
                path.lineTo(mappedPoint.x, mappedPoint.y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

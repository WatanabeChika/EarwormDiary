package com.example.earwormdiary.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.LocalSong
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

data class CalendarImageExportResult(
    val relativePath: String,
    val monthCount: Int
)

object CalendarImageExporter {
    private const val RELATIVE_DIRECTORY = "Pictures/EarwormDiary"
    private val backgroundColor = "#F5F2EC".toColorInt()
    private val cardColor = Color.WHITE
    private val cardBorderColor = "#E7EAF0".toColorInt()
    private val cellEmptyColor = "#FBFCFE".toColorInt()
    private val cellTextColor = "#1F2328".toColorInt()
    private val secondaryTextColor = "#5F6874".toColorInt()
    private val placeholderColor = "#DDE3EA".toColorInt()

    suspend fun exportMonths(
        context: Context,
        records: Map<LocalDate, DailyRecord>,
        startMonth: YearMonth,
        endMonth: YearMonth
    ): CalendarImageExportResult = withContext(Dispatchers.IO) {
        require(!endMonth.isBefore(startMonth)) { "结束月份不能早于开始月份。" }

        val months = buildMonthRange(startMonth, endMonth)
        require(months.size <= 12) { "最多只能导出 12 个月。" }

        val monthRecords = records.filterKeys { date ->
            val month = YearMonth.from(date)
            !month.isBefore(startMonth) && !month.isAfter(endMonth)
        }
        val artworkMap = loadArtworkBitmaps(context, monthRecords.values)
        val monthWidthPx = if (months.size == 1) 1880 else 1440
        val monthBitmaps = months.map { month ->
            renderMonthBitmap(
                month = month,
                records = monthRecords,
                artworkMap = artworkMap,
                widthPx = monthWidthPx
            )
        }

        val finalBitmap = stitchBitmaps(monthBitmaps)

        val fileName = buildFileName(startMonth, endMonth)
        val savedPath = saveBitmap(context, finalBitmap, fileName)
        CalendarImageExportResult(
            relativePath = savedPath,
            monthCount = months.size
        )
    }

    private fun buildMonthRange(startMonth: YearMonth, endMonth: YearMonth): List<YearMonth> {
        val months = mutableListOf<YearMonth>()
        var current = startMonth
        while (!current.isAfter(endMonth)) {
            months += current
            current = current.plusMonths(1)
        }
        return months
    }

    private fun buildFileName(startMonth: YearMonth, endMonth: YearMonth): String {
        return if (startMonth == endMonth) {
            "EarwormDiary_Calendar_$startMonth.png"
        } else {
            "EarwormDiary_Calendar_${startMonth}_to_${endMonth}.png"
        }
    }

    private fun renderMonthBitmap(
        month: YearMonth,
        records: Map<LocalDate, DailyRecord>,
        artworkMap: Map<String, Bitmap>,
        widthPx: Int
    ): Bitmap {
        val rows = buildCalendarRows(month)
        val outerPadding = 64f
        val cardPadding = 52f
        val cardRadius = 30f
        val titleHeight = 164f
        val weekHeaderHeight = 34f
        val rowGap = 18f
        val cellGap = 18f

        val contentWidth = widthPx - outerPadding * 2 - cardPadding * 2
        val cellSize = (contentWidth - cellGap * 6) / 7f
        val rowsHeight = rows.size * cellSize + (rows.size - 1) * rowGap
        val cardHeight = cardPadding * 2 + titleHeight + weekHeaderHeight + 34f + rowsHeight
        val heightPx = ceil(cardHeight + outerPadding * 2).toInt()

        val bitmap = createBitmap(widthPx, heightPx)
        val canvas = Canvas(bitmap)

        val cardRect = RectF(
            outerPadding,
            outerPadding,
            widthPx - outerPadding,
            outerPadding + cardHeight
        )
        drawSoftRoundShadow(canvas, cardRect, cardRadius)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint)
        val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardStrokePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cellTextColor
            textSize = 70f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.01f
        }
        val weekPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryTextColor
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.06f
        }
        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cellTextColor
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val symbolPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val titleCenterX = cardRect.centerX()
        val titleBaseY = cardRect.top + cardPadding + 66f
        val monthLabel = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"
        canvas.drawText(monthLabel, titleCenterX, titleBaseY, titlePaint)

        val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val headerTop = cardRect.top + cardPadding + titleHeight
        val gridLeft = cardRect.left + cardPadding
        val gridTop = headerTop + weekHeaderHeight + 34f

        weekdays.forEachIndexed { index, day ->
            val centerX = gridLeft + index * (cellSize + cellGap) + cellSize / 2f
            canvas.drawText(day, centerX, headerTop + 24f, weekPaint)
        }

        rows.forEachIndexed { rowIndex, row ->
            val top = gridTop + rowIndex * (cellSize + rowGap)
            row.forEachIndexed { columnIndex, date ->
                if (date == null) return@forEachIndexed

                val left = gridLeft + columnIndex * (cellSize + cellGap)
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                drawDayCell(
                    canvas = canvas,
                    rect = rect,
                    day = date.dayOfMonth,
                    record = records[date],
                    artworkMap = artworkMap,
                    dayPaint = dayPaint,
                    symbolPaint = symbolPaint
                )
            }
        }

        return bitmap
    }

    private fun drawDayCell(
        canvas: Canvas,
        rect: RectF,
        day: Int,
        record: DailyRecord?,
        artworkMap: Map<String, Bitmap>,
        dayPaint: TextPaint,
        symbolPaint: TextPaint
    ) {
        val cellRadius = 20f
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cellEmptyColor }
        canvas.drawRoundRect(rect, cellRadius, cellRadius, cellPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, cellRadius, cellRadius, strokePaint)

        if (record == null) {
            val baseline = rect.centerY() - (dayPaint.descent() + dayPaint.ascent()) / 2f
            canvas.drawText(day.toString(), rect.centerX(), baseline, dayPaint)
            return
        }

        val clipPath = Path().apply { addRoundRect(rect, cellRadius, cellRadius, Path.Direction.CW) }
        canvas.withClip(clipPath) {
            when (record.songCount) {
                1 -> drawSongCover(
                    canvas = this,
                    song = record.primaryEntry.song,
                    bitmap = artworkMap[artworkKey(record.primaryEntry.song)],
                    target = rect,
                    centerXNorm = 0.5f,
                    centerYNorm = 0.5f,
                    symbolPaint = symbolPaint
                )

                2, 3 -> {
                    record.entries.forEachIndexed { index, entry ->
                        val path = buildSegmentPath(rect, record.songCount, index)
                        withClip(path) {
                            val center = segmentNormalizedCenter(record.songCount, index)
                            drawSongCover(
                                canvas = this,
                                song = entry.song,
                                bitmap = artworkMap[artworkKey(entry.song)],
                                target = rect,
                                centerXNorm = center.x,
                                centerYNorm = center.y,
                                symbolPaint = symbolPaint
                            )
                        }
                    }
                }
            }
        }
    }

    private fun drawSongCover(
        canvas: Canvas,
        song: LocalSong,
        bitmap: Bitmap?,
        target: RectF,
        centerXNorm: Float,
        centerYNorm: Float,
        symbolPaint: TextPaint
    ) {
        when {
            song.isNone -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = placeholderColor }
                canvas.drawRect(target, paint)
                val baseline = target.centerY() - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
                canvas.drawText("×", target.centerX(), baseline, symbolPaint.apply { color = secondaryTextColor })
            }
            song.isText -> {
                val colorHash = song.title.hashCode()
                val color1 = 0xFF80DEEA.toInt() + (colorHash % 0x002222)
                val color2 = 0xFFFFF59D.toInt() - (colorHash % 0x001111)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        target.left,
                        target.top,
                        target.right,
                        target.bottom,
                        color1,
                        color2,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(target, paint)
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = "#3C4043".toColorInt()
                    textSize = target.height() * 0.3f
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }
                val baseline = target.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(firstDisplayCharacter(song.title), target.centerX(), baseline, textPaint)
            }
            bitmap != null -> drawBitmapCover(canvas, bitmap, target, centerXNorm, centerYNorm)
            else -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = "#B8C0CC".toColorInt() }
            canvas.drawRect(target, paint)
            val baseline = target.centerY() - (symbolPaint.descent() + symbolPaint.ascent()) / 2f
            canvas.drawText("♪", target.centerX(), baseline, symbolPaint)
            }
        }
    }

    private fun drawBitmapCover(
        canvas: Canvas,
        bitmap: Bitmap,
        target: RectF,
        centerXNorm: Float,
        centerYNorm: Float
    ) {
        val src = computeCenterCropSrcRect(bitmap.width, bitmap.height, target, centerXNorm, centerYNorm)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, src, target, paint)
    }

    private fun computeCenterCropSrcRect(
        bitmapWidth: Int,
        bitmapHeight: Int,
        target: RectF,
        centerXNorm: Float,
        centerYNorm: Float
    ): Rect {
        val targetRatio = target.width() / target.height()
        val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        if (bitmapRatio > targetRatio) {
            cropHeight = bitmapHeight
            cropWidth = (bitmapHeight * targetRatio).toInt()
        } else {
            cropWidth = bitmapWidth
            cropHeight = (bitmapWidth / targetRatio).toInt()
        }

        val maxLeft = (bitmapWidth - cropWidth).coerceAtLeast(0)
        val maxTop = (bitmapHeight - cropHeight).coerceAtLeast(0)
        val left = (centerXNorm * bitmapWidth - cropWidth / 2f).toInt().coerceIn(0, maxLeft)
        val top = (centerYNorm * bitmapHeight - cropHeight / 2f).toInt().coerceIn(0, maxTop)
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    private fun stitchBitmaps(bitmaps: List<Bitmap>): Bitmap {
        require(bitmaps.isNotEmpty()) { "No month bitmaps to stitch." }
        val columns = when {
            bitmaps.size <= 3 -> 1
            bitmaps.size == 4 -> 2
            else -> 3
        }
        val rows = ceil(bitmaps.size / columns.toDouble()).toInt()
        val spacing = 32
        val outerMargin = 92
        val columnWidth = bitmaps.maxOf { it.width }
        val rowHeights = (0 until rows).map { row ->
            bitmaps.drop(row * columns).take(columns).maxOf { it.height }
        }

        val width = columnWidth * columns + spacing * (columns - 1) + outerMargin * 2
        val height = rowHeights.sum() + spacing * (rows - 1) + outerMargin * 2
        val merged = createBitmap(width, height)
        val canvas = Canvas(merged)
        drawMagazineBackground(canvas, width.toFloat(), height.toFloat())

        var top = outerMargin
        repeat(rows) { row ->
            var left = outerMargin
            bitmaps.drop(row * columns).take(columns).forEach { bitmap ->
                canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)
                left += columnWidth + spacing
            }
            top += rowHeights[row] + spacing
        }

        return merged
    }

    private suspend fun loadArtworkBitmaps(
        context: Context,
        records: Collection<DailyRecord>
    ): Map<String, Bitmap> = withContext(Dispatchers.IO) {
        val songs = records
            .flatMap { it.entries }
            .map { it.song }
            .filterNot { it.isNone || it.isText }
            .distinctBy { artworkKey(it) }

        val pairs = songs.map { song ->
            async {
                val bitmap = if (song.albumArtUri.toString().startsWith("http")) {
                    loadRemoteBitmap(context, song.albumArtUri.toString())
                } else {
                    loadLocalBitmap(context, song.uri)
                }
                artworkKey(song) to bitmap
            }
        }.awaitAll()

        val result = mutableMapOf<String, Bitmap>()
        pairs.forEach { (key, bitmap) ->
            if (bitmap != null) {
                result[key] = bitmap
            }
        }
        result
    }

    private suspend fun loadRemoteBitmap(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .size(1200)
            .build()
        val result = context.imageLoader.execute(request)
        return (result as? SuccessResult)?.drawable?.let { drawable ->
            val source = createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1)
            )
            val canvas = Canvas(source)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            source
        }
    }

    private fun loadLocalBitmap(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val picture = retriever.embeddedPicture ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
            options.inSampleSize = calculateInSampleSize(options, 1200, 1200)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIRECTORY)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("无法创建导出文件。")

            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: error("无法写入导出文件。")

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            "$RELATIVE_DIRECTORY/$fileName"
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val exportDir = File(picturesDir, "EarwormDiary").apply { mkdirs() }
            val file = File(exportDir, fileName)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
            file.absolutePath
        }
    }

    private fun buildCalendarRows(month: YearMonth): List<List<LocalDate?>> {
        val daysInMonth = month.lengthOfMonth()
        val emptySlots = month.atDay(1).dayOfWeek.value - 1
        val totalSlots = emptySlots + daysInMonth
        val cells = MutableList<LocalDate?>(totalSlots) { index ->
            val day = index - emptySlots + 1
            if (day in 1..daysInMonth) month.atDay(day) else null
        }
        while (cells.size < 42) {
            cells += null
        }
        return cells.chunked(7)
    }

    private fun drawMagazineBackground(canvas: Canvas, width: Float, height: Float) {
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width,
                height,
                intArrayOf(
                    "#F7F2EA".toColorInt(),
                    "#F4F6FA".toColorInt(),
                    "#EEF2F7".toColorInt()
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, basePaint)

        val glowPaintTop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.18f,
                height * 0.12f,
                width * 0.42f,
                Color.argb(34, 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.18f, height * 0.12f, width * 0.42f, glowPaintTop)

        val glowPaintBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.84f,
                height * 0.88f,
                width * 0.36f,
                Color.argb(24, 180, 193, 214),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.84f, height * 0.88f, width * 0.36f, glowPaintBottom)
    }

    private fun drawSoftRoundShadow(canvas: Canvas, rect: RectF, radius: Float) {
        val shadowLayers = listOf(
            Triple(12f, 6, 16),
            Triple(24f, 10, 10),
            Triple(38f, 14, 6)
        )
        shadowLayers.forEach { (expand, offsetY, alpha) ->
            val shadowRect = RectF(
                rect.left - expand,
                rect.top - expand * 0.55f + offsetY,
                rect.right + expand,
                rect.bottom + expand + offsetY
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 20, 28, 38)
            }
            canvas.drawRoundRect(shadowRect, radius + expand, radius + expand, paint)
        }
    }

    private fun buildSegmentPath(rect: RectF, songCount: Int, index: Int): Path {
        val normalizedPolygon = segmentPolygon(songCount, index)
        return Path().apply {
            normalizedPolygon.forEachIndexed { pointIndex, point ->
                val mappedX = rect.left + point.x * rect.width()
                val mappedY = rect.top + point.y * rect.height()
                if (pointIndex == 0) {
                    moveTo(mappedX, mappedY)
                } else {
                    lineTo(mappedX, mappedY)
                }
            }
            close()
        }
    }

    private fun artworkKey(song: LocalSong): String {
        return if (song.albumArtUri.toString().startsWith("http")) {
            song.albumArtUri.toString()
        } else {
            song.uri.toString()
        }
    }

    private fun segmentNormalizedCenter(songCount: Int, index: Int): Point {
        return when (songCount) {
            2 -> {
                if (index == 0) Point(1f / 3f, 1f / 3f) else Point(2f / 3f, 2f / 3f)
            }
            3 -> polygonCentroid(segmentPolygon(songCount, index))
            else -> Point(0.5f, 0.5f)
        }
    }

    private fun segmentPolygon(songCount: Int, index: Int): List<Point> {
        if (songCount == 2) {
            return if (index == 0) {
                listOf(
                    Point(0f, 0f),
                    Point(1f, 0f),
                    Point(0f, 1f)
                )
            } else {
                listOf(
                    Point(1f, 0f),
                    Point(1f, 1f),
                    Point(0f, 1f)
                )
            }
        }

        val sectorAngles = listOf(
            -90f to 30f,
            30f to 150f,
            150f to 270f
        )
        val (startAngle, endAngle) = sectorAngles[index]
        val center = Point(0.5f, 0.5f)
        val startPoint = rayToUnitSquare(startAngle)
        val endPoint = rayToUnitSquare(endAngle)

        val polygon = mutableListOf(center, startPoint)
        polygon += cornersBetweenClockwise(startPoint, endPoint)
        polygon += endPoint
        return polygon
    }

    private fun cornersBetweenClockwise(start: Point, end: Point): List<Point> {
        val corners = listOf(
            Point(1f, 0f),
            Point(1f, 1f),
            Point(0f, 1f),
            Point(0f, 0f)
        )
        val result = mutableListOf<Point>()
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

    private fun perimeterProgress(point: Point): Float {
        return when {
            approximately(point.y, 0f) -> point.x
            approximately(point.x, 1f) -> 1f + point.y
            approximately(point.y, 1f) -> 2f + (1f - point.x)
            else -> 3f + (1f - point.y)
        }
    }

    private fun polygonCentroid(points: List<Point>): Point {
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

        if (abs(areaTimesTwo) < 0.0001f) return Point(0.5f, 0.5f)
        return Point(
            x = centroidX / (3f * areaTimesTwo),
            y = centroidY / (3f * areaTimesTwo)
        )
    }

    private fun rayToUnitSquare(angleDegrees: Float): Point {
        val center = Point(0.5f, 0.5f)
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
        return Point(center.x + dx * t, center.y + dy * t)
    }

    private fun approximately(a: Float, b: Float): Boolean = abs(a - b) < 0.001f

    private fun firstDisplayCharacter(text: String): String {
        return text.firstOrNull { !it.isWhitespace() }?.toString() ?: "?"
    }

    private fun calculateInSampleSize(
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

    private data class Point(val x: Float, val y: Float)
}

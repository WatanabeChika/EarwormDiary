package com.example.earwormdiary.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.ui.components.CategorySelectionDialog
import com.example.earwormdiary.ui.components.DailyRecordCover
import com.example.earwormdiary.ui.components.bitmapCache
import com.example.earwormdiary.ui.components.loadLocalAudioCover
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class CalendarSongActionType {
    REPLACE,
    DELETE
}

@Composable
fun CalendarScreen(
    records: Map<LocalDate, DailyRecord>,
    categories: List<Category>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onAddSong: (LocalDate) -> Unit,
    onReplaceSong: (LocalDate, Int) -> Unit,
    onReplaceAllSongs: (LocalDate) -> Unit,
    onDeleteSong: (LocalDate, Int) -> Unit,
    onRemoveRecord: (LocalDate) -> Unit,
    onCopyRecord: (LocalDate, LocalDate) -> Unit,
    onUpdateRecord: (DailyRecord) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(currentMonth, records) {
        withContext(Dispatchers.IO) {
            val startMonth = currentMonth.minusMonths(1)
            val endMonth = currentMonth.plusMonths(1)

            val recordsToPreload = records.filterKeys { date ->
                val recordMonth = YearMonth.from(date)
                !recordMonth.isBefore(startMonth) && !recordMonth.isAfter(endMonth)
            }

            recordsToPreload.values.forEach { record ->
                record.entries.forEach { entry ->
                    val song = entry.song
                    if (song.isNone || song.isText) return@forEach

                    if (song.albumArtUri.toString().startsWith("http")) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(song.albumArtUri.toString())
                                .build()
                        )
                    } else {
                        val cacheKey = song.uri.toString()
                        if (bitmapCache.get(cacheKey) == null) {
                            loadLocalAudioCover(context, song.uri)?.let { bitmapCache.put(cacheKey, it) }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        YearMonthPickerDialog(
            initialYearMonth = currentMonth,
            onDismissRequest = { showDatePicker = false },
            onYearMonthSelected = {
                currentMonth = it
                showDatePicker = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                MonthHeader(
                    currentMonth = currentMonth,
                    onMonthChange = { currentMonth = it },
                    onTitleClick = { showDatePicker = true }
                )
                DaysOfWeekHeader()
                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = currentMonth,
                    transitionSpec = {
                        if (targetState.isAfter(initialState)) {
                            (slideInHorizontally { it } + fadeIn()).togetherWith(
                                slideOutHorizontally { -it } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                slideOutHorizontally { it } + fadeOut()
                            )
                        }
                    },
                    label = "calendar_month_animation"
                ) { targetMonth ->
                    ManualCalendarGrid(
                        currentMonth = targetMonth,
                        records = records,
                        selectedDate = selectedDate,
                        onDateSelected = onDateSelected,
                        onCopyRecord = onCopyRecord,
                        onMonthSwipe = { direction ->
                            currentMonth = currentMonth.plusMonths(direction.toLong())
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                color = Color.LightGray
            )
            Text(text = "当日回响", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            HorizontalDivider(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                color = Color.LightGray
            )
        }

        DetailArea(
            date = selectedDate,
            record = records[selectedDate],
            categories = categories,
            onAddClick = { onAddSong(selectedDate) },
            onReplaceSong = { index -> onReplaceSong(selectedDate, index) },
            onReplaceAllSongs = { onReplaceAllSongs(selectedDate) },
            onDeleteSong = { index -> onDeleteSong(selectedDate, index) },
            onDeleteAllSongs = { onRemoveRecord(selectedDate) },
            onUpdateCategory = { index, categoryId ->
                records[selectedDate]?.let { onUpdateRecord(it.updateCategory(index, categoryId)) }
            }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun MonthHeader(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onTitleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(20.dp))
        }

        Surface(
            onClick = onTitleClick,
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentMonth.year}年 ${currentMonth.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun YearMonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismissRequest: () -> Unit,
    onYearMonthSelected: (YearMonth) -> Unit
) {
    var isSelectingYear by remember { mutableStateOf(true) }
    var selectedYear by remember { mutableIntStateOf(initialYearMonth.year) }
    var selectedMonth by remember { mutableIntStateOf(initialYearMonth.monthValue) }

    val currentYear = remember { java.time.Year.now().value }
    val years = remember { (currentYear - 50..currentYear + 50).toList() }
    val listState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (years.indexOf(selectedYear) - 6).coerceAtLeast(0)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onYearMonthSelected(YearMonth.now()) }) {
                    Text("跳到今天")
                }
                TextButton(onClick = onDismissRequest) {
                    Text("取消")
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { isSelectingYear = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelectingYear) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) {
                    Text(
                        text = "${selectedYear}年",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isSelectingYear) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Text("/", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)

                TextButton(
                    onClick = { isSelectingYear = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (!isSelectingYear) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) {
                    Text(
                        text = "${selectedMonth}月",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (!isSelectingYear) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(300.dp)
            ) {
                if (isSelectingYear) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = listState,
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(years) { year ->
                            val isSelected = year == selectedYear
                            OutlinedButton(
                                onClick = {
                                    selectedYear = year
                                    isSelectingYear = false
                                },
                                colors = if (isSelected) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                },
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Text(
                                    text = "$year",
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items((1..12).toList()) { month ->
                            val isSelected = month == selectedMonth
                            OutlinedButton(
                                onClick = {
                                    selectedMonth = month
                                    onYearMonthSelected(YearMonth.of(selectedYear, month))
                                },
                                colors = if (isSelected) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text(
                                    text = "${month}月",
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun ManualCalendarGrid(
    currentMonth: YearMonth,
    records: Map<LocalDate, DailyRecord>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onCopyRecord: (LocalDate, LocalDate) -> Unit,
    onMonthSwipe: (Int) -> Unit
) {
    val rows = remember(currentMonth) {
        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value
        val emptySlots = firstDayOfWeek - 1
        val totalSlots = emptySlots + daysInMonth
        (0 until totalSlots).chunked(7)
    }
    val emptySlots = remember(currentMonth) { currentMonth.atDay(1).dayOfWeek.value - 1 }

    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartDay by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedRecord by remember { mutableStateOf<DailyRecord?>(null) }

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 50.dp.toPx() }
    var swipeTotalX by remember { mutableFloatStateOf(0f) }

    fun getDayFromOffset(offset: Offset): Int? {
        if (gridSize.width == 0 || gridSize.height == 0) return null
        val cellWidth = gridSize.width / 7f
        val totalRows = rows.size
        val cellHeight = gridSize.height / totalRows.toFloat()
        val col = (offset.x / cellWidth).toInt()
        val row = (offset.y / cellHeight).toInt()
        if (col in 0..6 && row in 0 until totalRows) {
            val index = row * 7 + col
            val day = index - emptySlots + 1
            if (day > 0 && day <= currentMonth.lengthOfMonth()) return day
        }
        return null
    }

    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { gridSize = it.size }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeTotalX > swipeThresholdPx) {
                                onMonthSwipe(-1)
                            } else if (swipeTotalX < -swipeThresholdPx) {
                                onMonthSwipe(1)
                            }
                            swipeTotalX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeTotalX += dragAmount
                        }
                    )
                }
                .pointerInput(currentMonth, records) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val day = getDayFromOffset(offset)
                            if (day != null) {
                                val date = currentMonth.atDay(day)
                                records[date]?.let {
                                    isDragging = true
                                    dragStartDay = day
                                    draggedRecord = it
                                    dragOffset = offset
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        },
                        onDragEnd = {
                            val targetDay = getDayFromOffset(dragOffset)
                            if (isDragging && dragStartDay != null && targetDay != null) {
                                val sourceDate = currentMonth.atDay(dragStartDay!!)
                                val targetDate = currentMonth.atDay(targetDay)
                                if (sourceDate != targetDate) {
                                    onCopyRecord(sourceDate, targetDate)
                                    onDateSelected(targetDate)
                                }
                            }
                            isDragging = false
                            dragStartDay = null
                            draggedRecord = null
                        },
                        onDragCancel = {
                            isDragging = false
                            dragStartDay = null
                            draggedRecord = null
                        }
                    )
                }
        ) {
            rows.forEach { rowIds ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowIds.forEach { index ->
                        val day = index - emptySlots + 1
                        if (day > 0) {
                            val date = currentMonth.atDay(day)
                            val isSource = isDragging && dragStartDay == day
                            Box(modifier = Modifier.weight(1f).alpha(if (isSource) 0.5f else 1f)) {
                                DayCellUpdated(
                                    day = day,
                                    record = records[date],
                                    isSelected = date == selectedDate,
                                    isToday = date == LocalDate.now(),
                                    onClick = { onDateSelected(date) }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    if (rowIds.size < 7) {
                        repeat(7 - rowIds.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (isDragging && draggedRecord != null) {
            val ghostSize = 80.dp
            val ghostSizePx = with(density) { ghostSize.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragOffset.x - ghostSizePx / 2).toInt(),
                            (dragOffset.y - ghostSizePx / 2).toInt()
                        )
                    }
                    .size(ghostSize)
                    .shadow(8.dp, MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .alpha(0.9f)
            ) {
                DailyRecordCover(record = draggedRecord!!, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun DayCellUpdated(
    day: Int,
    record: DailyRecord?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val selectionColor = Color(0xFFFF9800)

    val containerColor = when {
        isToday && record == null -> MaterialTheme.colorScheme.secondaryContainer
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = remember(isSelected, isToday) {
        when {
            isSelected && isToday -> {
                BorderStroke(
                    3.dp,
                    Brush.linearGradient(
                        colors = listOf(primaryColor, selectionColor),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    )
                )
            }
            isSelected -> BorderStroke(2.dp, selectionColor)
            isToday -> BorderStroke(2.dp, primaryColor.copy(alpha = 0.7f))
            else -> null
        }
    }

    Card(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = borderStroke,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (record != null) {
                DailyRecordCover(
                    record = record,
                    modifier = Modifier.fillMaxSize(),
                    compactTextCovers = true
                )
            } else {
                Text(
                    text = "$day",
                    modifier = Modifier.align(Alignment.Center),
                    style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer else Color.Black
                )
            }

            if (isSelected && record == null) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DetailArea(
    date: LocalDate,
    record: DailyRecord?,
    categories: List<Category>,
    onAddClick: () -> Unit,
    onReplaceSong: (Int) -> Unit,
    onReplaceAllSongs: () -> Unit,
    onDeleteSong: (Int) -> Unit,
    onDeleteAllSongs: () -> Unit,
    onUpdateCategory: (Int, String?) -> Unit
) {
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryIndex by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<CalendarSongActionType?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (record != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                record.entries.forEachIndexed { index, entry ->
                    val currentCategory = categories.find { it.id == entry.categoryId }
                    val categoryColor = currentCategory?.let { getCategoryColor(it.id).copy(alpha = 0.2f) }
                        ?: MaterialTheme.colorScheme.surfaceVariant
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val coverSize = maxWidth * 0.43f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                            Card(
                                modifier = Modifier
                                    .size(coverSize),
                                elevation = CardDefaults.cardElevation(4.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                DailyRecordCover(
                                    record = DailyRecord(date = record.date, entries = listOf(entry)),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                SelectionContainer {
                                    Column {
                                        Text(
                                            text = entry.song.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.Black,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "歌手: ${entry.song.artist}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.DarkGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "日期: $date",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                if (!entry.song.isNone) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = categoryColor,
                                        shape = RoundedCornerShape(50),
                                        onClick = {
                                            editingCategoryIndex = index
                                            showCategoryDialog = true
                                        }
                                    ) {
                                        Text(
                                            text = currentCategory?.name ?: "未分类",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarActionButton(
                        text = "删除",
                        icon = Icons.Default.Delete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        onClick = {
                            if (record.songCount > 1) {
                                pendingAction = CalendarSongActionType.DELETE
                            } else {
                                onDeleteSong(0)
                            }
                        }
                    )

                    CalendarActionButton(
                        text = "更换",
                        icon = Icons.Default.Edit,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (record.songCount > 1) {
                                pendingAction = CalendarSongActionType.REPLACE
                            } else {
                                onReplaceSong(0)
                            }
                        }
                    )

                    if (record.canAddMore && record.entries.none { it.song.isNone }) {
                        CalendarActionButton(
                            text = "添加",
                            icon = Icons.Default.Add,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            onClick = onAddClick
                        )
                    }
                }
            }

            if (showCategoryDialog && editingCategoryIndex in record.entries.indices) {
                CategorySelectionDialog(
                    categories = categories,
                    currentCategoryId = record.entries[editingCategoryIndex].categoryId,
                    onCategorySelected = {
                        onUpdateCategory(editingCategoryIndex, it)
                        showCategoryDialog = false
                    },
                    onDismissRequest = { showCategoryDialog = false }
                )
            }

            if (pendingAction != null) {
                SongActionDialog(
                    title = if (pendingAction == CalendarSongActionType.REPLACE) "选择要更换的歌曲" else "选择要删除的歌曲",
                    record = record,
                    confirmLabel = if (pendingAction == CalendarSongActionType.REPLACE) "更换这首" else "删除这首",
                    allLabel = if (pendingAction == CalendarSongActionType.REPLACE) "全部更换" else "全部删除",
                    onSongAction = { index ->
                        if (pendingAction == CalendarSongActionType.REPLACE) {
                            onReplaceSong(index)
                        } else {
                            onDeleteSong(index)
                        }
                        pendingAction = null
                    },
                    onAllAction = {
                        if (pendingAction == CalendarSongActionType.REPLACE) {
                            onReplaceAllSongs()
                        } else {
                            onDeleteAllSongs()
                        }
                        pendingAction = null
                    },
                    onDismissRequest = { pendingAction = null }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color.LightGray, MaterialTheme.shapes.medium)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "这一天还没有记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(text = date.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAddClick) {
                    Text("+ 添加记录")
                }
            }
        }
    }
}

@Composable
private fun CalendarActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text)
    }
}

@Composable
private fun SongActionDialog(
    title: String,
    record: DailyRecord,
    confirmLabel: String,
    allLabel: String,
    onSongAction: (Int) -> Unit,
    onAllAction: () -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                record.entries.forEachIndexed { index, entry ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(onClick = { onSongAction(index) }) {
                                Text(confirmLabel)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Button(
                    onClick = onAllAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(allLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DaysOfWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

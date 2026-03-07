package com.example.earwormdiary.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.ui.components.AlbumCover
import com.example.earwormdiary.ui.components.CategorySelectionDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.example.earwormdiary.ui.components.bitmapCache
import com.example.earwormdiary.ui.components.loadLocalAudioCover

@Composable
fun CalendarScreen(
    records: Map<LocalDate, DailyRecord>,
    categories: List<Category>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
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
                val song = record.song
                if (song.isNone || song.isText) return@forEach

                val isNetwork = song.albumArtUri.toString().startsWith("http")

                if (isNetwork) {
                    val request = ImageRequest.Builder(context)
                        .data(song.albumArtUri.toString())
                        .build()
                    imageLoader.enqueue(request)
                } else {
                    val cacheKey = song.uri.toString()
                    if (bitmapCache.get(cacheKey) == null) {
                        val loadedBitmap = loadLocalAudioCover(context, song.uri)
                        if (loadedBitmap != null) {
                            bitmapCache.put(cacheKey, loadedBitmap)
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
            onYearMonthSelected = { newYearMonth ->
                currentMonth = newYearMonth
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
                        // 判断是切换到下一个月还是上一个月
                        if (targetState.isAfter(initialState)) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
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
            HorizontalDivider(modifier = Modifier.weight(1f).padding(end = 8.dp), color = Color.LightGray)
            Text(text = "当日回响", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            HorizontalDivider(modifier = Modifier.weight(1f).padding(start = 8.dp), color = Color.LightGray)
        }

        DetailArea(
            date = selectedDate,
            record = records[selectedDate],
            categories = categories,
            onEditClick = { onDayClick(selectedDate) },
            onRemoveClick = { onRemoveRecord(selectedDate) },
            onUpdateCategory = { newCategoryId ->
                val record = records[selectedDate]
                if (record != null) {
                    onUpdateRecord(record.copy(categoryId = newCategoryId))
                }
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Prev",
                modifier = Modifier.size(20.dp))
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
            Icon(Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next",
                modifier = Modifier.size(20.dp))
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
                // 左下角：跳转至今天
                TextButton(
                    onClick = {
                        onYearMonthSelected(YearMonth.now())
                    }
                ) {
                    Text("跳转至今天")
                }

                // 右下角：取消
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
                // 年份按钮
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

                // 月份按钮 (始终显示数值)
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
                    // 年份选择网格
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
                                    isSelectingYear = false // 选完年自动跳到月
                                },
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Text(
                                    text = "$year",
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    // 月份选择网格
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
                                    // 选完月直接确认
                                    onYearMonthSelected(YearMonth.of(selectedYear, month))
                                },
                                colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(
                                    text = "${month}月",
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
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
    val swipeThreshold = with(density) { 50.dp.toPx() } // 滑动阈值
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
            if (day > 0 && day <= currentMonth.lengthOfMonth()) {
                return day
            }
        }
        return null
    }

    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { gridSize = it.size }
                // 月份切换手势监听
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeTotalX > swipeThreshold) {
                                onMonthSwipe(-1)
                            } else if (swipeTotalX < -swipeThreshold) {
                                onMonthSwipe(1)
                            }
                            swipeTotalX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 消费掉事件，阻止事件向上传递给 MainActivity 的页面切换逻辑
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
                                val record = records[date]
                                if (record != null) {
                                    isDragging = true
                                    dragStartDay = day
                                    draggedRecord = record
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragOffset.x - ghostSize.toPx() / 2).toInt(),
                            (dragOffset.y - ghostSize.toPx() / 2).toInt()
                        )
                    }
                    .size(ghostSize)
                    .shadow(8.dp, MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .alpha(0.9f)
            ) {
                AlbumCover(
                    song = draggedRecord!!.song,
                    modifier = Modifier.fillMaxSize()
                )
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

    val containerColor = if (isToday && record == null) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderStroke = remember(isSelected, isToday) {
        when {
            isSelected && isToday -> {
                val brush = Brush.linearGradient(
                    colors = listOf(primaryColor, selectionColor),
                    start = Offset(0f, 0f),
                    end = Offset.Infinite
                )
                BorderStroke(3.dp, brush)
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
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = borderStroke
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (record != null) {
                AlbumCover(
                    song = record.song,
                    modifier = Modifier.fillMaxSize()
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

            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        }
    }
}

@Composable
fun DetailArea(
    date: LocalDate,
    record: DailyRecord?,
    categories: List<Category>,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onUpdateCategory: (String?) -> Unit
) {
    var showCategoryDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (record != null) {
            val currentCategory = categories.find { it.id == record.categoryId }

            // ================== 第一行：封面 + 信息 ==================
            Row(verticalAlignment = Alignment.Top) {
                // 左列：封面
                Card(
                    modifier = Modifier.size(120.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    AlbumCover(
                        song = record.song,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 右列：纯文本信息 + 分类标签
                Column(modifier = Modifier.weight(1f)) {
                    SelectionContainer {
                        Column {
                            Text(text = record.song.title, style = MaterialTheme.typography.headlineSmall, color = Color.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "歌手: ${record.song.artist}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "日期: $date", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                            // 分类标签
                            if (!record.song.isNone) {
                                Spacer(modifier = Modifier.height(4.dp))

                                val backgroundColor = if (currentCategory != null) {
                                    getCategoryColor(
                                        currentCategory.id
                                    ).copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }

                                val labelText = currentCategory?.name ?: "+ 点击添加分类"

                                Surface(
                                    color = backgroundColor,
                                    shape = RoundedCornerShape(50),
                                    onClick = { showCategoryDialog = true }
                                ) {
                                    Text(
                                        text = labelText,
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

            Spacer(modifier = Modifier.height(8.dp))

            // ================== 第二行：操作按钮 ==================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 删除按钮
                Button(
                    onClick = onRemoveClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }

                // 更换按钮
                Button(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("更换")
                }
            }

            if (showCategoryDialog) {
                CategorySelectionDialog(
                    categories = categories,
                    currentCategoryId = record.categoryId,
                    onCategorySelected = {
                        onUpdateCategory(it)
                        showCategoryDialog = false
                    },
                    onDismissRequest = { showCategoryDialog = false }
                )
            }

        } else {
            Column(modifier = Modifier.fillMaxWidth().border(width = 2.dp, color = Color.LightGray, shape = MaterialTheme.shapes.medium).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "未找到耳虫捕获记录", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(text = date.toString(), style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onEditClick) { Text("+ 添加记录") }
            }
        }
    }
}

@Composable
fun DaysOfWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        val days = listOf("一", "二", "三", "四", "五", "六", "日")
        days.forEach { day ->
            Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}
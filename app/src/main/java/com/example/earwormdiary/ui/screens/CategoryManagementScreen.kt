package com.example.earwormdiary.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.earwormdiary.data.model.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private val CategoryColors = listOf(
    Color(0xFFEF9A9A), Color(0xFFF48FB1), Color(0xFFCE93D8), Color(0xFFB39DDB),
    Color(0xFF9FA8DA), Color(0xFF90CAF9), Color(0xFF81D4FA), Color(0xFF80CBC4),
    Color(0xFFA5D6A7), Color(0xFFE6EE9C), Color(0xFFFFF59D), Color(0xFFFFCC80),
    Color(0xFFFFAB91), Color(0xFFBCAAA4), Color(0xFFB0BEC5)
)

fun getCategoryColor(id: String): Color {
    val index = id.hashCode().absoluteValue % CategoryColors.size
    return CategoryColors[index]
}

@Composable
fun CategoryManagementScreen(
    categories: List<Category>,
    onCategoriesChanged: (List<Category>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var inputText by remember { mutableStateOf("") }

    val localCategories = remember { mutableStateListOf<Category>() }
    val listState = rememberLazyListState()

    // ---- 拖拽状态管理 ----
    var isDragging by remember { mutableStateOf(false) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggedItemData by remember { mutableStateOf<Category?>(null) }

    // 手指位置
    var touchOffsetInItemY by remember { mutableFloatStateOf(0f) }
    var currentTouchY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val handleWidthPx = with(density) { 60.dp.toPx() }
    val autoScrollThreshold = with(density) { 60.dp.toPx() }
    val scrollSpeed = 15f
    val itemSpacingPx = with(density) { 8.dp.toPx() }

    // 初始化数据
    LaunchedEffect(categories) {
        if (!isDragging) {
            localCategories.clear()
            localCategories.addAll(categories)
        }
    }

    // ---- 1. 使用 derivedStateOf 计算插入位置 ----
    val targetInsertIndex by remember {
        derivedStateOf {
            if (!isDragging) return@derivedStateOf null

            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isEmpty()) return@derivedStateOf null

            val checkY = currentTouchY
            val hitItem = visibleItems.find { item ->
                checkY >= item.offset && checkY <= (item.offset + item.size)
            }

            if (hitItem != null) {
                val itemCenter = hitItem.offset + (hitItem.size / 2)
                if (checkY < itemCenter) {
                    hitItem.index
                } else {
                    hitItem.index + 1
                }
            } else {
                val firstItem = visibleItems.first()
                val lastItem = visibleItems.last()

                if (checkY < firstItem.offset) {
                    firstItem.index
                } else if (checkY > lastItem.offset + lastItem.size) {
                    lastItem.index + 1
                } else {
                    null
                }
            }
        }
    }

    // ---- 2. 自动滚动逻辑 ----
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (isActive) {
                val scrollDiff = withFrameNanos {
                    val layoutInfo = listState.layoutInfo
                    val viewportHeight = layoutInfo.viewportSize.height
                    var diff = 0f
                    if (currentTouchY < autoScrollThreshold) {
                        diff = -scrollSpeed
                    } else if (currentTouchY > viewportHeight - autoScrollThreshold) {
                        diff = scrollSpeed
                    }
                    diff
                }

                if (scrollDiff != 0f) {
                    listState.scrollBy(scrollDiff)
                }
            }
        }
    }

    fun openDialog(category: Category? = null) {
        editingCategory = category
        inputText = category?.name ?: ""
        showDialog = true
    }

    fun saveCategory() {
        if (inputText.isBlank()) return
        val newName = inputText.trim()
        val isDuplicate = localCategories.any { existing ->
            existing.name == newName && (editingCategory == null || existing.id != editingCategory!!.id)
        }
        if (isDuplicate) {
            Toast.makeText(context, "该类别已存在，请使用其他名称", Toast.LENGTH_SHORT).show()
            return
        }

        val newList = localCategories.toMutableList()
        if (editingCategory != null) {
            val index = newList.indexOfFirst { it.id == editingCategory!!.id }
            if (index != -1) newList[index] = editingCategory!!.copy(name = newName)
        } else {
            newList.add(0, Category(UUID.randomUUID().toString(), newName))
        }

        onCategoriesChanged(newList)
        showDialog = false

        scope.launch {
            delay(100)
            listState.scrollToItem(0)
        }
    }

    fun deleteCategory(id: String) {
        val newList = localCategories.filter { it.id != id }
        onCategoriesChanged(newList)
    }

    fun performMove() {
        val fromIndex = draggedItemIndex ?: return
        var toIndex = targetInsertIndex ?: return

        if (toIndex < 0) toIndex = 0
        if (toIndex > localCategories.size) toIndex = localCategories.size

        if (toIndex == fromIndex || toIndex == fromIndex + 1) return

        val item = localCategories[fromIndex]
        val newList = localCategories.toMutableList()

        newList.removeAt(fromIndex)
        val finalInsertIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
        newList.add(finalInsertIndex, item)

        localCategories.clear()
        localCategories.addAll(newList)
        onCategoriesChanged(newList)

        if (finalInsertIndex == 0) {
            scope.launch {
                listState.scrollToItem(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val changeX = down.position.x
                    val changeY = down.position.y

                    val hitItemInfo = listState.layoutInfo.visibleItemsInfo.find { item ->
                        changeY >= item.offset && changeY <= (item.offset + item.size)
                    }

                    if (hitItemInfo != null && changeX < handleWidthPx) {
                        down.consume()

                        isDragging = true
                        draggedItemIndex = hitItemInfo.index
                        draggedItemData = localCategories.getOrNull(hitItemInfo.index)
                        touchOffsetInItemY = changeY - hitItemInfo.offset
                        currentTouchY = changeY

                        drag(down.id) { change ->
                            change.consume()
                            currentTouchY = change.position.y
                        }

                        performMove()

                        isDragging = false
                        draggedItemIndex = null
                        draggedItemData = null
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                userScrollEnabled = !isDragging
            ) {
                itemsIndexed(localCategories, key = { _, item -> item.id }) { index, category ->
                    val isSource = isDragging && index == draggedItemIndex

                    Box(modifier = Modifier.alpha(if (isSource) 0.3f else 1f)) {
                        CategoryItem(
                            category = category,
                            elevation = 2.dp,
                            onEdit = { openDialog(category) },
                            onDelete = { deleteCategory(category.id) },
                            modifier = Modifier
                        )
                    }
                }
            }
        }

        // 指示线逻辑
        val indicatorLineY by remember {
            derivedStateOf {
                if (!isDragging) return@derivedStateOf null
                val target = targetInsertIndex ?: return@derivedStateOf null
                val source = draggedItemIndex ?: -1

                if (target == source || target == source + 1) return@derivedStateOf null

                val layoutInfo = listState.layoutInfo
                val targetItemInfo = layoutInfo.visibleItemsInfo.find { it.index == target }

                if (targetItemInfo != null) {
                    targetItemInfo.offset.toFloat()
                } else {
                    val prevItemInfo = layoutInfo.visibleItemsInfo.find { it.index == target - 1 }
                    if (prevItemInfo != null) {
                        prevItemInfo.offset + prevItemInfo.size + itemSpacingPx
                    } else if (target == 0) {
                        layoutInfo.viewportStartOffset.toFloat()
                    } else {
                        null
                    }
                }
            }
        }

        if (indicatorLineY != null) {
            val paddingPx = with(density) { 16.dp.toPx() }
            val lineY = indicatorLineY!!

            Canvas(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                val canvasWidth = size.width
                val drawY = lineY + paddingPx

                drawLine(
                    color = Color(0xFF2196F3),
                    start = Offset(paddingPx, drawY),
                    end = Offset(canvasWidth - paddingPx, drawY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFF2196F3),
                    radius = 6.dp.toPx(),
                    center = Offset(paddingPx, drawY)
                )
            }
        }

        // 悬浮层
        if (isDragging && draggedItemData != null) {
            val visualTop = currentTouchY - touchOffsetInItemY

            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(0, visualTop.roundToInt()) }
                    .zIndex(3f)
                    .shadow(8.dp, MaterialTheme.shapes.medium)
                    .alpha(0.9f)
            ) {
                CategoryItem(
                    category = draggedItemData!!,
                    elevation = 8.dp,
                    onEdit = {},
                    onDelete = {},
                    modifier = Modifier
                )
            }
        }

        FloatingActionButton(
            onClick = { openDialog(null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加类别")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(if (editingCategory == null) "新建类别" else "修改类别") },
                text = {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("类别名称") },
                        singleLine = true
                    )
                },
                confirmButton = { TextButton(onClick = { saveCategory() }) { Text("保存") } },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: Category,
    elevation: Dp,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tagColor = getCategoryColor(category.id)

    Card(
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "拖拽排序",
                tint = Color.Gray,
                modifier = modifier
                    .padding(end = 4.dp)
                    .size(20.dp)
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color = tagColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            CompactIconButton(
                icon = Icons.Default.Edit,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onEdit
            )

            CompactIconButton(
                icon = Icons.Default.Delete,
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
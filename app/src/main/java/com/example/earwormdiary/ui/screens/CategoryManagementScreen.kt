package com.example.earwormdiary.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.earwormdiary.data.model.DailyRecord
import java.time.LocalDate
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    records: Map<LocalDate, DailyRecord>,
    selectedCategoryId: String?,
    onSelectedCategoryIdChange: (String?) -> Unit,
    onCategoriesChanged: (List<Category>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val localCategories = remember { mutableStateListOf<Category>() }

    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var inputText by remember { mutableStateOf("") }
    var revealedCategoryId by remember { mutableStateOf<String?>(null) }

    var isDragging by remember { mutableStateOf(false) }
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggedItemData by remember { mutableStateOf<Category?>(null) }
    var touchOffsetInItemY by remember { mutableFloatStateOf(0f) }
    var currentTouchY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val handleWidthPx = with(density) { 60.dp.toPx() }
    val autoScrollThreshold = with(density) { 60.dp.toPx() }
    val scrollSpeed = 15f
    val itemSpacingPx = with(density) { 8.dp.toPx() }

    LaunchedEffect(categories) {
        if (!isDragging) {
            localCategories.clear()
            localCategories.addAll(normalizeCategories(categories))
        }
    }

    val activeCount by remember {
        derivedStateOf { localCategories.count { !it.archived } }
    }

    val targetInsertIndex by remember {
        derivedStateOf {
            if (!isDragging) return@derivedStateOf null

            val sourceIndex = draggedItemIndex ?: return@derivedStateOf null
            val draggedCategory = localCategories.getOrNull(sourceIndex) ?: return@derivedStateOf null
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null

            val hitItem = visibleItems.find { item ->
                currentTouchY >= item.offset && currentTouchY <= item.offset + item.size
            }

            val rawTarget = when {
                hitItem != null -> {
                    val itemCenter = hitItem.offset + hitItem.size / 2
                    if (currentTouchY < itemCenter) hitItem.index else hitItem.index + 1
                }
                currentTouchY < visibleItems.first().offset -> visibleItems.first().index
                currentTouchY > visibleItems.last().offset + visibleItems.last().size -> visibleItems.last().index + 1
                else -> null
            }

            val rangeStart = if (draggedCategory.archived) activeCount else 0
            val rangeEnd = if (draggedCategory.archived) localCategories.size else activeCount
            rawTarget?.coerceIn(rangeStart, rangeEnd)
        }
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (isActive) {
                val scrollDiff = withFrameNanos {
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    when {
                        currentTouchY < autoScrollThreshold -> -scrollSpeed
                        currentTouchY > viewportHeight - autoScrollThreshold -> scrollSpeed
                        else -> 0f
                    }
                }

                if (scrollDiff != 0f) {
                    listState.scrollBy(scrollDiff)
                }
            }
        }
    }

    fun persistCategories(updated: List<Category>) {
        val normalized = normalizeCategories(updated)
        localCategories.clear()
        localCategories.addAll(normalized)
        onCategoriesChanged(normalized)
    }

    fun openDialog(category: Category? = null) {
        revealedCategoryId = null
        editingCategory = category
        inputText = category?.name ?: ""
        showDialog = true
    }

    fun saveCategory() {
        val newName = inputText.trim()
        if (newName.isBlank()) return

        val isDuplicate = localCategories.any { existing ->
            existing.name == newName && (editingCategory == null || existing.id != editingCategory?.id)
        }
        if (isDuplicate) {
            Toast.makeText(context, "该类别已存在，请使用其他名称", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = localCategories.toMutableList()
        if (editingCategory != null) {
            val index = updated.indexOfFirst { it.id == editingCategory?.id }
            if (index != -1) {
                updated[index] = updated[index].copy(name = newName)
            }
        } else {
            updated.add(0, Category(id = UUID.randomUUID().toString(), name = newName))
        }

        persistCategories(updated)
        showDialog = false

        scope.launch {
            listState.scrollToItem(0)
        }
    }

    fun deleteCategory(id: String) {
        revealedCategoryId = null
        persistCategories(localCategories.filter { it.id != id })
    }

    fun setArchived(id: String, archived: Boolean) {
        revealedCategoryId = null
        val updated = localCategories.map { category ->
            if (category.id == id) category.copy(archived = archived) else category
        }
        persistCategories(updated)
    }

    fun performMove() {
        val fromIndex = draggedItemIndex ?: return
        val draggedCategory = localCategories.getOrNull(fromIndex) ?: return

        var toIndex = targetInsertIndex ?: return
        val rangeStart = if (draggedCategory.archived) activeCount else 0
        val rangeEnd = if (draggedCategory.archived) localCategories.size else activeCount
        toIndex = toIndex.coerceIn(rangeStart, rangeEnd)

        if (toIndex == fromIndex || toIndex == fromIndex + 1) return

        revealedCategoryId = null
        val updated = localCategories.toMutableList()
        val item = updated.removeAt(fromIndex)
        val finalInsertIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex
        updated.add(finalInsertIndex, item)
        persistCategories(updated)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(localCategories.size, revealedCategoryId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hitItemInfo = listState.layoutInfo.visibleItemsInfo.find { item ->
                            down.position.y >= item.offset && down.position.y <= item.offset + item.size
                        }
                        val hitCategory = hitItemInfo?.index?.let(localCategories::getOrNull)

                        if (
                            hitItemInfo != null &&
                            hitCategory != null &&
                            down.position.x < handleWidthPx &&
                            revealedCategoryId == null
                        ) {
                            down.consume()
                            isDragging = true
                            draggedItemIndex = hitItemInfo.index
                            draggedItemData = hitCategory
                            touchOffsetInItemY = down.position.y - hitItemInfo.offset
                            currentTouchY = down.position.y

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (localCategories.isEmpty()) {
                    EmptyCategoryState()
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        userScrollEnabled = !isDragging
                    ) {
                        itemsIndexed(localCategories, key = { _, item -> item.id }) { index, category ->
                            val isSource = isDragging && index == draggedItemIndex
                            val showArchivedHeader = category.archived && (index == 0 || !localCategories[index - 1].archived)
                            val editAction: (() -> Unit)? = if (category.archived) {
                                null
                            } else {
                                { revealedCategoryId = null; openDialog(category) }
                            }
                            val deleteAction: (() -> Unit)? = if (category.archived) {
                                null
                            } else {
                                { revealedCategoryId = null; deleteCategory(category.id) }
                            }

                            Column(
                                modifier = Modifier.alpha(if (isSource) 0.35f else 1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (showArchivedHeader) {
                                    Text(
                                        text = "已归档类别",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                    )
                                }

                                CategoryItem(
                                    category = category,
                                    elevation = 2.dp,
                                    onClick = {
                                        if (revealedCategoryId == category.id) {
                                            revealedCategoryId = null
                                        } else {
                                            onSelectedCategoryIdChange(category.id)
                                        }
                                    },
                                    onEdit = editAction,
                                    onDelete = deleteAction,
                                    onArchiveToggle = { setArchived(category.id, !category.archived) },
                                    isActionsRevealed = revealedCategoryId == category.id,
                                    onActionsRevealChange = { revealed ->
                                        revealedCategoryId = if (revealed) category.id else null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val indicatorLineY by remember {
                derivedStateOf {
                    if (!isDragging) return@derivedStateOf null
                    val target = targetInsertIndex ?: return@derivedStateOf null
                    val source = draggedItemIndex ?: return@derivedStateOf null

                    if (target == source || target == source + 1) return@derivedStateOf null

                    val targetItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == target }
                    when {
                        targetItemInfo != null -> targetItemInfo.offset.toFloat()
                        target == 0 -> listState.layoutInfo.viewportStartOffset.toFloat()
                        else -> {
                            val previousItem = listState.layoutInfo.visibleItemsInfo.find { it.index == target - 1 }
                            previousItem?.let { it.offset + it.size + itemSpacingPx }
                        }
                    }
                }
            }

            if (indicatorLineY != null) {
                val paddingPx = with(density) { 16.dp.toPx() }
                val lineY = indicatorLineY!!
                val indicatorColor = MaterialTheme.colorScheme.primary
                val archivedHeaderOffsetPx = with(density) { 32.dp.toPx() }
                val adjustedLineY = if (
                    targetInsertIndex == activeCount &&
                    localCategories.getOrNull(activeCount)?.archived == true
                ) {
                    lineY + archivedHeaderOffsetPx
                } else {
                    lineY
                }

                Canvas(modifier = Modifier.fillMaxSize().zIndex(2f)) {
                    val drawY = adjustedLineY + paddingPx
                    drawLine(
                        color = indicatorColor,
                        start = Offset(paddingPx, drawY),
                        end = Offset(size.width - paddingPx, drawY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = indicatorColor,
                        radius = 6.dp.toPx(),
                        center = Offset(paddingPx, drawY)
                    )
                }
            }

            if (isDragging && draggedItemData != null) {
                val visualTop = currentTouchY - touchOffsetInItemY

                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .offset { IntOffset(0, visualTop.roundToInt()) }
                        .zIndex(3f)
                        .shadow(8.dp, MaterialTheme.shapes.medium)
                        .alpha(0.92f)
                ) {
                    CategoryItem(
                        category = draggedItemData!!,
                        elevation = 8.dp,
                        onClick = {},
                        onEdit = null,
                        onDelete = null,
                        onArchiveToggle = {},
                        isActionsRevealed = false,
                        onActionsRevealChange = {}
                    )
                }
            }

            FloatingActionButton(
                onClick = { openDialog() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
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
                    confirmButton = {
                        TextButton(onClick = { saveCategory() }) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = selectedCategoryId != null,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f)
        ) {
            BackHandler { onSelectedCategoryIdChange(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(16.dp)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                selectedCategoryId?.let { activeCategoryId ->
                    CategoryStatsScreen(
                        categoryId = activeCategoryId,
                        categories = localCategories,
                        records = records
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCategoryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有类别",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击右下角按钮添加类别。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryItem(
    category: Category,
    elevation: Dp,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onArchiveToggle: () -> Unit,
    isActionsRevealed: Boolean,
    onActionsRevealChange: (Boolean) -> Unit
) {
    val tagColor = getCategoryColor(category.id)
    val actionAreaWidth = 52.dp
    val offsetX by animateDpAsState(
        targetValue = if (isActionsRevealed) -actionAreaWidth else 0.dp,
        label = "category_swipe_offset"
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionAreaWidth)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            CompactIconButton(
                icon = if (category.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = if (category.archived) "取消归档" else "归档类别",
                onClick = onArchiveToggle
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = offsetX)
                .clip(RoundedCornerShape(20.dp))
                .pointerInput(category.id, isActionsRevealed) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                totalDrag <= -32f -> onActionsRevealChange(true)
                                totalDrag >= 32f -> onActionsRevealChange(false)
                                else -> onActionsRevealChange(isActionsRevealed)
                            }
                        }
                    )
                }
                .clickable {
                    if (isActionsRevealed) {
                        onActionsRevealChange(false)
                    } else {
                        onClick()
                    }
                },
            elevation = CardDefaults.cardElevation(elevation),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "拖拽排序",
                    tint = if (category.archived) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        Color.Gray
                    },
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color = tagColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.92f),
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

                if (!category.archived) {
                    CompactIconButton(
                        icon = Icons.Default.Edit,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = "编辑类别",
                        onClick = { onEdit?.invoke() }
                    )
                    CompactIconButton(
                        icon = Icons.Default.Delete,
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = "删除类别",
                        onClick = { onDelete?.invoke() }
                    )
                }
            }
        }
    }
}

@Composable
fun CompactIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
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
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun normalizeCategories(categories: List<Category>): List<Category> {
    return categories.filterNot { it.archived } + categories.filter { it.archived }
}

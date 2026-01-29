package com.example.earwormdiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.earwormdiary.ui.components.AlbumCover
import com.example.earwormdiary.ui.components.CategorySelectionDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    records: Map<LocalDate, com.example.earwormdiary.data.model.DailyRecord>,
    categories: List<com.example.earwormdiary.data.model.Category>,
    onNavigateToSearch: () -> Unit,
    onRemoveRecord: () -> Unit,
    onUpdateRecord: (com.example.earwormdiary.data.model.DailyRecord) -> Unit
) {
    val today = LocalDate.now()
    val record = records[today]

    var showCategoryDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (record != null) {
            TodayDetailView(
                record = record,
                categories = categories,
                onEditClick = onNavigateToSearch,
                onRemoveClick = onRemoveRecord,
                onCategoryClick = { showCategoryDialog = true }
            )
        } else {
            TodayEmptyView(onAddClick = onNavigateToSearch)
        }
    }

    if (showCategoryDialog && record != null) {
        CategorySelectionDialog(
            categories = categories,
            currentCategoryId = record.categoryId,
            onCategorySelected = { newCategoryId ->
                onUpdateRecord(record.copy(categoryId = newCategoryId))
                showCategoryDialog = false
            },
            onDismissRequest = { showCategoryDialog = false }
        )
    }
}

@Composable
fun TodayDetailView(
    record: com.example.earwormdiary.data.model.DailyRecord,
    categories: List<com.example.earwormdiary.data.model.Category>,
    onEditClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val dateText = record.date.format(DateTimeFormatter.ofPattern("M月d日"))
    val currentCategory = categories.find { it.id == record.categoryId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f),
                        elevation = CardDefaults.cardElevation(12.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        AlbumCover(
                            song = record.song,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = record.song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = record.song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )

                    // 分类标签逻辑
                    // 只有当歌曲不是“无”的时候，才显示分类操作区
                    if (!record.song.isNone) {
                        Spacer(modifier = Modifier.height(8.dp))

                        val backgroundColor = if (currentCategory != null) {
                            getCategoryColor(
                                currentCategory.id
                            ).copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }

                        val textColor = if (currentCategory != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val labelText = currentCategory?.name ?: "+  点击添加分类"

                        Surface(
                            color = backgroundColor,
                            shape = RoundedCornerShape(50),
                            onClick = onCategoryClick
                        ) {
                            Text(
                                text = labelText,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        // 底部按钮栏
        Row(
            modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 删除
            Button(
                onClick = onRemoveClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除")
            }


            // 2. 更换
            Button(
                onClick = onEditClick,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("更换")
            }
        }
    }
}

@Composable
fun TodayEmptyView(onAddClick: () -> Unit) {
    val todayText = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 整体居中，并留出顶部空间
        Spacer(modifier = Modifier.weight(0.8f))

        // 1. 日期
        Text(
            text = todayText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        // 2. 加号方框
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 20.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加记录",
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(40.dp))

        // 3. 提示文字
        Text(
            text = "点击记录今日旋律",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}
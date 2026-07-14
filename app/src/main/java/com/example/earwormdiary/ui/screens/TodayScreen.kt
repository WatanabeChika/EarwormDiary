package com.example.earwormdiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.ui.components.CategorySelectionDialog
import com.example.earwormdiary.ui.components.DailyRecordCover
import com.example.earwormdiary.ui.components.SegmentedCategoryStrip
import java.lang.Character.UnicodeBlock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class SongActionType {
    REPLACE,
    DELETE
}

@Composable
fun TodayScreen(
    records: Map<LocalDate, DailyRecord>,
    categories: List<Category>,
    onAddSong: () -> Unit,
    onReplaceSong: (Int) -> Unit,
    onReplaceAllSongs: () -> Unit,
    onDeleteSong: (Int) -> Unit,
    onDeleteAllSongs: () -> Unit,
    onUpdateRecord: (DailyRecord) -> Unit
) {
    val today = LocalDate.now()
    val record = records[today]

    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategoryIndex by remember { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<SongActionType?>(null) }

    if (record != null) {
        TodayDetailView(
            record = record,
            categories = categories,
            onAddClick = onAddSong,
            onReplaceClick = {
                if (record.songCount > 1) {
                    pendingAction = SongActionType.REPLACE
                } else {
                    onReplaceSong(0)
                }
            },
            onDeleteClick = {
                if (record.songCount > 1) {
                    pendingAction = SongActionType.DELETE
                } else {
                    onDeleteSong(0)
                }
            },
            onCategoryClick = { index ->
                if (!record.entries[index].song.isNone) {
                    editingCategoryIndex = index
                    showCategoryDialog = true
                }
            }
        )
    } else {
        TodayEmptyView(onAddClick = onAddSong)
    }

    if (showCategoryDialog && record != null && editingCategoryIndex in record.entries.indices) {
        CategorySelectionDialog(
            categories = categories,
            currentCategoryId = record.entries[editingCategoryIndex].categoryId,
            onCategorySelected = {
                onUpdateRecord(record.updateCategory(editingCategoryIndex, it))
                showCategoryDialog = false
            },
            onDismissRequest = { showCategoryDialog = false }
        )
    }

    if (pendingAction != null && record != null) {
        SongActionDialog(
            title = if (pendingAction == SongActionType.REPLACE) "选择要更换的歌曲" else "选择要删除的歌曲",
            record = record,
            confirmLabel = if (pendingAction == SongActionType.REPLACE) "更换这首" else "删除这首",
            allLabel = if (pendingAction == SongActionType.REPLACE) "全部更换" else "全部删除",
            onSongAction = { index ->
                if (pendingAction == SongActionType.REPLACE) {
                    onReplaceSong(index)
                } else {
                    onDeleteSong(index)
                }
                pendingAction = null
            },
            onAllAction = {
                if (pendingAction == SongActionType.REPLACE) {
                    onReplaceAllSongs()
                } else {
                    onDeleteAllSongs()
                }
                pendingAction = null
            },
            onDismissRequest = { pendingAction = null }
        )
    }
}

@Composable
fun TodayDetailView(
    record: DailyRecord,
    categories: List<Category>,
    onAddClick: () -> Unit,
    onReplaceClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCategoryClick: (Int) -> Unit
) {
    val dateText = record.date.format(DateTimeFormatter.ofPattern("M月d日"))
    val titleStyle = when (record.songCount) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    val artistStyle = when (record.songCount) {
        1 -> MaterialTheme.typography.titleMedium
        2 -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.bodyMedium
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f),
                    elevation = CardDefaults.cardElevation(12.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    DailyRecordCover(record = record, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(25.dp))

                if (record.songCount == 1) {
                    val entry = record.entries.first()
                    SongTextBlock(
                        title = entry.song.title,
                        artist = entry.song.artist,
                        titleStyle = titleStyle,
                        artistStyle = artistStyle,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(todaySongSpacing(record.songCount))
                    ) {
                        record.entries.forEach { entry ->
                            SongTextBlock(
                                title = entry.song.title,
                                artist = entry.song.artist,
                                titleStyle = titleStyle,
                                artistStyle = artistStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (record.entries.any { !it.song.isNone }) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SegmentedCategoryStrip(
                        entries = record.entries,
                        categories = categories,
                        modifier = Modifier.fillMaxWidth(todayCategoryStripWidth(record, categories)),
                        emptyLabel = "未分类",
                        slanted = record.songCount > 1,
                        onSegmentClick = onCategoryClick
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                text = "删除",
                icon = Icons.Default.Delete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                onClick = onDeleteClick
            )

            ActionButton(
                text = "更换",
                icon = Icons.Default.Edit,
                modifier = Modifier.weight(1f),
                onClick = onReplaceClick
            )

            if (record.canAddMore) {
                ActionButton(
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
}

private fun todayCategoryStripWidth(record: DailyRecord, categories: List<Category>): Float {
    return when (record.songCount) {
        1 -> {
            val categoryName = categories.find { it.id == record.primaryEntry.categoryId }?.name ?: "未分类"
            val visualWidthUnits = categoryName.sumOf { todayCategoryCharWidth(it).toDouble() }.toFloat()
            (0.14f + visualWidthUnits * 0.038f).coerceIn(0.2f, 0.46f)
        }

        2 -> 0.72f
        else -> 0.84f
    }
}

private fun todaySongSpacing(songCount: Int): Dp = when (songCount) {
    2 -> 6.dp
    else -> 12.dp
}

private fun todayCategoryCharWidth(char: Char): Float {
    if (char.isWhitespace()) return 0.3f
    if (char.code in 0x0020..0x007E) {
        return if (char.isLetterOrDigit()) 0.5f else 0.35f
    }

    return when (UnicodeBlock.of(char)) {
        UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION,
        UnicodeBlock.HIRAGANA,
        UnicodeBlock.KATAKANA,
        UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS,
        UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS,
        UnicodeBlock.HANGUL_SYLLABLES,
        UnicodeBlock.HANGUL_JAMO,
        UnicodeBlock.HANGUL_COMPATIBILITY_JAMO -> 1f

        else -> 0.75f
    }
}

@Composable
private fun SongTextBlock(
    title: String,
    artist: String,
    titleStyle: TextStyle,
    artistStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = titleStyle,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = artist,
            style = artistStyle,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
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
        Spacer(modifier = Modifier.size(4.dp))
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
fun TodayEmptyView(onAddClick: () -> Unit) {
    val todayText = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.8f))

        Text(
            text = todayText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(onClick = onAddClick),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 20.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
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

            Text(
                text = "点击记录今日旋律",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

package com.example.earwormdiary.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.RecordEntry
import com.example.earwormdiary.ui.screens.getCategoryColor
import java.time.LocalDate

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SongRecordCard(
    date: LocalDate,
    entry: RecordEntry,
    categories: List<Category>,
    modifier: Modifier = Modifier,
    onCategoryClick: (() -> Unit)? = null
) {
    val currentCategory = categories.find { it.id == entry.categoryId }
    val categoryColor = currentCategory?.let { getCategoryColor(it.id).copy(alpha = 0.2f) }
        ?: MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier,
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
                    modifier = Modifier.size(coverSize),
                    elevation = CardDefaults.cardElevation(4.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    DailyRecordCover(
                        record = DailyRecord(date = date, entries = listOf(entry)),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
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
                            modifier = if (onCategoryClick != null) {
                                Modifier.clickable(onClick = onCategoryClick)
                            } else {
                                Modifier
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

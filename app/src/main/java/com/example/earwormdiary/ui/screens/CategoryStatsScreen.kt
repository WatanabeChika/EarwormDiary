package com.example.earwormdiary.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.data.model.DailyRecord
import com.example.earwormdiary.data.model.RecordEntry
import com.example.earwormdiary.ui.components.SongRecordCard
import java.time.LocalDate

private data class CategoryRecordMatch(
    val date: LocalDate,
    val entry: RecordEntry
)

@Composable
fun CategoryStatsScreen(
    categoryId: String,
    categories: List<Category>,
    records: Map<LocalDate, DailyRecord>
) {
    val category = categories.find { it.id == categoryId }
    val matches = records
        .toList()
        .sortedByDescending { it.first }
        .flatMap { (date, record) ->
            record.entries
                .filter { it.categoryId == categoryId }
                .map { entry -> CategoryRecordMatch(date = date, entry = entry) }
        }

    if (category == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "该类别不存在",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CategoryStatsHeader(
                category = category,
                recordCount = matches.size
            )
        }

        if (matches.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "当前没有歌曲记录使用这个类别。",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "归档后历史标签仍会保留；只要有记录带着这个类别，它们都会继续显示在这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                matches,
                key = { index, match -> "${match.date}-$index-${match.entry.song.id}-${match.entry.song.title}" }
            ) { _, match ->
                SongRecordCard(
                    date = match.date,
                    entry = match.entry,
                    categories = categories
                )
            }
        }
    }
}

@Composable
private fun CategoryStatsHeader(
    category: Category,
    recordCount: Int
) {
    val tagColor = getCategoryColor(category.id)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(tagColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                SelectionContainer {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "当前共有 $recordCount 条歌曲记录使用这个类别。",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

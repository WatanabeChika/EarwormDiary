package com.example.earwormdiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.Category
import com.example.earwormdiary.ui.screens.getCategoryColor

@Composable
fun CategorySelectionDialog(
    categories: List<Category>,
    currentCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("设置类别") },
        text = {
            if (categories.isEmpty()) {
                Text("暂无类别，请先去设置页添加。")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    // "无类别" 选项
                    item {
                        CategorySelectionItem(
                            name = "无类别",
                            color = Color.LightGray,
                            isSelected = currentCategoryId == null,
                            onClick = { onCategorySelected(null) }
                        )
                    }

                    // 类别列表
                    items(categories) { category ->
                        CategorySelectionItem(
                            name = category.name,
                            color = getCategoryColor(category.id),
                            isSelected = currentCategoryId == category.id,
                            onClick = { onCategorySelected(category.id) }
                        )
                    }
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
fun CategorySelectionItem(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
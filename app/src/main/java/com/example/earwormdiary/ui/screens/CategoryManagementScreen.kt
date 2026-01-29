package com.example.earwormdiary.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.earwormdiary.data.model.Category
import java.util.UUID
import kotlin.math.absoluteValue

private val CategoryColors = listOf(
    Color(0xFFEF9A9A), // Red 200
    Color(0xFFF48FB1), // Pink 200
    Color(0xFFCE93D8), // Purple 200
    Color(0xFFB39DDB), // Deep Purple 200
    Color(0xFF9FA8DA), // Indigo 200
    Color(0xFF90CAF9), // Blue 200
    Color(0xFF81D4FA), // Light Blue 200
    Color(0xFF80CBC4), // Teal 200
    Color(0xFFA5D6A7), // Green 200
    Color(0xFFE6EE9C), // Lime 200
    Color(0xFFFFF59D), // Yellow 200
    Color(0xFFFFCC80), // Orange 200
    Color(0xFFFFAB91), // Deep Orange 200
    Color(0xFFBCAAA4), // Brown 200
    Color(0xFFB0BEC5)  // Blue Grey 200
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
    var showDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var inputText by remember { mutableStateOf("") }

    fun openDialog(category: Category? = null) {
        editingCategory = category
        inputText = category?.name ?: ""
        showDialog = true
    }

    fun saveCategory() {
        if (inputText.isBlank()) return

        val newName = inputText.trim()

        // 检查重名
        val isDuplicate = categories.any { existing ->
            existing.name == newName && (editingCategory == null || existing.id != editingCategory!!.id)
        }

        if (isDuplicate) {
            Toast.makeText(context, "该类别已存在，请使用其他名称", Toast.LENGTH_SHORT).show()
            return
        }

        val newList = categories.toMutableList()
        if (editingCategory != null) {
            val index = newList.indexOfFirst { it.id == editingCategory!!.id }
            if (index != -1) newList[index] = editingCategory!!.copy(name = newName)
        } else {
            newList.add(Category(UUID.randomUUID().toString(), newName))
        }
        onCategoriesChanged(newList)
        showDialog = false
    }

    fun deleteCategory(id: String) {
        val newList = categories.filter { it.id != id }
        onCategoriesChanged(newList)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    CategoryItem(
                        category = category,
                        onEdit = { openDialog(category) },
                        onDelete = { deleteCategory(category.id) }
                    )
                }
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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tagColor = getCategoryColor(category.id)

    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = tagColor, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
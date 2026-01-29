package com.example.earwormdiary.data.local

import android.content.Context
import com.example.earwormdiary.data.model.Category
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CategoryStorage {
    private const val FILE_NAME = "categories.json"

    fun saveCategories(context: Context, categories: List<Category>) {
        try {
            val jsonArray = JSONArray()
            categories.forEach { category ->
                val jsonObj = JSONObject().apply {
                    put("id", category.id)
                    put("name", category.name)
                }
                jsonArray.put(jsonObj)
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadCategories(context: Context): List<Category> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) {
            return emptyList()
        }

        val list = mutableListOf<Category>()
        try {
            val jsonString = file.readText()
            if (jsonString.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Category(
                        id = obj.getString("id"),
                        name = obj.getString("name")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
        return list
    }
}
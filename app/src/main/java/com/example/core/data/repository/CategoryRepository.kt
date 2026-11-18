package com.example.core.data.repository

import com.example.core.database.SeedData
import com.example.core.database.dao.CategoryDao
import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CategoryType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CategoryRepository(
    private val categoryDao: CategoryDao
) {
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.observeAllCategories()

    fun categoriesByType(type: CategoryType): Flow<List<CategoryEntity>> =
        categoryDao.observeCategoriesByType(type)

    suspend fun ensureDefaultCategoriesSeeded() {
        if (categoryDao.countCategories() == 0) {
            categoryDao.insertCategories(SeedData.defaultCategories())
        }
    }

    suspend fun createCategory(
        name: String,
        type: CategoryType,
        parentId: String? = null,
        colorHex: Long = 0xFF1E6BFF
    ): CategoryEntity {
        val entity = CategoryEntity(
            id = "cat_${UUID.randomUUID()}",
            name = name.trim(),
            type = type,
            parentId = parentId,
            iconKey = "label",
            colorHex = colorHex,
            isSystem = false,
            sortOrder = 100
        )
        categoryDao.insertCategory(entity)
        return entity
    }
}

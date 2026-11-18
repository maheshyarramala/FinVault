package com.example.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CategoryType {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["type"]),
        Index(value = ["parent_id"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: CategoryType,
    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,
    @ColumnInfo(name = "icon_key")
    val iconKey: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: Long,
    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = true,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)

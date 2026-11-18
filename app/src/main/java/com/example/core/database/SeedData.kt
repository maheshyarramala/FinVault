package com.example.core.database

import com.example.core.database.entity.CategoryEntity
import com.example.core.database.entity.CategoryType

/**
 * Default Indian personal finance categories seeded idempotently on first database creation.
 * No mock transactions or hardcoded account balances are ever seeded.
 */
object SeedData {
    fun defaultCategories(): List<CategoryEntity> = listOf(
        // Expense Categories
        CategoryEntity(
            id = "cat_exp_food",
            name = "Food & Dining",
            type = CategoryType.EXPENSE,
            iconKey = "restaurant",
            colorHex = 0xFFEA580C,
            sortOrder = 1
        ),
        CategoryEntity(
            id = "cat_exp_groceries",
            name = "Groceries",
            type = CategoryType.EXPENSE,
            iconKey = "shopping_basket",
            colorHex = 0xFF16A34A,
            sortOrder = 2
        ),
        CategoryEntity(
            id = "cat_exp_transport",
            name = "Transport & Fuel",
            type = CategoryType.EXPENSE,
            iconKey = "directions_car",
            colorHex = 0xFF2563EB,
            sortOrder = 3
        ),
        CategoryEntity(
            id = "cat_exp_housing",
            name = "Rent & Utilities",
            type = CategoryType.EXPENSE,
            iconKey = "home",
            colorHex = 0xFF7C3AED,
            sortOrder = 4
        ),
        CategoryEntity(
            id = "cat_exp_health",
            name = "Healthcare",
            type = CategoryType.EXPENSE,
            iconKey = "medical_services",
            colorHex = 0xFFDC2626,
            sortOrder = 5
        ),
        CategoryEntity(
            id = "cat_exp_shopping",
            name = "Shopping",
            type = CategoryType.EXPENSE,
            iconKey = "shopping_bag",
            colorHex = 0xFFDB2777,
            sortOrder = 6
        ),
        CategoryEntity(
            id = "cat_exp_emi",
            name = "Loan EMI & Interest",
            type = CategoryType.EXPENSE,
            iconKey = "account_balance",
            colorHex = 0xFF1E6BFF,
            sortOrder = 7
        ),
        CategoryEntity(
            id = "cat_exp_bills",
            name = "Bills & Recharge",
            type = CategoryType.EXPENSE,
            iconKey = "receipt_long",
            colorHex = 0xFF0891B2,
            sortOrder = 8
        ),

        // Income Categories
        CategoryEntity(
            id = "cat_inc_salary",
            name = "Salary",
            type = CategoryType.INCOME,
            iconKey = "payments",
            colorHex = 0xFF059669,
            sortOrder = 1
        ),
        CategoryEntity(
            id = "cat_inc_business",
            name = "Business",
            type = CategoryType.INCOME,
            iconKey = "storefront",
            colorHex = 0xFF0D9488,
            sortOrder = 2
        ),
        CategoryEntity(
            id = "cat_inc_freelance",
            name = "Freelance",
            type = CategoryType.INCOME,
            iconKey = "work",
            colorHex = 0xFF1E6BFF,
            sortOrder = 3
        ),
        CategoryEntity(
            id = "cat_inc_reimbursement",
            name = "Reimbursement & Split",
            type = CategoryType.INCOME,
            iconKey = "group",
            colorHex = 0xFF4F46E5,
            sortOrder = 4
        ),
        CategoryEntity(
            id = "cat_inc_other",
            name = "Other Income",
            type = CategoryType.INCOME,
            iconKey = "trending_up",
            colorHex = 0xFF65A30D,
            sortOrder = 5
        )
    )
}

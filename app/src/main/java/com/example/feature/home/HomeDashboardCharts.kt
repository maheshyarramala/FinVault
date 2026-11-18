package com.example.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.MoneyPaise
import com.example.core.data.repository.MonthlyCashFlowBar
import com.example.core.designsystem.component.FinVaultCard
import com.example.core.designsystem.component.FinVaultEmptyStateCard
import com.example.core.designsystem.component.FinVaultStatusPill
import com.example.core.designsystem.theme.FinVaultNumericTypography
import com.example.core.designsystem.theme.LocalFinVaultColors
import java.util.Locale

@Composable
fun IncomeExpenseComparisonChartCard(
    bars: List<MonthlyCashFlowBar>,
    monthlyNet: MoneyPaise,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinVaultColors.current
    val hasAnyData = bars.any { it.income.paise > 0L || it.expense.paise > 0L }

    if (!hasAnyData) {
        FinVaultEmptyStateCard(
            icon = Icons.Filled.BarChart,
            title = "Income vs Expense Chart",
            body = "Post income or expenses to visualize your 6-month cash flow directly from the central ledger.",
            badgeLabel = "6-Month Trend",
            modifier = modifier.testTag("home_income_expense_chart_card")
        )
        return
    }

    val maxPaise = bars.maxOfOrNull { maxOf(it.income.paise, it.expense.paise) }
        ?.coerceAtLeast(1L) ?: 1L
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val incomeColor = semantic.income
    val expenseColor = semantic.expense

    FinVaultCard(modifier = modifier.testTag("home_income_expense_chart_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Income vs Expense",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Last ${bars.size} months • Ledger verified",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FinVaultStatusPill(
                text = "Net ${monthlyNet.formatInr()}",
                containerColor = if (monthlyNet.paise >= 0L) {
                    semantic.incomeContainer
                } else {
                    semantic.expenseContainer
                },
                contentColor = if (monthlyNet.paise >= 0L) {
                    semantic.income
                } else {
                    semantic.expense
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDotLabel(color = incomeColor, label = "Income")
            LegendDotLabel(color = expenseColor, label = "Expense")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .testTag("home_income_expense_canvas")
        ) {
            val count = bars.size.coerceAtLeast(1)
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.28f).coerceIn(6.dp.toPx(), 18.dp.toPx())
            val barGap = (slotWidth * 0.08f).coerceIn(2.dp.toPx(), 6.dp.toPx())
            val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

            // Horizontal reference lines
            listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                val y = size.height * (1f - fraction)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            bars.forEachIndexed { index, bar ->
                val centerX = slotWidth * index + slotWidth / 2f
                val incomeHeight = ((bar.income.paise.toFloat() / maxPaise.toFloat()) * size.height)
                    .coerceAtLeast(if (bar.income.paise > 0L) 4.dp.toPx() else 0f)
                val expenseHeight = ((bar.expense.paise.toFloat() / maxPaise.toFloat()) * size.height)
                    .coerceAtLeast(if (bar.expense.paise > 0L) 4.dp.toPx() else 0f)

                if (incomeHeight > 0f) {
                    drawRoundRect(
                        color = incomeColor,
                        topLeft = Offset(
                            x = centerX - barWidth - barGap / 2f,
                            y = size.height - incomeHeight
                        ),
                        size = Size(barWidth, incomeHeight),
                        cornerRadius = corner
                    )
                }
                if (expenseHeight > 0f) {
                    drawRoundRect(
                        color = expenseColor,
                        topLeft = Offset(
                            x = centerX + barGap / 2f,
                            y = size.height - expenseHeight
                        ),
                        size = Size(barWidth, expenseHeight),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CategoryDonutChartCard(
    slices: List<CategoryExpenseSlice>,
    totalMonthlyExpense: MoneyPaise,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty() || totalMonthlyExpense.paise <= 0L) {
        FinVaultEmptyStateCard(
            icon = Icons.Filled.DonutLarge,
            title = "Category Expense Breakdown",
            body = "Record expenses this month to inspect your spending distribution across categories.",
            badgeLabel = "Current Month",
            modifier = modifier.testTag("home_category_chart_card")
        )
        return
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val topSlices = slices.take(5)

    FinVaultCard(modifier = modifier.testTag("home_category_chart_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Expense Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${slices.size} active spending categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FinVaultStatusPill(text = totalMonthlyExpense.formatInr())
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(132.dp)
                        .testTag("home_category_donut_canvas")
                ) {
                    val strokePx = 18.dp.toPx()
                    val diameter = minOf(size.width, size.height) - strokePx
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx)
                    )

                    var startAngle = -90f
                    val activeSlices = slices.filter { (it.percentage / 100f) * 360f > 0.5f }
                    val gapDegrees = if (activeSlices.size > 1) 1.5f else 0f
                    activeSlices.forEach { slice ->
                        val sweep = (slice.percentage / 100f) * 360f
                        drawArc(
                            color = Color(slice.colorHex),
                            startAngle = startAngle,
                            sweepAngle = (sweep - gapDegrees).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalMonthlyExpense.formatInr(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topSlices.forEach { slice ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(slice.colorHex))
                            )
                            Text(
                                text = slice.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.0f", slice.percentage)}%",
                            style = FinVaultNumericTypography.rowAmount,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (slices.size > 5) {
                    Text(
                        text = "+${slices.size - 5} more categories",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDotLabel(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

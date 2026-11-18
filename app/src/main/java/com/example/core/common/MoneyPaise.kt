package com.example.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Type-safe representation of Indian Rupee currency stored strictly in integer paise
 * (1 INR = 100 paise) to prevent floating-point rounding errors across ledger operations.
 */
@JvmInline
value class MoneyPaise(val paise: Long) : Comparable<MoneyPaise> {

    operator fun plus(other: MoneyPaise): MoneyPaise = MoneyPaise(paise + other.paise)

    operator fun minus(other: MoneyPaise): MoneyPaise = MoneyPaise(paise - other.paise)

    operator fun unaryMinus(): MoneyPaise = MoneyPaise(-paise)

    override fun compareTo(other: MoneyPaise): Int = paise.compareTo(other.paise)

    val isZero: Boolean get() = paise == 0L
    val isPositive: Boolean get() = paise > 0L
    val isNegative: Boolean get() = paise < 0L

    fun abs(): MoneyPaise = MoneyPaise(kotlin.math.abs(paise))

    /**
     * Formats paise in Indian numbering system with 2 decimal places:
     * e.g. 12545075L -> "₹1,25,450.75", -50000L -> "-₹500.00".
     */
    fun formatInr(includeSymbol: Boolean = true, showPlusSign: Boolean = false): String {
        val sign = when {
            paise < 0L -> "-"
            showPlusSign && paise > 0L -> "+"
            else -> ""
        }
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remainderPaise = absPaise % 100L

        val formattedRupees = formatIndianGrouping(rupees)
        val formattedPaise = remainderPaise.toString().padStart(2, '0')
        val symbol = if (includeSymbol) "₹" else ""
        return "$sign$symbol$formattedRupees.$formattedPaise"
    }

    companion object {
        val ZERO = MoneyPaise(0L)

        fun fromRupees(rupees: Long): MoneyPaise = MoneyPaise(rupees * 100L)

        /**
         * Parses a decimal INR string (e.g. "1250.50", "1,25,000.75") safely via BigDecimal
         * into integer paise. Returns null if the input is malformed.
         */
        fun parseFromRupeeInput(rawInput: String): MoneyPaise? {
            val cleaned = rawInput
                .trim()
                .replace("₹", "")
                .replace(",", "")
                .replace(" ", "")
            if (cleaned.isEmpty()) return null
            return try {
                val decimal = BigDecimal(cleaned)
                val scaled = decimal
                    .multiply(BigDecimal(100))
                    .setScale(0, RoundingMode.HALF_UP)
                MoneyPaise(scaled.longValueExact())
            } catch (_: NumberFormatException) {
                null
            } catch (_: ArithmeticException) {
                null
            }
        }

        private fun formatIndianGrouping(value: Long): String {
            val digits = value.toString()
            if (digits.length <= 3) return digits

            val lastThree = digits.takeLast(3)
            val remaining = digits.dropLast(3)
            val groupedRemaining = StringBuilder()
            var count = 0
            for (i in remaining.length - 1 downTo 0) {
                groupedRemaining.append(remaining[i])
                count++
                if (count == 2 && i > 0) {
                    groupedRemaining.append(',')
                    count = 0
                }
            }
            return "${groupedRemaining.reverse()},$lastThree"
        }
    }
}

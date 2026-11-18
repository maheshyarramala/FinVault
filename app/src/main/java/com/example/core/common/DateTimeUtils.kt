package com.example.core.common

import com.example.core.database.entity.RecurringFrequency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Centralized date and time helpers ensuring consistent epoch-millisecond storage
 * and India-friendly formatting across the ledger, calculators, budgets, and reports.
 */
object DateTimeUtils {

    private val indiaLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun formatShortDate(epochMs: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy", indiaLocale)
        return formatter.format(Date(epochMs))
    }

    fun formatDateTime(epochMs: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", indiaLocale)
        return formatter.format(Date(epochMs))
    }

    fun formatMonthYear(epochMs: Long): String {
        val formatter = SimpleDateFormat("MMMM yyyy", indiaLocale)
        return formatter.format(Date(epochMs))
    }

    fun monthKey(epochMs: Long = nowEpochMs()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun formatMonthKeyLabel(monthKey: String): String {
        val (start, _) = monthRangeFromKey(monthKey)
        return formatMonthYear(start)
    }

    fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
        val parts = monthKey.split("-")
        val year = parts[0].toInt()
        val monthIndex = parts[1].toInt() - 1
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, deltaMonths)
        }
        return monthKey(cal.timeInMillis)
    }

    fun monthRangeFromKey(monthKey: String): Pair<Long, Long> {
        val parts = monthKey.split("-")
        val year = parts[0].toInt()
        val monthIndex = parts[1].toInt() - 1
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthIndex)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.MONTH, 1)
        }.timeInMillis - 1L

        return start to end
    }

    fun currentMonthRange(nowMs: Long = nowEpochMs()): Pair<Long, Long> =
        monthRangeFromKey(monthKey(nowMs))

    fun dayRange(nowMs: Long = nowEpochMs()): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis - 1L
        return start to end
    }

    fun last7DaysRange(nowMs: Long = nowEpochMs()): Pair<Long, Long> {
        val (_, dayEnd) = dayRange(nowMs)
        val start = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return start to dayEnd
    }

    fun currentYearRange(nowMs: Long = nowEpochMs()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val year = cal.get(Calendar.YEAR)
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.YEAR, 1)
        }.timeInMillis - 1L
        return start to end
    }

    fun formatShortMonthLabel(monthKey: String): String {
        val (start, _) = monthRangeFromKey(monthKey)
        val formatter = SimpleDateFormat("MMM", indiaLocale)
        return formatter.format(Date(start))
    }

    fun nextDayOfMonthEpochMs(dayOfMonth: Int, fromEpochMs: Long = nowEpochMs()): Long {
        val safeDay = dayOfMonth.coerceIn(1, 28)
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromEpochMs
            set(Calendar.DAY_OF_MONTH, safeDay)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis < fromEpochMs) {
                add(Calendar.MONTH, 1)
            }
        }
        return cal.timeInMillis
    }

    fun advanceByFrequency(epochMs: Long, frequency: RecurringFrequency): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        when (frequency) {
            RecurringFrequency.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RecurringFrequency.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RecurringFrequency.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RecurringFrequency.YEARLY -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }
}

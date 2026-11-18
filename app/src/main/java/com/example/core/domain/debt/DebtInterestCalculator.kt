package com.example.core.domain.debt

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Deterministic, paise-safe interest calculations for personal debts.
 *
 * Rates are basis points (100 bps = 1%). Compound interest compounds for each
 * completed compounding period; the remaining days accrue simple interest on
 * the compounded balance. This convention is explicit and avoids floating point.
 */
object DebtInterestCalculator {

    enum class InterestType { NONE, SIMPLE, COMPOUND, FIXED }
    enum class CompoundingFrequency(val periodsPerYear: Int) {
        MONTHLY(12), QUARTERLY(4), HALF_YEARLY(2), YEARLY(1)
    }

    data class Terms(
        val type: InterestType,
        val annualRateBps: Int = 0,
        val fixedInterestPaise: Long = 0L,
        val frequency: CompoundingFrequency = CompoundingFrequency.MONTHLY
    ) {
        init {
            require(annualRateBps in 0..100_000) { "Annual rate must be between 0% and 1000%" }
            require(fixedInterestPaise >= 0L) { "Fixed interest cannot be negative" }
        }
    }

    data class Accrual(
        val principalPaise: Long,
        val interestPaise: Long,
        val totalDuePaise: Long,
        val elapsedDays: Long
    )

    fun calculate(
        principalPaise: Long,
        terms: Terms,
        startDate: LocalDate,
        asOfDate: LocalDate
    ): Accrual {
        require(principalPaise >= 0L) { "Principal cannot be negative" }
        require(!asOfDate.isBefore(startDate)) { "As-of date cannot be before start date" }
        val days = ChronoUnit.DAYS.between(startDate, asOfDate)
        val interest = when (terms.type) {
            InterestType.NONE -> 0L
            InterestType.FIXED -> terms.fixedInterestPaise
            InterestType.SIMPLE -> simpleInterest(principalPaise, terms.annualRateBps, days)
            InterestType.COMPOUND -> compoundInterest(
                principalPaise, terms.annualRateBps, days, terms.frequency.periodsPerYear
            )
        }
        return Accrual(
            principalPaise = principalPaise,
            interestPaise = interest,
            totalDuePaise = Math.addExact(principalPaise, interest),
            elapsedDays = days
        )
    }

    private fun simpleInterest(principal: Long, rateBps: Int, days: Long): Long {
        if (principal == 0L || rateBps == 0 || days == 0L) return 0L
        return BigDecimal.valueOf(principal)
            .multiply(BigDecimal.valueOf(rateBps.toLong()))
            .multiply(BigDecimal.valueOf(days))
            .divide(BigDecimal.valueOf(10_000L * 365L), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    private fun compoundInterest(
        principal: Long,
        rateBps: Int,
        days: Long,
        periodsPerYear: Int
    ): Long {
        if (principal == 0L || rateBps == 0 || days == 0L) return 0L
        val periodDays = 365L / periodsPerYear
        val completedPeriods = days / periodDays
        val remainingDays = days % periodDays
        val periodRate = BigDecimal.valueOf(rateBps.toLong())
            .divide(BigDecimal.valueOf(10_000L * periodsPerYear), 16, RoundingMode.HALF_UP)
        var balance = BigDecimal.valueOf(principal)
        val multiplier = BigDecimal.ONE.add(periodRate)
        if (completedPeriods > 0L) {
            // Exponentiation by squaring avoids a loop for long-running debts.
            var exponent = completedPeriods
            var factor = multiplier
            var power = BigDecimal.ONE
            while (exponent > 0L) {
                if ((exponent and 1L) == 1L) power = power.multiply(factor)
                exponent = exponent ushr 1
                if (exponent > 0L) factor = factor.multiply(factor)
            }
            balance = balance.multiply(power)
        }
        if (remainingDays > 0L) {
            val residualRate = BigDecimal.valueOf(rateBps.toLong())
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(10_000L * 365L), 16, RoundingMode.HALF_UP)
            balance = balance.multiply(BigDecimal.ONE.add(residualRate))
        }
        val total = balance.setScale(0, RoundingMode.HALF_UP).longValueExact()
        return (total - principal).coerceAtLeast(0L)
    }
}

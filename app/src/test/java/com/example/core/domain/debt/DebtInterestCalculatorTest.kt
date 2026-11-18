package com.example.core.domain.debt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class DebtInterestCalculatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test fun `no interest returns principal only`() {
        val result = DebtInterestCalculator.calculate(
            100_000L, DebtInterestCalculator.Terms(DebtInterestCalculator.InterestType.NONE),
            start, start.plusDays(365)
        )
        assertEquals(0L, result.interestPaise)
        assertEquals(100_000L, result.totalDuePaise)
    }

    @Test fun `simple interest is calculated in integer paise`() {
        val result = DebtInterestCalculator.calculate(
            100_000L,
            DebtInterestCalculator.Terms(DebtInterestCalculator.InterestType.SIMPLE, annualRateBps = 1_200),
            start, start.plusDays(365)
        )
        assertEquals(12_000L, result.interestPaise)
        assertEquals(112_000L, result.totalDuePaise)
    }

    @Test fun `fixed interest is not time dependent`() {
        val terms = DebtInterestCalculator.Terms(
            DebtInterestCalculator.InterestType.FIXED, fixedInterestPaise = 7_500L
        )
        val first = DebtInterestCalculator.calculate(50_000L, terms, start, start.plusDays(1))
        val later = DebtInterestCalculator.calculate(50_000L, terms, start, start.plusDays(900))
        assertEquals(7_500L, first.interestPaise)
        assertEquals(first.interestPaise, later.interestPaise)
    }

    @Test fun `compound interest is at least simple interest for a full year`() {
        val terms = DebtInterestCalculator.Terms(
            DebtInterestCalculator.InterestType.COMPOUND,
            annualRateBps = 1_200,
            frequency = DebtInterestCalculator.CompoundingFrequency.MONTHLY
        )
        val result = DebtInterestCalculator.calculate(100_000L, terms, start, start.plusDays(365))
        assertEquals(12_683L, result.interestPaise)
    }

    @Test fun `invalid dates and negative principal are rejected`() {
        val terms = DebtInterestCalculator.Terms(DebtInterestCalculator.InterestType.NONE)
        assertThrows(IllegalArgumentException::class.java) {
            DebtInterestCalculator.calculate(-1L, terms, start, start)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebtInterestCalculator.calculate(1L, terms, start, start.minusDays(1))
        }
    }
}

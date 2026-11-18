package com.example.core.domain.split

import com.example.core.database.entity.SplitExpenseEntity
import com.example.core.database.entity.SplitExpenseShareEntity
import com.example.core.database.entity.SplitMethod
import com.example.core.database.entity.SplitParticipantEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun `equal split distributes remainder paise deterministically with exact sum`() {
        val shares = SplitCalculator.calculateShares(
            totalAmountPaise = 100_00L, // ₹100.00 = 10000 paise / 3
            spec = SplitInputSpec.Equal(listOf("me", "aarav", "priya"))
        )
        assertEquals(listOf(3334L, 3333L, 3333L), shares.map { it.shareAmountPaise })
        assertEquals(100_00L, shares.sumOf { it.shareAmountPaise })
    }

    @Test
    fun `unequal amount split accepts exact sum and rejects mismatched sum`() {
        val shares = SplitCalculator.calculateShares(
            totalAmountPaise = 1500_00L,
            spec = SplitInputSpec.UnequalAmounts(
                participantIds = listOf("me", "aarav", "priya"),
                amountsPaise = mapOf("me" to 500_00L, "aarav" to 700_00L, "priya" to 300_00L)
            )
        )
        assertEquals(1500_00L, shares.sumOf { it.shareAmountPaise })

        val failure = runCatching {
            SplitCalculator.calculateShares(
                totalAmountPaise = 1500_00L,
                spec = SplitInputSpec.UnequalAmounts(
                    participantIds = listOf("me", "aarav"),
                    amountsPaise = mapOf("me" to 500_00L, "aarav" to 900_00L)
                )
            )
        }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `percentage split handles decimal bps with Hamilton remainder and rejects non-100 total`() {
        val bps1 = SplitCalculator.parsePercentToBps("33.33")!!
        val bps2 = SplitCalculator.parsePercentToBps("33.33")!!
        val bps3 = SplitCalculator.parsePercentToBps("33.34")!!
        val shares = SplitCalculator.calculateShares(
            totalAmountPaise = 1000_00L,
            spec = SplitInputSpec.Percentages(
                participantIds = listOf("me", "aarav", "priya"),
                percentagesBps = mapOf("me" to bps1, "aarav" to bps2, "priya" to bps3)
            )
        )
        assertEquals(1000_00L, shares.sumOf { it.shareAmountPaise })
        assertEquals(33330L, shares[0].shareAmountPaise)
        assertEquals(33330L, shares[1].shareAmountPaise)
        assertEquals(33340L, shares[2].shareAmountPaise)

        val badPct = runCatching {
            SplitCalculator.calculateShares(
                totalAmountPaise = 1000_00L,
                spec = SplitInputSpec.Percentages(
                    participantIds = listOf("me", "aarav"),
                    percentagesBps = mapOf("me" to 6000, "aarav" to 3000)
                )
            )
        }
        assertTrue(badPct.isFailure)
    }

    @Test
    fun `custom shares allocate fractional weights proportionally with exact paise conservation`() {
        val shares = SplitCalculator.calculateShares(
            totalAmountPaise = 1000_00L, // ₹1,000 across 2 : 1.5 : 0.5 = total 4.0 shares
            spec = SplitInputSpec.CustomShares(
                participantIds = listOf("me", "aarav", "priya"),
                unitsScaled = mapOf(
                    "me" to SplitCalculator.parseCustomSharesScaled("2")!!,
                    "aarav" to SplitCalculator.parseCustomSharesScaled("1.5")!!,
                    "priya" to SplitCalculator.parseCustomSharesScaled("0.5")!!
                )
            )
        )
        assertEquals(listOf(500_00L, 375_00L, 125_00L), shares.map { it.shareAmountPaise })
        assertEquals(1000_00L, shares.sumOf { it.shareAmountPaise })

        // Prime total against 3 : 2 : 2 weights
        val tricky = SplitCalculator.calculateShares(
            totalAmountPaise = 101_00L,
            spec = SplitInputSpec.CustomShares(
                participantIds = listOf("a", "b", "c"),
                unitsScaled = mapOf("a" to 300L, "b" to 200L, "c" to 200L)
            )
        )
        assertEquals(101_00L, tricky.sumOf { it.shareAmountPaise })
    }

    @Test
    fun `simplified settlements minimize multi-party transfers accurately`() {
        val now = 1000L
        val me = SplitParticipantEntity("p_me", "g1", "Me", isCurrentUser = true, createdAtEpochMs = now, updatedAtEpochMs = now)
        val aarav = SplitParticipantEntity("p_a", "g1", "Aarav", isCurrentUser = false, createdAtEpochMs = now, updatedAtEpochMs = now)
        val priya = SplitParticipantEntity("p_p", "g1", "Priya", isCurrentUser = false, createdAtEpochMs = now, updatedAtEpochMs = now)

        // Me paid ₹900 split equally (₹300 each): Me +₹600, Aarav -₹300, Priya -₹300
        // Aarav paid ₹300 split equally (₹100 each): Me +₹500, Aarav -₹100, Priya -₹400
        val e1 = SplitExpenseEntity(
            id = "e1",
            groupId = "g1",
            idempotencyKey = "k1",
            description = "Hotel",
            totalAmountPaise = 900_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = me.id,
            paidFromAccountId = "acc1",
            ledgerTransactionId = "tx1",
            splitMethod = SplitMethod.EQUAL,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val e2 = SplitExpenseEntity(
            id = "e2",
            groupId = "g1",
            idempotencyKey = "k2",
            description = "Taxi",
            totalAmountPaise = 300_00L,
            expenseDateEpochMs = now,
            paidByParticipantId = aarav.id,
            paidFromAccountId = null,
            ledgerTransactionId = null,
            splitMethod = SplitMethod.EQUAL,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val shares = listOf(
            SplitExpenseShareEntity("s1", "e1", me.id, 300_00L),
            SplitExpenseShareEntity("s2", "e1", aarav.id, 300_00L),
            SplitExpenseShareEntity("s3", "e1", priya.id, 300_00L),
            SplitExpenseShareEntity("s4", "e2", me.id, 100_00L),
            SplitExpenseShareEntity("s5", "e2", aarav.id, 100_00L),
            SplitExpenseShareEntity("s6", "e2", priya.id, 100_00L)
        )

        val summary = SplitCalculator.computeGroupSummary(
            participants = listOf(me, aarav, priya),
            expenses = listOf(e1, e2),
            shares = shares,
            settlements = emptyList(),
            fallbackLastActivityMs = now
        )

        assertEquals(1200_00L, summary.totalExpensesPaise)
        assertEquals(500_00L, summary.currentUserYouAreOwedPaise)
        assertEquals(0L, summary.currentUserYouOwePaise)
        assertEquals(500_00L, summary.groupUnsettledAmountPaise)

        // Minimum 2 transfers: Priya -> Me ₹400, Aarav -> Me ₹100
        assertEquals(2, summary.simplifiedSettlements.size)
        assertEquals(500_00L, summary.simplifiedSettlements.sumOf { it.amountPaise })
        assertTrue(summary.simplifiedSettlements.all { it.toParticipant.id == me.id })
    }
}

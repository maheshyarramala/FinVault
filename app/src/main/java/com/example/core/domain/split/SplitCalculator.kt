package com.example.core.domain.split

import com.example.core.database.entity.SplitExpenseEntity
import com.example.core.database.entity.SplitExpenseShareEntity
import com.example.core.database.entity.SplitMethod
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs

data class ComputedSplitShare(
    val participantId: String,
    val shareAmountPaise: Long,
    val percentageBps: Int? = null,
    val customUnitsScaled: Long? = null
)

sealed interface SplitInputSpec {
    val participantIds: List<String>

    data class Equal(
        override val participantIds: List<String>
    ) : SplitInputSpec

    data class UnequalAmounts(
        override val participantIds: List<String>,
        val amountsPaise: Map<String, Long>
    ) : SplitInputSpec

    data class Percentages(
        override val participantIds: List<String>,
        val percentagesBps: Map<String, Int>
    ) : SplitInputSpec

    data class CustomShares(
        override val participantIds: List<String>,
        val unitsScaled: Map<String, Long>
    ) : SplitInputSpec

    fun splitMethod(): SplitMethod = when (this) {
        is Equal -> SplitMethod.EQUAL
        is UnequalAmounts -> SplitMethod.UNEQUAL_AMOUNT
        is Percentages -> SplitMethod.PERCENTAGE
        is CustomShares -> SplitMethod.CUSTOM_SHARE
    }
}

data class ParticipantBalanceSummary(
    val participant: SplitParticipantEntity,
    val totalPaidExpensesPaise: Long,
    val totalExpenseSharePaise: Long,
    val totalSettlementsPaidPaise: Long,
    val totalSettlementsReceivedPaise: Long,
    val netBalancePaise: Long
) {
    val amountToReceivePaise: Long
        get() = netBalancePaise.coerceAtLeast(0L)

    val amountOwedPaise: Long
        get() = (-netBalancePaise).coerceAtLeast(0L)
}

data class SuggestedSettlement(
    val fromParticipant: SplitParticipantEntity,
    val toParticipant: SplitParticipantEntity,
    val amountPaise: Long
)

data class GroupLedgerComputedSummary(
    val participantBalances: List<ParticipantBalanceSummary>,
    val simplifiedSettlements: List<SuggestedSettlement>,
    val totalExpensesPaise: Long,
    val totalSettlementsPaise: Long,
    val groupUnsettledAmountPaise: Long,
    val currentUserNetBalancePaise: Long,
    val currentUserYouOwePaise: Long,
    val currentUserYouAreOwedPaise: Long,
    val lastActivityEpochMs: Long
)

/**
 * Pure deterministic integer-paise Split Bill calculation and debt-simplification engine.
 * Guarantees zero paise drift across EQUAL, UNEQUAL_AMOUNT, PERCENTAGE, and CUSTOM_SHARE modes.
 */
object SplitCalculator {

    const val FULL_PERCENT_BPS = 10_000

    fun parsePercentToBps(input: String): Int? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isBlank()) return null
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        if (bd < BigDecimal.ZERO || bd > BigDecimal(100)) return null
        val scaled = bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        val bps = scaled.toInt()
        return if (bps in 0..FULL_PERCENT_BPS) bps else null
    }

    fun formatBpsAsPercent(bps: Int): String {
        val whole = bps / 100
        val frac = bps % 100
        return if (frac == 0) {
            "$whole%"
        } else {
            String.format(java.util.Locale.US, "%d.%02d%%", whole, frac).replace("0%", "%")
        }
    }

    fun parseCustomSharesScaled(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isBlank()) return null
        val bd = cleaned.toBigDecimalOrNull() ?: return null
        if (bd < BigDecimal.ZERO) return null
        return bd.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun calculateShares(
        totalAmountPaise: Long,
        spec: SplitInputSpec
    ): List<ComputedSplitShare> {
        require(totalAmountPaise > 0L) { "Expense amount must be greater than ₹0.00" }
        val participants = spec.participantIds.distinct()
        require(participants.isNotEmpty()) { "Select at least one participant" }
        require(participants.size == spec.participantIds.size) {
            "Duplicate participant in split selection"
        }

        val computed = when (spec) {
            is SplitInputSpec.Equal -> splitEqually(totalAmountPaise, participants)
            is SplitInputSpec.UnequalAmounts -> splitUnequal(totalAmountPaise, participants, spec.amountsPaise)
            is SplitInputSpec.Percentages -> splitByPercentages(totalAmountPaise, participants, spec.percentagesBps)
            is SplitInputSpec.CustomShares -> splitByCustomShares(totalAmountPaise, participants, spec.unitsScaled)
        }

        val shareSum = computed.sumOf { it.shareAmountPaise }
        check(shareSum == totalAmountPaise) {
            "Calculated split shares ($shareSum) do not match total ($totalAmountPaise)"
        }
        return computed
    }

    fun splitEqually(
        totalAmountPaise: Long,
        participantIds: List<String>
    ): List<ComputedSplitShare> {
        require(totalAmountPaise > 0L) { "Expense amount must be positive" }
        require(participantIds.isNotEmpty()) { "Select at least one participant" }
        val n = participantIds.size.toLong()
        val base = totalAmountPaise / n
        val remainder = (totalAmountPaise % n).toInt()
        return participantIds.mapIndexed { index, id ->
            val share = base + if (index < remainder) 1L else 0L
            ComputedSplitShare(participantId = id, shareAmountPaise = share)
        }
    }

    fun splitUnequal(
        totalAmountPaise: Long,
        participantIds: List<String>,
        amountsPaise: Map<String, Long>
    ): List<ComputedSplitShare> {
        require(totalAmountPaise > 0L) { "Expense amount must be positive" }
        require(participantIds.isNotEmpty()) { "Select at least one participant" }
        val shares = participantIds.map { id ->
            val amt = amountsPaise[id] ?: -1L
            require(amt >= 0L) { "Split amount cannot be negative for participant" }
            ComputedSplitShare(participantId = id, shareAmountPaise = amt)
        }
        val sum = shares.sumOf { it.shareAmountPaise }
        require(sum == totalAmountPaise) {
            "Participant amounts must sum to exact total ($totalAmountPaise paise, got $sum paise)"
        }
        return shares
    }

    fun splitByPercentages(
        totalAmountPaise: Long,
        participantIds: List<String>,
        percentagesBps: Map<String, Int>
    ): List<ComputedSplitShare> {
        require(totalAmountPaise > 0L) { "Expense amount must be positive" }
        require(participantIds.isNotEmpty()) { "Select at least one participant" }
        val weights = participantIds.map { id ->
            val bps = percentagesBps[id] ?: -1
            require(bps >= 0) { "Percentage cannot be negative" }
            id to bps
        }
        val totalBps = weights.sumOf { it.second }
        require(totalBps == FULL_PERCENT_BPS) {
            "Percentages must total 100.00% (currently ${formatBpsAsPercent(totalBps)})"
        }

        val allocated = allocateByWeights(
            totalAmountPaise = totalAmountPaise,
            weights = weights.map { it.first to it.second.toLong() }
        )
        return weights.mapIndexed { idx, (id, bps) ->
            ComputedSplitShare(
                participantId = id,
                shareAmountPaise = allocated[idx],
                percentageBps = bps
            )
        }
    }

    fun splitByCustomShares(
        totalAmountPaise: Long,
        participantIds: List<String>,
        unitsScaled: Map<String, Long>
    ): List<ComputedSplitShare> {
        require(totalAmountPaise > 0L) { "Expense amount must be positive" }
        require(participantIds.isNotEmpty()) { "Select at least one participant" }
        val weights = participantIds.map { id ->
            val unit = unitsScaled[id] ?: -1L
            require(unit >= 0L) { "Custom share weight cannot be negative" }
            id to unit
        }
        val totalWeight = weights.sumOf { it.second }
        require(totalWeight > 0L) { "Total custom shares must be greater than zero" }

        val allocated = allocateByWeights(
            totalAmountPaise = totalAmountPaise,
            weights = weights
        )
        return weights.mapIndexed { idx, (id, unit) ->
            ComputedSplitShare(
                participantId = id,
                shareAmountPaise = allocated[idx],
                customUnitsScaled = unit
            )
        }
    }

    /**
     * Largest Remainder (Hamilton) allocation in BigInteger so sum == totalAmountPaise with zero overflow.
     */
    private fun allocateByWeights(
        totalAmountPaise: Long,
        weights: List<Pair<String, Long>>
    ): List<Long> {
        val totalWeight = weights.sumOf { it.second }
        require(totalWeight > 0L) { "Total split weight must be positive" }

        val totalBig = BigInteger.valueOf(totalAmountPaise)
        val totalWeightBig = BigInteger.valueOf(totalWeight)

        val baseShares = LongArray(weights.size)
        val remainders = Array(weights.size) { index ->
            val wBig = BigInteger.valueOf(weights[index].second)
            val numerator = totalBig.multiply(wBig)
            val floorVal = numerator.divide(totalWeightBig).longValueExact()
            val rem = numerator.remainder(totalWeightBig)
            baseShares[index] = floorVal
            index to rem
        }

        val allocatedSum = baseShares.sum()
        val leftover = (totalAmountPaise - allocatedSum).toInt()
        if (leftover > 0) {
            remainders.sortWith(
                compareByDescending<Pair<Int, BigInteger>> { it.second }
                    .thenBy { it.first }
            )
            for (i in 0 until leftover) {
                val targetIdx = remainders[i].first
                baseShares[targetIdx] = baseShares[targetIdx] + 1L
            }
        }
        return baseShares.toList()
    }

    fun computeGroupSummary(
        participants: List<SplitParticipantEntity>,
        expenses: List<SplitExpenseEntity>,
        shares: List<SplitExpenseShareEntity>,
        settlements: List<SplitSettlementEntity>,
        fallbackLastActivityMs: Long
    ): GroupLedgerComputedSummary {
        val activeExpenses = expenses.filter { !it.isDeleted }
        val activeExpenseIds = activeExpenses.mapTo(HashSet()) { it.id }
        val activeShares = shares.filter { it.expenseId in activeExpenseIds }
        val activeSettlements = settlements.filter { !it.isDeleted }

        val paidExpensesByParticipant = mutableMapOf<String, Long>()
        for (exp in activeExpenses) {
            paidExpensesByParticipant[exp.paidByParticipantId] =
                (paidExpensesByParticipant[exp.paidByParticipantId] ?: 0L) + exp.totalAmountPaise
        }

        val shareByParticipant = mutableMapOf<String, Long>()
        for (share in activeShares) {
            shareByParticipant[share.participantId] =
                (shareByParticipant[share.participantId] ?: 0L) + share.shareAmountPaise
        }

        val settlementsPaidByParticipant = mutableMapOf<String, Long>()
        val settlementsReceivedByParticipant = mutableMapOf<String, Long>()
        for (s in activeSettlements) {
            settlementsPaidByParticipant[s.fromParticipantId] =
                (settlementsPaidByParticipant[s.fromParticipantId] ?: 0L) + s.amountPaise
            settlementsReceivedByParticipant[s.toParticipantId] =
                (settlementsReceivedByParticipant[s.toParticipantId] ?: 0L) + s.amountPaise
        }

        val balances = participants.map { p ->
            val paid = paidExpensesByParticipant[p.id] ?: 0L
            val owedShare = shareByParticipant[p.id] ?: 0L
            val sent = settlementsPaidByParticipant[p.id] ?: 0L
            val received = settlementsReceivedByParticipant[p.id] ?: 0L
            val net = paid + sent - owedShare - received
            ParticipantBalanceSummary(
                participant = p,
                totalPaidExpensesPaise = paid,
                totalExpenseSharePaise = owedShare,
                totalSettlementsPaidPaise = sent,
                totalSettlementsReceivedPaise = received,
                netBalancePaise = net
            )
        }

        val simplified = computeSimplifiedSettlements(balances)
        val groupUnsettled = balances.sumOf { it.amountToReceivePaise }
        val currentUserBalance = balances
            .filter { it.participant.isCurrentUser }
            .sumOf { it.netBalancePaise }

        val maxExpDate = activeExpenses.maxOfOrNull { it.updatedAtEpochMs.coerceAtLeast(it.expenseDateEpochMs) } ?: 0L
        val maxSetDate = activeSettlements.maxOfOrNull { it.updatedAtEpochMs.coerceAtLeast(it.settlementDateEpochMs) } ?: 0L
        val latestActivity = maxOf(fallbackLastActivityMs, maxExpDate, maxSetDate)

        return GroupLedgerComputedSummary(
            participantBalances = balances,
            simplifiedSettlements = simplified,
            totalExpensesPaise = activeExpenses.sumOf { it.totalAmountPaise },
            totalSettlementsPaise = activeSettlements.sumOf { it.amountPaise },
            groupUnsettledAmountPaise = groupUnsettled,
            currentUserNetBalancePaise = currentUserBalance,
            currentUserYouOwePaise = (-currentUserBalance).coerceAtLeast(0L),
            currentUserYouAreOwedPaise = currentUserBalance.coerceAtLeast(0L),
            lastActivityEpochMs = latestActivity
        )
    }

    /**
     * Greedy minimum-transfer debt simplification. Does not mutate stored settlement records.
     */
    fun computeSimplifiedSettlements(
        balances: List<ParticipantBalanceSummary>
    ): List<SuggestedSettlement> {
        data class MutableParticipantAmount(
            val participant: SplitParticipantEntity,
            var remainingPaise: Long
        )

        val debtors = balances
            .filter { it.netBalancePaise < 0L }
            .map { MutableParticipantAmount(it.participant, abs(it.netBalancePaise)) }
            .sortedWith(
                compareByDescending<MutableParticipantAmount> { it.remainingPaise }
                    .thenBy { it.participant.name.lowercase() }
                    .thenBy { it.participant.id }
            )
            .toMutableList()

        val creditors = balances
            .filter { it.netBalancePaise > 0L }
            .map { MutableParticipantAmount(it.participant, it.netBalancePaise) }
            .sortedWith(
                compareByDescending<MutableParticipantAmount> { it.remainingPaise }
                    .thenBy { it.participant.name.lowercase() }
                    .thenBy { it.participant.id }
            )
            .toMutableList()

        val suggestions = mutableListOf<SuggestedSettlement>()
        var dIdx = 0
        var cIdx = 0
        while (dIdx < debtors.size && cIdx < creditors.size) {
            val debtor = debtors[dIdx]
            val creditor = creditors[cIdx]
            val transfer = minOf(debtor.remainingPaise, creditor.remainingPaise)
            if (transfer > 0L) {
                suggestions.add(
                    SuggestedSettlement(
                        fromParticipant = debtor.participant,
                        toParticipant = creditor.participant,
                        amountPaise = transfer
                    )
                )
                debtor.remainingPaise -= transfer
                creditor.remainingPaise -= transfer
            }
            if (debtor.remainingPaise <= 0L) dIdx++
            if (creditor.remainingPaise <= 0L) cIdx++
        }
        return suggestions
    }
}

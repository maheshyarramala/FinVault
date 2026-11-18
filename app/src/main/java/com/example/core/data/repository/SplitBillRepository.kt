package com.example.core.data.repository

import androidx.room.withTransaction
import com.example.core.common.DateTimeUtils
import com.example.core.common.MoneyPaise
import com.example.core.database.FinVaultDatabase
import com.example.core.database.dao.AccountDao
import com.example.core.database.dao.SplitBillDao
import com.example.core.database.dao.SplitExpenseWithShares
import com.example.core.database.entity.AccountEntity
import com.example.core.database.entity.AccountType
import com.example.core.database.entity.PaymentMethod
import com.example.core.database.entity.SettlementDirection
import com.example.core.database.entity.SplitExpenseEntity
import com.example.core.database.entity.SplitExpenseShareEntity
import com.example.core.database.entity.SplitGroupEntity
import com.example.core.database.entity.SplitParticipantEntity
import com.example.core.database.entity.SplitSettlementEntity
import com.example.core.domain.ledger.CentralLedgerEngine
import com.example.core.domain.ledger.LedgerPostSpec
import com.example.core.domain.split.GroupLedgerComputedSummary
import com.example.core.domain.split.SplitCalculator
import com.example.core.domain.split.SplitInputSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

data class SplitGroupOverview(
    val group: SplitGroupEntity,
    val participants: List<SplitParticipantEntity>,
    val activeMemberCount: Int,
    val totalExpenses: MoneyPaise,
    val youOwe: MoneyPaise,
    val youAreOwed: MoneyPaise,
    val netBalance: MoneyPaise,
    val unsettledAmount: MoneyPaise,
    val lastActivityEpochMs: Long,
    val computedSummary: GroupLedgerComputedSummary
)

data class SplitBillPortfolioSummary(
    val totalGroupExpenses: MoneyPaise,
    val youOwe: MoneyPaise,
    val youAreOwed: MoneyPaise,
    val unsettledAmount: MoneyPaise,
    val paidByUserTotal: MoneyPaise,
    val userPersonalShareTotal: MoneyPaise,
    val reimbursementsReceivedTotal: MoneyPaise,
    val outgoingSettlementsTotal: MoneyPaise,
    val activeGroupCount: Int,
    val groupOverviews: List<SplitGroupOverview>
)

data class SplitGroupDetailSnapshot(
    val overview: SplitGroupOverview,
    val expenses: List<SplitExpenseWithShares>,
    val settlements: List<SplitSettlementEntity>
)

class SplitBillRepository(
    private val database: FinVaultDatabase,
    private val splitBillDao: SplitBillDao,
    private val accountDao: AccountDao,
    private val ledgerEngine: CentralLedgerEngine
) {
    /**
     * Live aggregate across all Split Bill groups.
     * Note: active portfolio cards aggregate active groups; Reports aggregates historical paid-by-user,
     * user shares, and reimbursements without double counting.
     */
    val portfolioSummary: Flow<SplitBillPortfolioSummary> = combine(
        splitBillDao.observeAllGroups(),
        splitBillDao.observeAllParticipants(),
        splitBillDao.observeAllActiveExpensesWithShares(),
        splitBillDao.observeAllActiveSettlements()
    ) { groups, participants, expensesWithShares, settlements ->
        val participantsByGroup = participants.groupBy { it.groupId }
        val expensesByGroup = expensesWithShares.groupBy { it.expense.groupId }
        val settlementsByGroup = settlements.groupBy { it.groupId }

        val overviews = groups.map { group ->
            val groupParticipants = participantsByGroup[group.id].orEmpty()
            val groupExpenseItems = expensesByGroup[group.id].orEmpty()
            val groupExpenses = groupExpenseItems.map { it.expense }
            val groupShares = groupExpenseItems.flatMap { it.shares }
            val groupSettlements = settlementsByGroup[group.id].orEmpty()

            val computed = SplitCalculator.computeGroupSummary(
                participants = groupParticipants,
                expenses = groupExpenses,
                shares = groupShares,
                settlements = groupSettlements,
                fallbackLastActivityMs = group.lastActivityEpochMs
            )

            SplitGroupOverview(
                group = group,
                participants = groupParticipants,
                activeMemberCount = groupParticipants.count { !it.isArchived },
                totalExpenses = MoneyPaise(computed.totalExpensesPaise),
                youOwe = MoneyPaise(computed.currentUserYouOwePaise),
                youAreOwed = MoneyPaise(computed.currentUserYouAreOwedPaise),
                netBalance = MoneyPaise(computed.currentUserNetBalancePaise),
                unsettledAmount = MoneyPaise(computed.groupUnsettledAmountPaise),
                lastActivityEpochMs = computed.lastActivityEpochMs,
                computedSummary = computed
            )
        }

        val activeOverviews = overviews.filter { !it.group.isArchived }
        val meParticipantIds = participants.filter { it.isCurrentUser }.mapTo(HashSet()) { it.id }

        val paidByUser = expensesWithShares
            .filter { it.expense.paidByParticipantId in meParticipantIds }
            .sumOf { it.expense.totalAmountPaise }
        val userShareTotal = expensesWithShares
            .flatMap { it.shares }
            .filter { it.participantId in meParticipantIds }
            .sumOf { it.shareAmountPaise }
        val reimbursementsReceived = settlements
            .filter { it.direction == SettlementDirection.INCOMING_REIMBURSEMENT }
            .sumOf { it.amountPaise }
        val outgoingSettlements = settlements
            .filter { it.direction == SettlementDirection.OUTGOING_PAYMENT }
            .sumOf { it.amountPaise }

        SplitBillPortfolioSummary(
            totalGroupExpenses = MoneyPaise(activeOverviews.sumOf { it.totalExpenses.paise }),
            youOwe = MoneyPaise(activeOverviews.sumOf { it.youOwe.paise }),
            youAreOwed = MoneyPaise(activeOverviews.sumOf { it.youAreOwed.paise }),
            unsettledAmount = MoneyPaise(activeOverviews.sumOf { it.unsettledAmount.paise }),
            paidByUserTotal = MoneyPaise(paidByUser),
            userPersonalShareTotal = MoneyPaise(userShareTotal),
            reimbursementsReceivedTotal = MoneyPaise(reimbursementsReceived),
            outgoingSettlementsTotal = MoneyPaise(outgoingSettlements),
            activeGroupCount = activeOverviews.size,
            groupOverviews = overviews
        )
    }

    fun observeGroupDetail(groupId: String): Flow<SplitGroupDetailSnapshot?> = combine(
        splitBillDao.observeGroupById(groupId),
        splitBillDao.observeParticipantsForGroup(groupId),
        splitBillDao.observeActiveExpensesForGroup(groupId),
        splitBillDao.observeActiveSettlementsForGroup(groupId)
    ) { group, participants, expensesWithShares, settlements ->
        if (group == null) return@combine null
        val expenses = expensesWithShares.map { it.expense }
        val shares = expensesWithShares.flatMap { it.shares }
        val computed = SplitCalculator.computeGroupSummary(
            participants = participants,
            expenses = expenses,
            shares = shares,
            settlements = settlements,
            fallbackLastActivityMs = group.lastActivityEpochMs
        )
        val overview = SplitGroupOverview(
            group = group,
            participants = participants,
            activeMemberCount = participants.count { !it.isArchived },
            totalExpenses = MoneyPaise(computed.totalExpensesPaise),
            youOwe = MoneyPaise(computed.currentUserYouOwePaise),
            youAreOwed = MoneyPaise(computed.currentUserYouAreOwedPaise),
            netBalance = MoneyPaise(computed.currentUserNetBalancePaise),
            unsettledAmount = MoneyPaise(computed.groupUnsettledAmountPaise),
            lastActivityEpochMs = computed.lastActivityEpochMs,
            computedSummary = computed
        )
        SplitGroupDetailSnapshot(
            overview = overview,
            expenses = expensesWithShares,
            settlements = settlements
        )
    }

    suspend fun createGroup(
        name: String,
        iconKey: String = "group",
        description: String? = null,
        currentUserDisplayName: String = "Me",
        initialMemberNames: List<String> = emptyList()
    ): SplitGroupEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Group name cannot be blank" }
        val now = DateTimeUtils.nowEpochMs()
        val groupId = UUID.randomUUID().toString()
        val group = SplitGroupEntity(
            id = groupId,
            name = cleanName,
            iconKey = iconKey.ifBlank { "group" },
            description = description?.trim()?.ifBlank { null },
            isArchived = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            lastActivityEpochMs = now
        )
        val meName = currentUserDisplayName.trim().ifBlank { "Me" }

        database.withTransaction {
            splitBillDao.insertGroup(group)
            splitBillDao.insertParticipant(
                SplitParticipantEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    name = meName,
                    isCurrentUser = true,
                    isArchived = false,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
            initialMemberNames
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(meName, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .forEachIndexed { idx, memberName ->
                    splitBillDao.insertParticipant(
                        SplitParticipantEntity(
                            id = UUID.randomUUID().toString(),
                            groupId = groupId,
                            name = memberName,
                            isCurrentUser = false,
                            isArchived = false,
                            createdAtEpochMs = now + idx + 1,
                            updatedAtEpochMs = now + idx + 1
                        )
                    )
                }
        }
        return group
    }

    suspend fun updateGroup(
        groupId: String,
        name: String,
        iconKey: String,
        description: String?
    ): SplitGroupEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Group name cannot be blank" }
        val existing = splitBillDao.getGroupById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        val now = DateTimeUtils.nowEpochMs()
        val updated = existing.copy(
            name = cleanName,
            iconKey = iconKey.ifBlank { existing.iconKey },
            description = description?.trim()?.ifBlank { null },
            updatedAtEpochMs = now
        )
        splitBillDao.updateGroup(updated)
        return updated
    }

    suspend fun setGroupArchived(groupId: String, archived: Boolean) {
        val existing = splitBillDao.getGroupById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        val now = DateTimeUtils.nowEpochMs()
        splitBillDao.setGroupArchived(existing.id, archived, now)
    }

    /**
     * Deletes a group ONLY if all balances are settled (zero unsettled debt across every member)
     * so financial consistency and ledger auditability are never compromised.
     */
    suspend fun deleteGroupSafely(groupId: String) {
        database.withTransaction {
            val group = splitBillDao.getGroupById(groupId)
                ?: throw IllegalArgumentException("Group not found: $groupId")
            val participants = splitBillDao.getParticipantsForGroupOnce(groupId)
            val expenses = splitBillDao.getActiveExpensesForGroupOnce(groupId)
            val shares = splitBillDao.getActiveSharesForGroupOnce(groupId)
            val settlements = splitBillDao.getActiveSettlementsForGroupOnce(groupId)

            val summary = SplitCalculator.computeGroupSummary(
                participants = participants,
                expenses = expenses,
                shares = shares,
                settlements = settlements,
                fallbackLastActivityMs = group.lastActivityEpochMs
            )

            check(summary.groupUnsettledAmountPaise == 0L) {
                "Cannot delete '${group.name}' while ${MoneyPaise(summary.groupUnsettledAmountPaise).formatInr()} remains unsettled. Settle balances or archive the group instead."
            }

            // Strict child-to-parent cleanup to satisfy ON DELETE RESTRICT foreign keys
            splitBillDao.hardDeleteSharesByGroup(groupId)
            splitBillDao.hardDeleteSettlementsByGroup(groupId)
            splitBillDao.hardDeleteExpensesByGroup(groupId)
            splitBillDao.hardDeleteParticipantsByGroup(groupId)
            splitBillDao.hardDeleteGroupById(groupId)
        }
    }

    suspend fun addParticipant(
        groupId: String,
        name: String,
        phone: String? = null,
        email: String? = null,
        notes: String? = null
    ): SplitParticipantEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Participant name cannot be blank" }
        val group = splitBillDao.getGroupById(groupId)
            ?: throw IllegalArgumentException("Group not found: $groupId")
        val existingMembers = splitBillDao.getParticipantsForGroupOnce(groupId)
        require(existingMembers.none { !it.isArchived && it.name.equals(cleanName, ignoreCase = true) }) {
            "A participant named '$cleanName' already exists in ${group.name}"
        }
        val now = DateTimeUtils.nowEpochMs()
        val participant = SplitParticipantEntity(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = cleanName,
            phone = phone?.trim()?.ifBlank { null },
            email = email?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            isCurrentUser = false,
            isArchived = false,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        database.withTransaction {
            splitBillDao.insertParticipant(participant)
            splitBillDao.touchGroupActivity(groupId, now, now)
        }
        return participant
    }

    suspend fun updateParticipant(
        participantId: String,
        name: String,
        phone: String?,
        email: String?,
        notes: String?
    ): SplitParticipantEntity {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Participant name cannot be blank" }
        val existing = splitBillDao.getParticipantById(participantId)
            ?: throw IllegalArgumentException("Participant not found: $participantId")
        val now = DateTimeUtils.nowEpochMs()
        val updated = existing.copy(
            name = cleanName,
            phone = phone?.trim()?.ifBlank { null },
            email = email?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            updatedAtEpochMs = now
        )
        splitBillDao.updateParticipant(updated)
        return updated
    }

    /**
     * Safely removes a participant: hard-deletes if unreferenced, archives if settled & referenced,
     * and rejects removal if the participant still has a non-zero unsettled balance.
     */
    suspend fun removeOrArchiveParticipantSafely(participantId: String): Boolean {
        return database.withTransaction {
            val participant = splitBillDao.getParticipantById(participantId)
                ?: throw IllegalArgumentException("Participant not found: $participantId")
            require(!participant.isCurrentUser) { "Your own profile ('${participant.name}') cannot be removed from the group" }

            val group = splitBillDao.getGroupById(participant.groupId)
                ?: throw IllegalArgumentException("Group missing")
            val participants = splitBillDao.getParticipantsForGroupOnce(participant.groupId)
            val expenses = splitBillDao.getActiveExpensesForGroupOnce(participant.groupId)
            val shares = splitBillDao.getActiveSharesForGroupOnce(participant.groupId)
            val settlements = splitBillDao.getActiveSettlementsForGroupOnce(participant.groupId)

            val computed = SplitCalculator.computeGroupSummary(
                participants = participants,
                expenses = expenses,
                shares = shares,
                settlements = settlements,
                fallbackLastActivityMs = group.lastActivityEpochMs
            )
            val net = computed.participantBalances
                .firstOrNull { it.participant.id == participantId }
                ?.netBalancePaise ?: 0L

            check(net == 0L) {
                "Settle ${participant.name}'s remaining balance (${MoneyPaise(kotlin.math.abs(net)).formatInr()}) before deleting or archiving."
            }

            val refCount = splitBillDao.countParticipantReferences(participantId)
            if (refCount == 0) {
                splitBillDao.deleteParticipantById(participantId)
                true // hard-deleted
            } else {
                val now = DateTimeUtils.nowEpochMs()
                splitBillDao.setParticipantArchived(participantId, isArchived = true, updatedAt = now)
                false // archived to preserve historical audit integrity
            }
        }
    }

    /**
     * Atomically creates a Split Bill expense + shares AND, when paid by the current user,
     * deducts the full amount from the selected Bank/Cash account via [CentralLedgerEngine].
     */
    suspend fun addExpense(
        groupId: String,
        description: String,
        totalAmountPaise: Long,
        expenseDateEpochMs: Long,
        paidByParticipantId: String,
        paidFromAccountId: String?,
        splitSpec: SplitInputSpec,
        notes: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
        testBeforeLedgerCommitHook: (suspend () -> Unit)? = null
    ): SplitExpenseEntity {
        val cleanDesc = description.trim()
        require(cleanDesc.isNotBlank()) { "Expense description cannot be blank" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }

        val computedShares = SplitCalculator.calculateShares(totalAmountPaise, splitSpec)

        return ledgerEngine.withAtomicLedgerLock {
            val replay = splitBillDao.getExpenseByIdempotencyKey(idempotencyKey)
            if (replay != null) return@withAtomicLedgerLock replay

            val group = splitBillDao.getGroupById(groupId)
                ?: throw IllegalArgumentException("Group not found: $groupId")
            val payer = splitBillDao.getParticipantById(paidByParticipantId)
                ?: throw IllegalArgumentException("Payer not found")
            require(payer.groupId == groupId) { "Payer does not belong to ${group.name}" }

            val groupParticipantIds = splitBillDao.getParticipantsForGroupOnce(groupId).mapTo(HashSet()) { it.id }
            require(computedShares.all { it.participantId in groupParticipantIds }) {
                "All split participants must belong to ${group.name}"
            }

            val expenseId = UUID.randomUUID().toString()
            val now = DateTimeUtils.nowEpochMs()

            val linkedAccount: AccountEntity?
            val ledgerTxId: String?
            if (payer.isCurrentUser) {
                linkedAccount = requireEligibleSplitAccount(paidFromAccountId)
                val postResult = post(
                    LedgerPostSpec.Expense(
                        accountId = linkedAccount.id,
                        amountPaise = totalAmountPaise,
                        categoryId = null,
                        paymentMethod = accountToPaymentMethod(linkedAccount.accountType),
                        isSplitExpense = true,
                        description = "${group.name}: $cleanDesc",
                        notes = notes?.trim()?.ifBlank { null },
                        timestampEpochMs = expenseDateEpochMs,
                        idempotencyKey = "ledger_$idempotencyKey",
                        sourceModule = "SPLIT_BILL",
                        sourceRecordId = expenseId
                    )
                )
                ledgerTxId = postResult.transactionId
            } else {
                linkedAccount = null
                ledgerTxId = null
            }

            testBeforeLedgerCommitHook?.invoke()

            val expense = SplitExpenseEntity(
                id = expenseId,
                groupId = groupId,
                idempotencyKey = idempotencyKey,
                description = cleanDesc,
                totalAmountPaise = totalAmountPaise,
                expenseDateEpochMs = expenseDateEpochMs,
                paidByParticipantId = payer.id,
                paidFromAccountId = linkedAccount?.id,
                ledgerTransactionId = ledgerTxId,
                splitMethod = splitSpec.splitMethod(),
                notes = notes?.trim()?.ifBlank { null },
                isDeleted = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            val shareEntities = computedShares.map { share ->
                SplitExpenseShareEntity(
                    id = UUID.randomUUID().toString(),
                    expenseId = expenseId,
                    participantId = share.participantId,
                    shareAmountPaise = share.shareAmountPaise,
                    percentageBps = share.percentageBps,
                    customUnitsScaled = share.customUnitsScaled
                )
            }
            splitBillDao.insertExpense(expense)
            splitBillDao.insertShares(shareEntities)
            splitBillDao.touchGroupActivity(groupId, maxOf(now, expenseDateEpochMs), now)
            expense
        }
    }

    /**
     * Atomically updates a Split Bill expense and synchronizes its Finance ledger record,
     * including seamless handling if payer switches between Me <-> Another Participant.
     */
    suspend fun editExpense(
        expenseId: String,
        description: String,
        totalAmountPaise: Long,
        expenseDateEpochMs: Long,
        paidByParticipantId: String,
        paidFromAccountId: String?,
        splitSpec: SplitInputSpec,
        notes: String? = null
    ): SplitExpenseEntity {
        val cleanDesc = description.trim()
        require(cleanDesc.isNotBlank()) { "Expense description cannot be blank" }
        val computedShares = SplitCalculator.calculateShares(totalAmountPaise, splitSpec)

        return ledgerEngine.withAtomicLedgerLock {
            val existing = splitBillDao.getExpenseById(expenseId)
                ?: throw IllegalArgumentException("Expense not found: $expenseId")
            check(!existing.isDeleted) { "Deleted expense cannot be edited" }

            val group = splitBillDao.getGroupById(existing.groupId)
                ?: throw IllegalArgumentException("Group not found")
            val newPayer = splitBillDao.getParticipantById(paidByParticipantId)
                ?: throw IllegalArgumentException("Payer not found")
            require(newPayer.groupId == existing.groupId) { "Payer belongs to a different group" }

            val now = DateTimeUtils.nowEpochMs()
            val oldLedgerId = existing.ledgerTransactionId
            val newLedgerId: String?
            val newAccountId: String?

            if (newPayer.isCurrentUser) {
                val account = requireEligibleSplitAccount(paidFromAccountId)
                val spec = LedgerPostSpec.Expense(
                    accountId = account.id,
                    amountPaise = totalAmountPaise,
                    categoryId = null,
                    paymentMethod = accountToPaymentMethod(account.accountType),
                    isSplitExpense = true,
                    description = "${group.name}: $cleanDesc",
                    notes = notes?.trim()?.ifBlank { null },
                    timestampEpochMs = expenseDateEpochMs,
                    idempotencyKey = "edit_split_exp_${expenseId}_$now",
                    sourceModule = "SPLIT_BILL",
                    sourceRecordId = expenseId
                )
                newLedgerId = if (oldLedgerId != null) {
                    editTransaction(oldLedgerId, spec).transactionId
                } else {
                    post(spec).transactionId
                }
                newAccountId = account.id
            } else {
                if (oldLedgerId != null) {
                    reverseOrDeleteTransaction(
                        transactionId = oldLedgerId,
                        idempotencyKey = "rev_split_exp_switch_${expenseId}_$now",
                        reason = "Split expense payer changed to ${newPayer.name}"
                    )
                }
                newLedgerId = null
                newAccountId = null
            }

            val updated = existing.copy(
                description = cleanDesc,
                totalAmountPaise = totalAmountPaise,
                expenseDateEpochMs = expenseDateEpochMs,
                paidByParticipantId = newPayer.id,
                paidFromAccountId = newAccountId,
                ledgerTransactionId = newLedgerId,
                splitMethod = splitSpec.splitMethod(),
                notes = notes?.trim()?.ifBlank { null },
                updatedAtEpochMs = now
            )
            splitBillDao.updateExpense(updated)
            splitBillDao.deleteSharesForExpense(expenseId)
            splitBillDao.insertShares(
                computedShares.map { share ->
                    SplitExpenseShareEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        participantId = share.participantId,
                        shareAmountPaise = share.shareAmountPaise,
                        percentageBps = share.percentageBps,
                        customUnitsScaled = share.customUnitsScaled
                    )
                }
            )
            splitBillDao.touchGroupActivity(existing.groupId, now, now)
            updated
        }
    }

    /**
     * Atomically marks a Split Bill expense deleted and reverses any linked Finance ledger transaction.
     */
    suspend fun deleteExpense(expenseId: String) {
        ledgerEngine.withAtomicLedgerLock {
            val existing = splitBillDao.getExpenseById(expenseId)
                ?: throw IllegalArgumentException("Expense not found: $expenseId")
            if (existing.isDeleted) return@withAtomicLedgerLock

            val now = DateTimeUtils.nowEpochMs()
            existing.ledgerTransactionId?.let { txId ->
                reverseOrDeleteTransaction(
                    transactionId = txId,
                    idempotencyKey = "rev_split_exp_${expenseId}",
                    reason = "Split expense deleted: ${existing.description}"
                )
            }
            splitBillDao.markExpenseDeleted(expenseId, now)
            splitBillDao.touchGroupActivity(existing.groupId, now, now)
        }
    }

    /**
     * Atomically records a Split Bill settlement and updates Finance via [CentralLedgerEngine]
     * whenever the user sends money (OUTGOING_PAYMENT) or receives reimbursement (INCOMING_REIMBURSEMENT).
     */
    suspend fun recordSettlement(
        groupId: String,
        fromParticipantId: String,
        toParticipantId: String,
        amountPaise: Long,
        settlementDateEpochMs: Long,
        accountId: String?,
        notes: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): SplitSettlementEntity {
        require(amountPaise > 0L) { "Settlement amount must be greater than ₹0.00" }
        require(fromParticipantId != toParticipantId) { "Payer and recipient must be different people" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key cannot be blank" }

        return ledgerEngine.withAtomicLedgerLock {
            val replay = splitBillDao.getSettlementByIdempotencyKey(idempotencyKey)
            if (replay != null) return@withAtomicLedgerLock replay

            val group = splitBillDao.getGroupById(groupId)
                ?: throw IllegalArgumentException("Group not found: $groupId")
            val fromMember = splitBillDao.getParticipantById(fromParticipantId)
                ?: throw IllegalArgumentException("Payer not found")
            val toMember = splitBillDao.getParticipantById(toParticipantId)
                ?: throw IllegalArgumentException("Recipient not found")
            require(fromMember.groupId == groupId && toMember.groupId == groupId) {
                "Both participants must belong to ${group.name}"
            }

            val settlementId = UUID.randomUUID().toString()
            val now = DateTimeUtils.nowEpochMs()
            val direction = resolveDirection(fromMember, toMember)

            val linkedAccountId: String?
            val ledgerTxId: String?

            when (direction) {
                SettlementDirection.OUTGOING_PAYMENT -> {
                    val account = requireEligibleSplitAccount(accountId)
                    val res = post(
                        LedgerPostSpec.SplitSettlement(
                            fromAccountId = account.id,
                            amountPaise = amountPaise,
                            participantName = toMember.name,
                            description = "${group.name}: Settled to ${toMember.name}",
                            notes = notes?.trim()?.ifBlank { null },
                            timestampEpochMs = settlementDateEpochMs,
                            idempotencyKey = "ledger_set_$idempotencyKey",
                            sourceModule = "SPLIT_BILL",
                            sourceRecordId = settlementId
                        )
                    )
                    linkedAccountId = account.id
                    ledgerTxId = res.transactionId
                }

                SettlementDirection.INCOMING_REIMBURSEMENT -> {
                    val account = requireEligibleSplitAccount(accountId)
                    val res = post(
                        LedgerPostSpec.Income(
                            destinationAccountId = account.id,
                            amountPaise = amountPaise,
                            categoryId = null,
                            isSplitReimbursement = true,
                            description = "${group.name}: Reimbursement from ${fromMember.name}",
                            notes = notes?.trim()?.ifBlank { null },
                            timestampEpochMs = settlementDateEpochMs,
                            idempotencyKey = "ledger_set_$idempotencyKey",
                            sourceModule = "SPLIT_BILL",
                            sourceRecordId = settlementId
                        )
                    )
                    linkedAccountId = account.id
                    ledgerTxId = res.transactionId
                }

                SettlementDirection.INTERNAL -> {
                    linkedAccountId = null
                    ledgerTxId = null
                }
            }

            val entity = SplitSettlementEntity(
                id = settlementId,
                groupId = groupId,
                idempotencyKey = idempotencyKey,
                fromParticipantId = fromMember.id,
                toParticipantId = toMember.id,
                amountPaise = amountPaise,
                settlementDateEpochMs = settlementDateEpochMs,
                notes = notes?.trim()?.ifBlank { null },
                accountId = linkedAccountId,
                ledgerTransactionId = ledgerTxId,
                direction = direction,
                isDeleted = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            splitBillDao.insertSettlement(entity)
            splitBillDao.touchGroupActivity(groupId, maxOf(now, settlementDateEpochMs), now)
            entity
        }
    }

    suspend fun editSettlement(
        settlementId: String,
        fromParticipantId: String,
        toParticipantId: String,
        amountPaise: Long,
        settlementDateEpochMs: Long,
        accountId: String?,
        notes: String?
    ): SplitSettlementEntity {
        require(amountPaise > 0L) { "Settlement amount must be greater than ₹0.00" }
        require(fromParticipantId != toParticipantId) { "Payer and recipient must be different people" }

        return ledgerEngine.withAtomicLedgerLock {
            val existing = splitBillDao.getSettlementById(settlementId)
                ?: throw IllegalArgumentException("Settlement not found: $settlementId")
            check(!existing.isDeleted) { "Deleted settlement cannot be edited" }

            val group = splitBillDao.getGroupById(existing.groupId)
                ?: throw IllegalArgumentException("Group not found")
            val fromMember = splitBillDao.getParticipantById(fromParticipantId)
                ?: throw IllegalArgumentException("Payer not found")
            val toMember = splitBillDao.getParticipantById(toParticipantId)
                ?: throw IllegalArgumentException("Recipient not found")
            require(fromMember.groupId == existing.groupId && toMember.groupId == existing.groupId) {
                "Both participants must belong to ${group.name}"
            }

            val now = DateTimeUtils.nowEpochMs()
            val newDirection = resolveDirection(fromMember, toMember)
            val oldLedgerId = existing.ledgerTransactionId
            val newAccountId: String?
            val newLedgerId: String?

            when (newDirection) {
                SettlementDirection.OUTGOING_PAYMENT -> {
                    val account = requireEligibleSplitAccount(accountId)
                    val spec = LedgerPostSpec.SplitSettlement(
                        fromAccountId = account.id,
                        amountPaise = amountPaise,
                        participantName = toMember.name,
                        description = "${group.name}: Settled to ${toMember.name}",
                        notes = notes?.trim()?.ifBlank { null },
                        timestampEpochMs = settlementDateEpochMs,
                        idempotencyKey = "edit_set_${settlementId}_$now",
                        sourceModule = "SPLIT_BILL",
                        sourceRecordId = settlementId
                    )
                    newLedgerId = if (oldLedgerId != null) {
                        editTransaction(oldLedgerId, spec).transactionId
                    } else {
                        post(spec).transactionId
                    }
                    newAccountId = account.id
                }

                SettlementDirection.INCOMING_REIMBURSEMENT -> {
                    val account = requireEligibleSplitAccount(accountId)
                    val spec = LedgerPostSpec.Income(
                        destinationAccountId = account.id,
                        amountPaise = amountPaise,
                        categoryId = null,
                        isSplitReimbursement = true,
                        description = "${group.name}: Reimbursement from ${fromMember.name}",
                        notes = notes?.trim()?.ifBlank { null },
                        timestampEpochMs = settlementDateEpochMs,
                        idempotencyKey = "edit_set_${settlementId}_$now",
                        sourceModule = "SPLIT_BILL",
                        sourceRecordId = settlementId
                    )
                    newLedgerId = if (oldLedgerId != null) {
                        editTransaction(oldLedgerId, spec).transactionId
                    } else {
                        post(spec).transactionId
                    }
                    newAccountId = account.id
                }

                SettlementDirection.INTERNAL -> {
                    if (oldLedgerId != null) {
                        reverseOrDeleteTransaction(
                            transactionId = oldLedgerId,
                            idempotencyKey = "rev_set_switch_${settlementId}_$now",
                            reason = "Settlement changed to internal group transfer"
                        )
                    }
                    newAccountId = null
                    newLedgerId = null
                }
            }

            val updated = existing.copy(
                fromParticipantId = fromMember.id,
                toParticipantId = toMember.id,
                amountPaise = amountPaise,
                settlementDateEpochMs = settlementDateEpochMs,
                notes = notes?.trim()?.ifBlank { null },
                accountId = newAccountId,
                ledgerTransactionId = newLedgerId,
                direction = newDirection,
                updatedAtEpochMs = now
            )
            splitBillDao.updateSettlement(updated)
            splitBillDao.touchGroupActivity(existing.groupId, now, now)
            updated
        }
    }

    suspend fun deleteSettlement(settlementId: String) {
        ledgerEngine.withAtomicLedgerLock {
            val existing = splitBillDao.getSettlementById(settlementId)
                ?: throw IllegalArgumentException("Settlement not found: $settlementId")
            if (existing.isDeleted) return@withAtomicLedgerLock

            val now = DateTimeUtils.nowEpochMs()
            existing.ledgerTransactionId?.let { txId ->
                reverseOrDeleteTransaction(
                    transactionId = txId,
                    idempotencyKey = "rev_split_set_${settlementId}",
                    reason = "Split settlement deleted"
                )
            }
            splitBillDao.markSettlementDeleted(settlementId, now)
            splitBillDao.touchGroupActivity(existing.groupId, now, now)
        }
    }

    private fun resolveDirection(
        fromMember: SplitParticipantEntity,
        toMember: SplitParticipantEntity
    ): SettlementDirection = when {
        fromMember.isCurrentUser && !toMember.isCurrentUser -> SettlementDirection.OUTGOING_PAYMENT
        !fromMember.isCurrentUser && toMember.isCurrentUser -> SettlementDirection.INCOMING_REIMBURSEMENT
        else -> SettlementDirection.INTERNAL
    }

    private suspend fun requireEligibleSplitAccount(accountId: String?): AccountEntity {
        val id = accountId?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Select a Bank or Cash account for this transaction.")
        val account = accountDao.getAccountById(id)
            ?: throw IllegalArgumentException("Selected account not found.")
        check(!account.isArchived) { "Account '${account.name}' is archived." }
        require(account.accountType == AccountType.BANK || account.accountType == AccountType.CASH) {
            "Split Bill payments and reimbursements require a Bank or Cash account."
        }
        return account
    }

    private fun accountToPaymentMethod(type: AccountType): PaymentMethod = when (type) {
        AccountType.BANK -> PaymentMethod.BANK
        AccountType.CASH -> PaymentMethod.CASH
        AccountType.SAVINGS -> PaymentMethod.SAVINGS
        AccountType.CREDIT_CARD -> PaymentMethod.CREDIT_CARD
        AccountType.CUSTOM -> PaymentMethod.CUSTOM
    }
}

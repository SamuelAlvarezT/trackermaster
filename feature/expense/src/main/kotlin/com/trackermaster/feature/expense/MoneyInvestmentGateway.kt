package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.domain.money.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

interface MoneyInvestmentGateway {
    val workspace: Flow<MoneyWorkspaceData>
    val book: Flow<InvestmentBook>
    val occupied: Flow<Set<Long>>
    val preferences: Flow<MoneyPreferences>
    val settings: Flow<AppSettings>
    suspend fun initialize()
    suspend fun save(draft: InvestmentDraft)
    suspend fun archive(value: MoneyInvestment)
    suspend fun reverse(sequence: Long, version: Long)
    suspend fun privacy(hidden: Boolean)
    fun changed()
}

class LocalInvestmentGateway @Inject constructor(private val repo: MoneyInvestmentRepository,
    private val money: MoneyRepository, commitments: MoneyCommitmentRepository,
    private val prefs: MoneyPreferencesRepository, settings: SettingsRepository,
    private val scheduler: MoneyPlanningScheduler) : MoneyInvestmentGateway {
    override val workspace = money.observeWorkspace()
    override val book = repo.data
    override val occupied = commitments.data.map { it.plannedMovementIds + it.payments.map { payment -> payment.movementId } }
    override val preferences = prefs.preferences
    override val settings = settings.settings
    override suspend fun initialize() = money.ensureCategories()
    override suspend fun save(draft: InvestmentDraft) {
        val amount = Money.fromDecimal(draft.amount, draft.currency)
        val cost = Money.fromDecimal(draft.cost, draft.currency)
        if (draft.kind == null) {
            if (draft.editing) repo.details(draft.id, draft.version, draft.name, draft.note, false, cost, amount, LocalDate.parse(draft.date))
            else repo.create(MoneyInvestment(draft.id, draft.name, draft.currency, draft.space, LocalDate.parse(draft.date), cost, amount, draft.note))
        } else {
            val date = LocalDate.parse(draft.date)
            val cash = if (draft.kind == InvestmentEventKind.VALUATION) null else MoneyMovement(accountId = requireNotNull(draft.account),
                amount = amount, direction = if (draft.kind == InvestmentEventKind.CONTRIBUTION) MoneyDirection.EXPENSE else MoneyDirection.INCOME,
                date = date, spaceId = draft.space, pocketId = draft.pocket, categoryId = draft.category, note = draft.note)
            repo.record(draft.id, draft.version, InvestmentEvent(draft.request, draft.id, draft.kind, amount, date,
                if (draft.kind == InvestmentEventKind.WITHDRAWAL) cost else amount, note = draft.note), cash, draft.linked)
        }
    }
    override suspend fun archive(value: MoneyInvestment) = repo.details(value.id, value.version, value.name, value.note, !value.archived)
    override suspend fun reverse(sequence: Long, version: Long) = repo.reverse(sequence, version)
    override suspend fun privacy(hidden: Boolean) = prefs.setPrivacy(hidden)
    override fun changed() { prefs.notifyMoneyChanged(); scheduler.refresh() }
}

@Module @InstallIn(SingletonComponent::class)
abstract class InvestmentBindings { @Binds abstract fun gateway(value: LocalInvestmentGateway): MoneyInvestmentGateway }

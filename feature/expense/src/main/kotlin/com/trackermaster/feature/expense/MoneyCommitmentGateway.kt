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
import java.time.LocalDate
import javax.inject.Inject

interface MoneyCommitmentGateway {
    val workspace: Flow<MoneyWorkspaceData>
    val commitments: Flow<MoneyCommitments>
    val preferences: Flow<MoneyPreferences>
    val settings: Flow<AppSettings>
    suspend fun initialize()
    suspend fun debt(value: MoneyDebt, opening: MoneyMovement?, existing: Boolean)
    suspend fun goal(value: SavingsGoal, account: Long?, space: Long)
    suspend fun repay(value: CommitmentDraft, movement: MoneyMovement)
    suspend fun allocate(value: CommitmentDraft, amount: Money)
    suspend fun reverse(id: String, version: Long)
    suspend fun privacy(hidden: Boolean)
    fun changed()
}
class LocalCommitmentGateway @Inject constructor(private val repo: MoneyCommitmentRepository,
    private val money: MoneyRepository, private val prefs: MoneyPreferencesRepository,
    settings: SettingsRepository, private val scheduler: MoneyPlanningScheduler) : MoneyCommitmentGateway {
    override val workspace = money.observeWorkspace()
    override val commitments = repo.data
    override val preferences = prefs.preferences
    override val settings = settings.settings
    override suspend fun initialize() = money.ensureCategories()
    override suspend fun debt(value: MoneyDebt, opening: MoneyMovement?, existing: Boolean) {
        if (existing) repo.updateDebt(value) else repo.createDebt(value, opening)
    }
    override suspend fun goal(value: SavingsGoal, account: Long?, space: Long) {
        if (value.pocketId == 0L) repo.createGoalWithPocket(value, requireNotNull(account), space) else repo.saveGoal(value)
    }
    override suspend fun repay(value: CommitmentDraft, movement: MoneyMovement) { repo.repay(value.id, value.version, value.request, movement, value.linked) }
    override suspend fun allocate(value: CommitmentDraft, amount: Money) { repo.allocate(value.id, value.version, amount, requireNotNull(value.account), value.withdraw, LocalDate.parse(value.date), value.request) }
    override suspend fun reverse(id: String, version: Long) = repo.reversePayment(id, version)
    override suspend fun privacy(hidden: Boolean) = prefs.setPrivacy(hidden)
    override fun changed() { prefs.notifyMoneyChanged(); scheduler.refresh() }
}
@Module @InstallIn(SingletonComponent::class)
abstract class CommitmentBindings { @Binds abstract fun gateway(value: LocalCommitmentGateway): MoneyCommitmentGateway }

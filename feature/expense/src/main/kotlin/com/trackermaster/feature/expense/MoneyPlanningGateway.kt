package com.trackermaster.feature.expense

import android.content.Context
import com.trackermaster.core.data.money.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.model.AppSettings
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

interface MoneyPlanningGateway {
    val data: Flow<MoneyWorkspaceData>
    val preferences: Flow<MoneyPreferences>
    val settings: Flow<AppSettings>
    val occurrences: Flow<List<MoneyPlanOccurrence>>
    suspend fun initialize()
    suspend fun budget(value: MoneyBudget)
    suspend fun deleteBudget(id: Long)
    suspend fun plan(value: MoneyPlan)
    suspend fun status(value: MoneyPlan, active: Boolean, archived: Boolean = false)
    suspend fun skip(value: MoneyPlan)
    suspend fun snooze(value: MoneyPlan, day: LocalDate)
    suspend fun confirm(form: SettlementForm, movement: MoneyMovement)
    suspend fun privacy(value: Boolean)
    fun notificationsAllowed(): Boolean
    fun refresh()
    fun notifyChanged()
}
class LocalPlanningGateway @Inject constructor(private val repo: MoneyPlanningRepository, private val money: MoneyRepository,
    private val prefs: MoneyPreferencesRepository, settings: SettingsRepository, private val scheduler: MoneyPlanningScheduler,
    @ApplicationContext private val context: Context) : MoneyPlanningGateway {
    override val data = money.observeWorkspace()
    override val preferences = prefs.preferences
    override val settings = settings.settings
    override val occurrences = repo.occurrences
    override suspend fun initialize() { money.ensureCategories(); scheduler.ensureScheduled() }
    override suspend fun budget(value: MoneyBudget) { repo.saveBudget(value) }
    override suspend fun deleteBudget(id: Long) = repo.deleteBudget(id)
    override suspend fun plan(value: MoneyPlan) = repo.savePlan(value)
    override suspend fun status(value: MoneyPlan, active: Boolean, archived: Boolean) = repo.status(value.id,value.version,active,archived)
    override suspend fun skip(value: MoneyPlan) = repo.skip(value.id,value.nextDue,value.version)
    override suspend fun snooze(value: MoneyPlan, day: LocalDate) = repo.snooze(value.id,value.version,day)
    override suspend fun confirm(form: SettlementForm, movement: MoneyMovement) {
        repo.confirm(form.planId,LocalDate.parse(form.due),form.version,movement,form.linked)
    }
    override suspend fun privacy(value: Boolean) = prefs.setPrivacy(value)
    override fun notificationsAllowed() = MoneyPlanningWorker.notificationsAllowed(context)
    override fun refresh() = scheduler.refresh()
    override fun notifyChanged() { prefs.notifyMoneyChanged(); scheduler.refresh() }
}
@Module @InstallIn(SingletonComponent::class)
abstract class PlanningBindings { @Binds abstract fun planning(value: LocalPlanningGateway): MoneyPlanningGateway }

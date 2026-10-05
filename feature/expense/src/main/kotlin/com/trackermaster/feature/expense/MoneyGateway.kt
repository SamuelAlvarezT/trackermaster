package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.money.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MoneyComputation

@Module
@InstallIn(SingletonComponent::class)
object MoneyDispatchers {
    @Provides @MoneyComputation fun computation(): CoroutineDispatcher = Dispatchers.Default
}

/** ViewModel boundary: a fake can exercise the entire UDF flow without Compose or SQLite. */
interface MoneyGateway {
    val data: Flow<MoneyWorkspaceData>
    val preferences: Flow<MoneyPreferences>
    val localeTag: Flow<String>
    suspend fun initialize()
    suspend fun record(movement: MoneyMovement): Long
    suspend fun transfer(editor: MoneyEditorUiState, amount: Money, received: Money)
    suspend fun delete(id: Long): MoneyUndoToken
    suspend fun restore(token: MoneyUndoToken)
    suspend fun account(account: MoneyAccount): Long
    suspend fun archive(id: Long, archived: Boolean)
    suspend fun pocket(account: Long, space: Long, name: String): Long
    suspend fun category(name: String, income: Boolean): Long
    suspend fun space(name: String): Long
    suspend fun appearance(detailed: Boolean, sections: Boolean)
    suspend fun privacy(hidden: Boolean)
    suspend fun rememberFunding(account: Long, pocket: Long?)
    fun notifyChanged()
}

@Singleton
class LocalMoneyGateway @Inject constructor(private val repo: MoneyRepository,
    private val prefs: MoneyPreferencesRepository, settings: SettingsRepository) : MoneyGateway {
    override val data = repo.observeWorkspace()
    override val preferences = prefs.preferences
    override val localeTag = settings.settings.map { it.localeTag }
    override suspend fun initialize() = repo.ensureCategories()
    override suspend fun record(movement: MoneyMovement) = repo.record(movement)
    override suspend fun transfer(editor: MoneyEditorUiState, amount: Money, received: Money) {
        if (editor.transferId == null) repo.transfer(requireNotNull(editor.accountId), requireNotNull(editor.targetAccountId),
            amount, received, editor.pocketId, editor.targetPocketId, editor.date, editor.note, editor.requestId)
        else repo.editTransfer(editor.transferId, requireNotNull(editor.accountId), requireNotNull(editor.targetAccountId),
            amount, received, editor.pocketId, editor.targetPocketId, editor.date, editor.note)
    }
    override suspend fun delete(id: Long) = repo.deleteEntry(id)
    override suspend fun restore(token: MoneyUndoToken) = repo.restore(token)
    override suspend fun account(account: MoneyAccount) = repo.saveAccount(account)
    override suspend fun archive(id: Long, archived: Boolean) = repo.archiveAccount(id, archived)
    override suspend fun pocket(account: Long, space: Long, name: String) = repo.createPocket(account, space, name)
    override suspend fun category(name: String, income: Boolean) = repo.createCategory(name, income)
    override suspend fun space(name: String) = repo.createSpace(name)
    override suspend fun appearance(detailed: Boolean, sections: Boolean) { prefs.setAppearance(detailed, sections) }
    override suspend fun privacy(hidden: Boolean) = prefs.setPrivacy(hidden)
    override suspend fun rememberFunding(account: Long, pocket: Long?) { prefs.rememberFunding(account, pocket) }
    override fun notifyChanged() = prefs.notifyMoneyChanged()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoneyBindings {
    @Binds abstract fun gateway(implementation: LocalMoneyGateway): MoneyGateway
}

package com.trackermaster.feature.expense

import com.trackermaster.core.data.money.*
import com.trackermaster.core.data.settings.SettingsRepository
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.domain.model.AppSettings
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ReceiptGateway {
    val workspace: Flow<MoneyWorkspaceData>
    val drafts: Flow<List<MoneyReceipt>>
    val preferences: Flow<MoneyPreferences>
    val settings: Flow<AppSettings>
    suspend fun initialize()
    suspend fun import(source: ReceiptSource): MoneyReceipt
    suspend fun read(id: String): MoneyReceipt
    suspend fun get(id: String): MoneyReceipt
    suspend fun persist(id: String, draft: ReceiptDraft)
    suspend fun duplicates(id: String, draft: ReceiptDraft): List<ReceiptDuplicate>
    suspend fun confirm(id: String, draft: ReceiptDraft)
    suspend fun discard(id: String)
    suspend fun privacy(hidden: Boolean)
}
class LocalReceiptGateway @Inject constructor(private val receipts: MoneyReceiptRepository, private val money: MoneyRepository,
    private val files: ReceiptFiles, private val reader: ReceiptReader, private val prefs: MoneyPreferencesRepository,
    settingsRepo: SettingsRepository) : ReceiptGateway {
    override val workspace = money.observeWorkspace()
    override val drafts = receipts.drafts
    override val preferences = prefs.preferences
    override val settings = settingsRepo.settings
    override suspend fun initialize() { money.ensureCategories() }
    override suspend fun import(source: ReceiptSource) = receipts.create(files.copy(source))
    override suspend fun get(id: String) = receipts.get(id)
    override suspend fun read(id: String): MoneyReceipt {
        val receipt = receipts.get(id)
        return try {
            val reading = reader.read(receipt)
            receipts.analyzed(id,reading.text,reading.preview,reading.text.isBlank())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { receipts.analyzed(id,"",receipt.previewPath,true) }
    }
    override suspend fun persist(id: String, draft: ReceiptDraft) { receipts.updateDraft(id,draft) }
    override suspend fun duplicates(id: String, draft: ReceiptDraft) = receipts.duplicates(id,draft)
    override suspend fun confirm(id: String, draft: ReceiptDraft) {
        receipts.confirm(id,draft)
        // A refresh failure must not present an already committed movement as unsaved.
        runCatching { prefs.notifyMoneyChanged() }
    }
    override suspend fun discard(id: String) { receipts.discard(id) }
    override suspend fun privacy(hidden: Boolean) { prefs.setPrivacy(hidden) }
}
@Module @InstallIn(SingletonComponent::class)
abstract class ReceiptBindings { @Binds abstract fun gateway(value: LocalReceiptGateway): ReceiptGateway }

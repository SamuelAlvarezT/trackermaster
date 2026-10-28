package com.trackermaster.core.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.trackermaster.core.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

data class CloudStatus(val configured: Boolean = false, val signedIn: Boolean = false,
    val pending: Int = 0, val syncing: Boolean = false, val lastCompletedAt: Long = 0,
    val error: String? = null)

@Singleton
class CloudSyncManager @Inject constructor(@ApplicationContext private val context: Context,
    private val store: CloudSyncStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var foregroundJob: Job? = null
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var retryAfter = 0L
    private var retryDelay = 5_000L
    private var nextCommandCheck = 0L
    private val owner = context.getString(R.string.cloud_owner_uid)
    private val writerKey = context.getString(R.string.cloud_writer_key)
    private val clientId = context.getString(R.string.cloud_web_client_id)
    private val configured = listOf(context.getString(R.string.cloud_api_key),
        context.getString(R.string.cloud_application_id), context.getString(R.string.cloud_project_id),
        clientId, owner, writerKey).all { it.isNotBlank() } && writerKey.matches(Regex("[a-fA-F0-9]{64}"))
    private val firebase by lazy {
        check(configured)
        FirebaseApp.getApps(context).firstOrNull { it.name == "trackmaster-cloud" }
            ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApiKey(context.getString(R.string.cloud_api_key))
                .setApplicationId(context.getString(R.string.cloud_application_id))
                .setProjectId(context.getString(R.string.cloud_project_id)).build(), "trackmaster-cloud")
    }
    private val auth by lazy { FirebaseAuth.getInstance(firebase) }
    private val firestore by lazy {
        FirebaseFirestore.getInstance(firebase).apply {
            // Room is the durable queue; don't keep a second persistent queue with writer proofs.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
    private val mutableStatus = MutableStateFlow(CloudStatus(configured = configured))
    val status: StateFlow<CloudStatus> = mutableStatus

    fun foreground(active: Boolean) {
        if (!active) {
            foregroundJob?.cancel(); foregroundJob = null
            networkCallback?.let { connectivity.unregisterNetworkCallback(it) }
            networkCallback = null
            return
        }
        if (foregroundJob?.isActive == true) return
        if (configured && networkCallback == null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { retryAfter = 0 }
            }.also { connectivity.registerDefaultNetworkCallback(it) }
        }
        foregroundJob = scope.launch {
            while (isActive) {
                try {
                    refresh()
                    check(!java.io.File(context.noBackupFilesDir, "cloud-sync-restored").exists()) {
                        "Cierra completamente y vuelve a abrir TrackMaster para cargar el respaldo."
                    }
                    if (configured && auth.currentUser?.uid == owner && System.currentTimeMillis() >= retryAfter) {
                        sync()
                        if (System.currentTimeMillis() >= nextCommandCheck) {
                            receiveCommands()
                            nextCommandCheck = System.currentTimeMillis() + 300_000L
                        }
                    }
                } catch (timeout: TimeoutCancellationException) { failed(timeout) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { failed(failure) }
                delay(1_500)
            }
        }
    }

    suspend fun signIn(activityContext: Context) {
        try {
            check(configured) { "Configura Firebase siguiendo docs/cloud-sync/SETUP.md." }
            val option = GetGoogleIdOption.Builder().setServerClientId(clientId)
                .setFilterByAuthorizedAccounts(false).build()
            val response = CredentialManager.create(activityContext).getCredential(activityContext,
                GetCredentialRequest.Builder().addCredentialOption(option).build())
            check(response.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val token = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            if (result.user?.uid != owner) {
                auth.signOut()
                error("Inicia sesión con la cuenta propietaria de TrackMaster.")
            }
            withContext(Dispatchers.IO) { store.bindOwner(owner) }
            retryAfter = 0; nextCommandCheck = 0; mutableStatus.value = mutableStatus.value.copy(error = null)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { failed(failure) }
    }

    suspend fun signOut(activityContext: Context) {
        mutex.withLock {
            if (configured) auth.signOut()
            mutableStatus.value = mutableStatus.value.copy(signedIn = false, syncing = false, error = null)
        }
        try { CredentialManager.create(activityContext).clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Firebase session has already ended. */ }
    }
    fun retry() { retryAfter = 0; mutableStatus.value = mutableStatus.value.copy(error = null) }
    suspend fun prepareRestore() {
        foregroundJob?.cancelAndJoin()
        foregroundJob = null
        // In-flight writes to the old dataset remain isolated from the restored dataset.
        mutableStatus.value = mutableStatus.value.copy(syncing = false)
    }

    private fun refresh() {
        val identity = store.identity()
        mutableStatus.value = mutableStatus.value.copy(pending = store.pendingCount(),
            lastCompletedAt = identity.lastCompletedAt, signedIn = configured && auth.currentUser?.uid == owner)
    }
    private fun failed(failure: Exception) {
        // Do not display raw SDK messages: they can contain request details.
        val error = if (failure is IllegalStateException) failure.message
            else "No se pudo sincronizar. Revisa conexión, cuenta, permisos o cuota de Firebase."
        mutableStatus.value = mutableStatus.value.copy(syncing = false, error = error)
        retryAfter = System.currentTimeMillis() + retryDelay
        retryDelay = (retryDelay * 2).coerceAtMost(300_000)
    }

    private suspend fun receiveCommands() = mutex.withLock {
        val identity = store.identity()
        val user = firestore.collection("users").document(owner)
        val dataset = user.collection("datasets").document(identity.datasetId)
        val commands = dataset.collection("commands").whereEqualTo("status", "queued").limit(20).get().await()
        for (command in commands.documents) {
            val decision = dataset.collection("decisions").document(command.id).get().await()
            if (decision.getString("decision") != "approved" || decision.getString("decidedBy") != owner) continue
            val result = store.applyTarget("${identity.datasetId}/${command.id}", command.getString("habitId") ?: continue,
                command.getString("recordKey") ?: continue, command.getString("localHabitId")?.toLongOrNull() ?: continue,
                command.getLong("expectedRevision") ?: continue, command.getLong("expectedTargetMs") ?: continue,
                command.getLong("targetDurationMs") ?: continue, command.getString("effectiveFrom") ?: continue)
            if (result == "waiting") continue
            val token = UUID.randomUUID().toString()
            val batch = firestore.batch()
            batch.set(user.collection("writerProof").document(token), mapOf("key" to writerKey, "at" to FieldValue.serverTimestamp()))
            batch.update(command.reference, mapOf("status" to result, "appliedAt" to FieldValue.serverTimestamp(), "writeToken" to token))
            withTimeout(30_000) { batch.commit().await() }
        }
    }

    private suspend fun sync() = mutex.withLock {
        check(auth.currentUser?.uid == owner)
        store.bindOwner(owner)
        val identity = store.identity()
        if (store.pendingCount() == 0 && identity.lastCompletedAt != 0L && System.currentTimeMillis() - identity.lastCompletedAt < 21_600_000L) return@withLock
        mutableStatus.value = mutableStatus.value.copy(syncing = true, error = null)
        try {
            val user = firestore.collection("users").document(owner)
            val dataset = user.collection("datasets").document(identity.datasetId)
            while (currentCoroutineContext().isActive) {
                // Each enabled habit adds a getAfter() dependency for its policy update.
                // Ten source records keep the batch below Firestore's 20 access-call limit.
                val records = store.pending().take(10)
                if (records.isEmpty()) break
                val batch = firestore.batch()
                val writeToken = UUID.randomUUID().toString()
                batch.set(user.collection("writerProof").document(writeToken),
                    mapOf("key" to writerKey, "at" to FieldValue.serverTimestamp()))
                // Coalesce this page only; newer queue entries remain pending after acknowledgement.
                records.groupBy { it.kind to it.key }.values.forEach { changes ->
                    val record = changes.last()
                    val payload = store.payload(record)
                    val reference = dataset.collection(record.kind)
                        .document(CloudSyncStore.documentId(record.kind, record.key))
                    batch.set(reference, mapOf("schemaVersion" to 1, "kind" to record.kind, "writeToken" to writeToken,
                        "recordKey" to record.key, "revision" to record.sequence,
                        "deleted" to (payload == null), "eventDay" to SyncProjection.eventDay(record.kind, payload.orEmpty()),
                        "data" to payload.orEmpty(), "updatedAt" to FieldValue.serverTimestamp()))
                    if (record.kind == "journal_entries") {
                        batch.set(dataset.collection("journal_metrics").document(reference.id), mapOf(
                            "schemaVersion" to 1, "kind" to "journal_metrics", "writeToken" to writeToken,
                            "recordKey" to record.key, "revision" to record.sequence, "deleted" to (payload == null),
                            "eventDay" to SyncProjection.eventDay(record.kind, payload.orEmpty()),
                            "data" to emptyMap<String, String>(), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                    if (record.kind == "habits" && payload != null) {
                        val policyRef = dataset.collection("habitPolicies").document(reference.id)
                        val policy = policyRef.get().await()
                        if (policy.getBoolean("enabled") == true) {
                            val target = payload["targetDurationMs"]?.toString()?.toLongOrNull() ?: 0L
                            val day = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).toString()
                            batch.update(policyRef, mapOf("baseRevision" to record.sequence, "targetMs" to target,
                                "trackedFrom" to if (policy.getLong("targetMs") == target) policy.getString("trackedFrom") else day))
                        }
                    }
                }
                withTimeout(30_000) { batch.commit().await() }
                store.acknowledge(records)
                refresh()
            }
            currentCoroutineContext().ensureActive()
            // Publish the dataset only after its initial upload has completed.
            val finalBatch = firestore.batch()
            val finalToken = UUID.randomUUID().toString()
            finalBatch.set(user.collection("writerProof").document(finalToken),
                mapOf("key" to writerKey, "at" to FieldValue.serverTimestamp()))
            finalBatch.set(user.collection("meta").document("sync"), mapOf(
                "schemaVersion" to 1, "datasetId" to identity.datasetId, "writeToken" to finalToken,
                "lastCompletedAt" to FieldValue.serverTimestamp()))
            withTimeout(30_000) { finalBatch.commit().await() }
            // New edits can arrive while committing metadata; never label those as completed.
            if (store.pendingCount() == 0) store.complete(System.currentTimeMillis())
            retryDelay = 5_000; retryAfter = 0
            refresh()
        } finally { mutableStatus.value = mutableStatus.value.copy(syncing = false) }
    }
}

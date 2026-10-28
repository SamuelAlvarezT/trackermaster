package com.trackermaster.core.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackermaster.core.database.CloudSyncSchema
import com.trackermaster.core.database.TrackermasterDatabase
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class CloudSyncStoreTest {
    private lateinit var room: TrackermasterDatabase
    private lateinit var store: CloudSyncStore
    @Before fun setup() {
        room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TrackermasterDatabase::class.java)
            .addCallback(CloudSyncSchema.callback).allowMainThreadQueries().build()
        store = CloudSyncStore(room)
    }
    @After fun close() { room.close() }
    private fun insertTask() { room.openHelper.writableDatabase.execSQL("INSERT INTO tasks (id,title,completed,createdAtEpoch,subtasksJson) VALUES (9007199254740993,'Original',0,1780000000000,'')") }
    @Test fun acknowledgementNeverDropsEditsArrivingDuringUpload() {
        insertTask(); val upload = store.pending()
        room.openHelper.writableDatabase.execSQL("UPDATE tasks SET title='Changed' WHERE id=9007199254740993")
        store.acknowledge(upload)
        assertEquals(1, store.pendingCount())
        val data = store.payload(store.pending().single())!!
        assertEquals("Changed", data["title"]); assertEquals("9007199254740993", data["id"])
    }
    @Test fun deletionSurvivesUntilServerAcknowledgement() {
        insertTask(); store.acknowledge(store.pending())
        room.openHelper.writableDatabase.execSQL("DELETE FROM tasks")
        assertEquals(1, store.pendingCount()); assertNull(store.payload(store.pending().single()))
        store.acknowledge(store.pending()); assertEquals(0, store.pendingCount())
    }
    @Test fun ownerBindingCannotChange() {
        store.bindOwner("owner")
        assertThrows(IllegalStateException::class.java) { store.bindOwner("other") }
    }
    @Test fun pendingChangesAndIdentitySurviveDatabaseReopening() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "cloud-sync-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, TrackermasterDatabase::class.java, name)
            .addCallback(CloudSyncSchema.callback).allowMainThreadQueries().build()
        room.close()
        room = open(); store = CloudSyncStore(room)
        try {
            insertTask(); store.bindOwner("owner")
            val datasetId = store.identity().datasetId
            room.close(); room = open(); store = CloudSyncStore(room)
            assertEquals(datasetId, store.identity().datasetId)
            assertEquals("owner", store.identity().ownerUid)
            assertEquals(1, store.pendingCount())
            assertEquals("Original", store.payload(store.pending().single())!!["title"])
        } finally { room.close(); context.deleteDatabase(name) }
    }
    @Test fun projectionStripsLocalFilesAndRetainsExactMoney() {
        val data = SyncProjection.sanitize("transactions", mapOf("amountMinor" to "9007199254740993", "imageUri" to "file:///private/receipt.jpg"))
        assertFalse(data.containsKey("imageUri")); assertEquals(true, data["imageUriLocalOnly"])
        assertEquals("9007199254740993", data["amountMinor"])
        assertEquals(20000L, SyncProjection.eventDay("transactions", mapOf("dateEpochDay" to "20000")))
    }
}

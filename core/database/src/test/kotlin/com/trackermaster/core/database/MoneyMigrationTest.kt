package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import android.database.sqlite.SQLiteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        TrackermasterDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun emptyVersionFourMigratesAndValidatesEntireSchema() {
        helper.createDatabase("empty", 4).close()
        val db = helper.runMigrationsAndValidate("empty", 5, true, MoneyMigrations.MIGRATION_4_5)
        assertEquals(5, db.scalar("SELECT COUNT(*) FROM money_spaces"))
        assertEquals(0, db.scalar("SELECT COUNT(*) FROM accounts"))
    }

    @Test fun populatedDatabasePreservesAllFeaturesAndOriginalMoneyValues() {
        val epoch = 1780000000000L
        val before = helper.createDatabase("populated", 4)
        before.execSQL("INSERT INTO accounts VALUES (1, 'Nequi', 'CHECKING', 'COP', 1000.25)")
        before.execSQL("INSERT INTO accounts VALUES (2, 'Travel', 'SAVINGS', 'USD', 12.50)")
        before.execSQL("INSERT INTO expense_categories VALUES (1, 'Food', 0, 123)")
        before.execSQL("INSERT INTO transactions VALUES (1, 1, 100.10, 'EXPENSE', 1, $epoch, 'Original note', 'receipt.jpg')")
        before.execSQL("INSERT INTO transactions VALUES (2, 2, 0.20, 'INCOME', 1, $epoch, '', NULL)")
        before.execSQL("INSERT INTO budgets VALUES (1, 1, 500.25, 'MONTHLY', 80)")
        before.execSQL("INSERT INTO exchange_rates VALUES ('USD', 'COP', 4000, $epoch)")
        before.execSQL("INSERT INTO habits VALUES (1, 'Read', 'CHECKBOX', 'DAILY', 0, 1, 0, '[]', 123, NULL, NULL, 0, 'Keep', 4)")
        before.execSQL("INSERT INTO habit_logs VALUES (1, 1, $epoch, 1, 0, '{}', 1)")
        before.execSQL("INSERT INTO mood_entries VALUES (1, $epoch, 4, 'Mood note', '')")
        before.execSQL("INSERT INTO mood_tags VALUES (1, 'Work', 123)")
        before.execSQL("INSERT INTO focus_sessions VALUES (1, 'WORK', 25, 1500, $epoch, 1)")
        before.execSQL("INSERT INTO journal_entries VALUES (1, 'Diary', '<p>Keep me</p>', $epoch, $epoch, 4)")
        before.execSQL("INSERT INTO attachments VALUES (1, 1, 'diary.pdf', 'application/pdf')")
        before.execSQL("INSERT INTO achievements VALUES (1, 'streak_7', 'Badge', $epoch, 1)")
        before.execSQL("INSERT INTO tasks VALUES (1, 'Keep task', 0, $epoch, 'Original subtasks')")
        val originalQueries = listOf("SELECT name, currencyCode, initialBalance FROM accounts ORDER BY id",
            "SELECT amount, note, imageUri, dateEpoch FROM transactions ORDER BY id", "SELECT * FROM expense_categories",
            "SELECT categoryId, limitAmount, period, alertThresholdPercent FROM budgets", "SELECT * FROM exchange_rates",
            "SELECT * FROM habits", "SELECT * FROM habit_logs", "SELECT * FROM mood_entries", "SELECT * FROM mood_tags",
            "SELECT * FROM focus_sessions", "SELECT * FROM journal_entries", "SELECT * FROM attachments",
            "SELECT * FROM achievements", "SELECT * FROM tasks")
        val originals = originalQueries.associateWith { before.rows(it) }
        before.close()
        val after = helper.runMigrationsAndValidate("populated", 5, true, MoneyMigrations.MIGRATION_4_5)
        originals.forEach { (query, rows) -> assertEquals(query, rows, after.rows(query)) }
        assertEquals(100025L, after.scalar("SELECT initialBalanceMinor FROM accounts WHERE id = 1"))
        assertEquals(1250L, after.scalar("SELECT initialBalanceMinor FROM accounts WHERE id = 2"))
        assertEquals(10010L, after.scalar("SELECT amountMinor FROM transactions WHERE id = 1"))
        assertEquals(20L, after.scalar("SELECT amountMinor FROM transactions WHERE id = 2"))
        assertEquals(1L, after.scalar("SELECT spaceId FROM accounts WHERE id = 2"))
        assertEquals(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay(),
            after.scalar("SELECT dateEpochDay FROM transactions WHERE id = 1"))
        assertEquals(1L, after.scalar("SELECT COUNT(*) FROM budgets WHERE currencyCode IS NULL AND limitAmountMinor IS NULL"))
    }

    @Test fun legacyNegativeAndSubCentAmountsRetainOriginalTrace() {
        val before = helper.createDatabase("rounding", 4)
        before.execSQL("INSERT INTO accounts VALUES (1, 'Account', 'CHECKING', 'USD', -1.015)")
        before.execSQL("INSERT INTO transactions VALUES (1, 1, -1.005, 'EXPENSE', 1, 0, '', NULL)")
        before.close()
        val after = helper.runMigrationsAndValidate("rounding", 5, true, MoneyMigrations.MIGRATION_4_5)
        assertEquals(-102L, after.scalar("SELECT initialBalanceMinor FROM accounts"))
        assertEquals(-100L, after.scalar("SELECT amountMinor FROM transactions"))
        assertEquals(listOf(listOf("-1.005")), after.rows("SELECT amount FROM transactions"))
    }

    @Test fun attachmentVariantFromOldUpgradeKeepsEveryAttachment() {
        val before = helper.createDatabase("attachment-variant", 4)
        before.execSQL("INSERT INTO journal_entries VALUES (1, 'Diary', 'Keep', 0, 0, NULL)")
        before.execSQL("DROP TABLE attachments")
        before.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, entryId INTEGER NOT NULL, path TEXT NOT NULL, mimeType TEXT NOT NULL, FOREIGN KEY(entryId) REFERENCES journal_entries(id) ON DELETE CASCADE)")
        before.execSQL("INSERT INTO attachments VALUES (1, 1, 'image.jpg', 'image/jpeg')")
        before.execSQL("INSERT INTO attachments VALUES (25, 1, 'deleted.jpg', 'image/jpeg')")
        before.execSQL("DELETE FROM attachments WHERE id = 25")
        before.close()
        val after = helper.runMigrationsAndValidate("attachment-variant", 5, true, MoneyMigrations.MIGRATION_4_5)
        assertEquals(listOf(listOf("1", "1", "image.jpg", "image/jpeg")), after.rows("SELECT * FROM attachments"))
        assertTrue(after.rows("PRAGMA foreign_key_list(attachments)").isEmpty())
        assertEquals(25L, after.scalar("SELECT seq FROM sqlite_sequence WHERE name = 'attachments'"))
    }

    @Test fun inconsistentLegacyDataAbortsUpgradeWithoutErasingDatabase() {
        val before = helper.createDatabase("invalid-currency", 4)
        before.execSQL("INSERT INTO accounts VALUES (1, 'Original account', 'CHECKING', 'INVALID', 100.25)")
        before.close()
        assertThrows(Exception::class.java) {
            helper.runMigrationsAndValidate("invalid-currency", 5, true, MoneyMigrations.MIGRATION_4_5)
        }
        val path = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath("invalid-currency")
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { original ->
            assertEquals(4, original.version)
            original.rawQuery("SELECT name, currencyCode, initialBalance FROM accounts", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Original account", it.getString(0))
                assertEquals("INVALID", it.getString(1))
                assertEquals(100.25, it.getDouble(2), 0.0)
            }
        }
    }

    private fun SupportSQLiteDatabase.scalar(query: String): Long = query(query).use { it.moveToFirst(); it.getLong(0) }
    private fun SupportSQLiteDatabase.rows(query: String): List<List<String?>> = query(query).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
    }
}

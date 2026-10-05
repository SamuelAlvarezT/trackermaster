package com.trackermaster.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyMigrationSixTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        TrackermasterDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun emptyVersionFiveMigratesWithoutAddingFinancialRecords() {
        helper.createDatabase("six-empty", 5).close()
        val after = helper.runMigrationsAndValidate("six-empty", 6, true, MoneyMigrations.MIGRATION_5_6)
        after.query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }
    @Test fun fullChainPreservesLegacyMoneyReceiptsAndCategoryNames() {
        helper.createDatabase("six-chain", 4).apply {
            execSQL("INSERT INTO accounts VALUES (1, 'Nequi', 'CHECKING', 'COP', 1000.25)")
            execSQL("INSERT INTO expense_categories VALUES (1, 'Mi categoría', 0, 123)")
            execSQL("INSERT INTO transactions VALUES (1, 1, 10.105, 'EXPENSE', 1, 1780000000000, 'Keep note', 'receipt.jpg')")
            execSQL("INSERT INTO budgets VALUES (1, 1, 500.25, 'MONTHLY', 80)")
            close()
        }
        val after = helper.runMigrationsAndValidate("six-chain", 6, true, MoneyMigrations.MIGRATION_4_5, MoneyMigrations.MIGRATION_5_6)
        after.query("SELECT amount, amountMinor, note, imageUri, entryRequestId FROM transactions WHERE id=1").use {
            it.moveToFirst(); assertEquals(10.105, it.getDouble(0), 0.0); assertEquals(1010L, it.getLong(1))
            assertEquals("Keep note", it.getString(2)); assertEquals("receipt.jpg", it.getString(3)); assertTrue(it.isNull(4))
        }
        after.query("SELECT name, builtinKey FROM expense_categories WHERE id=1").use {
            it.moveToFirst(); assertEquals("Mi categoría", it.getString(0)); assertTrue(it.isNull(1))
        }
        after.query("SELECT initialBalanceMinor FROM accounts WHERE id=1").use { it.moveToFirst(); assertEquals(100025L, it.getLong(0)) }
        after.query("SELECT limitAmount, currencyCode FROM budgets WHERE id=1").use {
            it.moveToFirst(); assertEquals(500.25, it.getDouble(0), 0.0); assertTrue(it.isNull(1))
        }
    }
    @Test fun newUniqueCategoryKeyAllowsMultipleLegacyNulls() {
        helper.createDatabase("six-keys", 5).apply {
            execSQL("INSERT INTO expense_categories VALUES (1, 'A', 0, 0)")
            execSQL("INSERT INTO expense_categories VALUES (2, 'B', 0, 0)"); close()
        }
        val after = helper.runMigrationsAndValidate("six-keys", 6, true, MoneyMigrations.MIGRATION_5_6)
        after.execSQL("INSERT INTO expense_categories VALUES (3, 'food', 0, 0, 'food')")
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            after.execSQL("INSERT INTO expense_categories VALUES (4, 'Other food', 0, 0, 'food')")
        }
        after.query("SELECT COUNT(*) FROM expense_categories WHERE builtinKey IS NULL").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
    }
}

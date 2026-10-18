package com.trackermaster.core.data.backup

import androidx.datastore.preferences.core.*
import org.junit.Assert.*
import org.junit.Test

class BackupValidationTest {
    @Test fun wrongTypesInOtherwiseValidProtobufAreRejectedBeforeReplacingSettings() {
        val malformed=mutablePreferencesOf(stringPreferencesKey("biometric") to "true")
        assertTrue(runCatching { BackupValidation.preferences("trackermaster_settings.preferences_pb",malformed) }.isFailure)
        BackupValidation.preferences("trackermaster_settings.preferences_pb",mutablePreferencesOf(booleanPreferencesKey("biometric") to true))
    }
    @Test fun currencyAndWidgetIdsKeepTheirOriginalTypes() {
        val prefs=mutablePreferencesOf(stringPreferencesKey("widget_currency") to "USD",longPreferencesKey("widget_account") to 42L)
        BackupValidation.preferences("money_settings.preferences_pb",prefs)
        assertTrue(runCatching { BackupValidation.preferences("money_settings.preferences_pb",mutablePreferencesOf(intPreferencesKey("widget_account") to 42)) }.isFailure)
    }
}

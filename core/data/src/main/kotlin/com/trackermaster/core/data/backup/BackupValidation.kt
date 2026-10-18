package com.trackermaster.core.data.backup

import androidx.datastore.preferences.core.Preferences
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.domain.focus.FocusRunStatus

object BackupValidation {
    fun preferences(name: String,values: Preferences) {
        val booleans=when(name) {
            "trackermaster_settings.preferences_pb"->setOf("biometric","onboarding","defumblr_enabled","lockscreen_widgets_enabled","app_was_open_before_screen_off","new_interface_enabled")
            "money_settings.preferences_pb"->setOf("detailed","sections","privacy")
            "unlock_launch_settings.preferences_pb"->setOf("enabled")
            else->emptySet()
        }
        val integers=when(name) {
            "trackermaster_settings.preferences_pb"->setOf("accent","pomodoro_work","pomodoro_break","pomodoro_long")
            "unlock_launch_settings.preferences_pb"->setOf("interval_minutes","rotation")
            else->emptySet()
        }
        val longs=when(name) {
            "trackermaster_settings.preferences_pb"->setOf("defumblr_last_shown","last_backup_at")
            "money_settings.preferences_pb"->setOf("last_account","last_pocket","widget_space","widget_account")
            else->emptySet()
        }
        val strings=when(name) {
            "trackermaster_settings.preferences_pb"->setOf("theme","locale","defumblr_interval","defumblr_morning_date","initial_section","backup_frequency")
            "money_settings.preferences_pb"->setOf("widget_currency")
            "unlock_launch_settings.preferences_pb"->setOf("locale","last_acknowledged","last_outcome")
            else->emptySet()
        }
        for((key,value) in values.asMap()) require(when(key.name) {
            in booleans->value is Boolean
            in integers->value is Int
            in longs->value is Long
            in strings->value is String
            else->true
        }) { "Invalid preference type" }
    }
    fun enums(db: SupportSQLiteDatabase) {
        fun check(table: String,column: String,allowed: Set<String>) {
            db.query("SELECT DISTINCT ${SqliteSnapshot.quote(column)} FROM ${SqliteSnapshot.quote(table)} WHERE ${SqliteSnapshot.quote(column)} IS NOT NULL").use { rows ->
                while(rows.moveToNext()) require(rows.getString(0) in allowed) { "Unsupported record type" }
            }
        }
        check("habits","progressType",ProgressType.entries.map { it.name }.toSet())
        check("habits","scheduleType",ScheduleType.entries.map { it.name }.toSet())
        check("habit_logs","progressTypeAtLog",ProgressType.entries.map { it.name }.toSet())
        check("habit_schedule_history","progressType",ProgressType.entries.map { it.name }.toSet())
        check("habit_schedule_history","scheduleType",ScheduleType.entries.map { it.name }.toSet())
        check("focus_sessions","category",FocusCategoryType.entries.map { it.name }.toSet())
        check("focus_runtime","category",FocusCategoryType.entries.map { it.name }.toSet())
        check("focus_runtime","status",FocusRunStatus.entries.map { it.name }.toSet())
        check("accounts","type",AccountType.entries.map { it.name }.toSet())
    }
}

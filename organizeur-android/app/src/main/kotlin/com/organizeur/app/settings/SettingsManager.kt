package com.organizeur.app.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_FILTER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_ENABLED, value).apply()

    var filterAction: FilterAction
        get() {
            val name = prefs.getString(KEY_FILTER_ACTION, FilterAction.SILENCE.name)
            return FilterAction.valueOf(name ?: FilterAction.SILENCE.name)
        }
        set(value) = prefs.edit().putString(KEY_FILTER_ACTION, value.name).apply()

    var isCallFilterVisible: Boolean
        get() = prefs.getBoolean(KEY_CALL_FILTER_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_CALL_FILTER_VISIBLE, value).apply()

    var isSilentModeVisible: Boolean
        get() = prefs.getBoolean(KEY_SILENT_MODE_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SILENT_MODE_VISIBLE, value).apply()

    var isAlarmVisible: Boolean
        get() = prefs.getBoolean(KEY_ALARM_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_VISIBLE, value).apply()

    var isTimerVisible: Boolean
        get() = prefs.getBoolean(KEY_TIMER_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_TIMER_VISIBLE, value).apply()

    var isCameraVisible: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_VISIBLE, value).apply()

    var isHelpVisible: Boolean
        get() = prefs.getBoolean(KEY_HELP_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_HELP_VISIBLE, value).apply()

    var isMiBandVisible: Boolean
        get() = prefs.getBoolean(KEY_MIBAND_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_MIBAND_VISIBLE, value).apply()

    var isGearVisible: Boolean
        get() = prefs.getBoolean(KEY_GEAR_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_GEAR_VISIBLE, value).apply()

    companion object {
        private const val PREFS_NAME = "organizeur_settings"
        private const val KEY_FILTER_ENABLED = "filter_enabled"
        private const val KEY_FILTER_ACTION = "filter_action"
        private const val KEY_CALL_FILTER_VISIBLE = "feature_call_filter_visible"
        private const val KEY_SILENT_MODE_VISIBLE = "feature_silent_mode_visible"
        private const val KEY_ALARM_VISIBLE = "feature_alarm_visible"
        private const val KEY_TIMER_VISIBLE = "feature_timer_visible"
        private const val KEY_CAMERA_VISIBLE = "feature_camera_visible"
        private const val KEY_HELP_VISIBLE = "feature_help_visible"
        private const val KEY_MIBAND_VISIBLE = "feature_miband_visible"
        private const val KEY_GEAR_VISIBLE = "feature_gear_visible"
    }
}

enum class FilterAction {
    SILENCE,
    REJECT
}

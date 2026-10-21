package com.derszili.shared

import android.content.Context

object ScheduleStore {
    private const val PREFS = "ders_zili_schedule"
    private const val KEY_SCHEDULE = "schedule_json"

    fun load(context: Context): SchoolSchedule = scheduleFromJson(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCHEDULE, null)
    )

    fun save(context: Context, schedule: SchoolSchedule) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCHEDULE, schedule.toJson())
            .apply()
    }
}

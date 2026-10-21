package com.derszili.shared

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {
    const val EXTRA_EVENT = "event"
    const val EXTRA_LESSON_NAME = "lesson_name"
    const val EXTRA_ROOM = "room"
    const val EXTRA_NEXT_NAME = "next_name"
    const val EXTRA_NEXT_TIME = "next_time"
    const val EXTRA_REMINDER_MINUTES = "reminder_minutes"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EVENT_START = "start"
    const val EVENT_END = "end"

    private const val PREFS = "ders_zili_alarm_ids"
    private const val KEY_IDS = "request_codes"
    private const val LOOK_AHEAD_DAYS = 14L

    fun schedule(context: Context, schedule: SchoolSchedule) {
        cancelAll(context)
        if (schedule.lessons.isEmpty()) return

        val manager = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val requestCodes = mutableSetOf<Int>()

        for (offset in 0..LOOK_AHEAD_DAYS) {
            val date = LocalDate.now().plusDays(offset)
            val dayLessons = schedule.lessons
                .filter { it.dayOfWeek == date.dayOfWeek.value }
                .sortedBy { it.startMinutes }

            dayLessons.forEachIndexed { index, lesson ->
                val start = date.atTime(LocalTime.of(lesson.startMinutes / 60, lesson.startMinutes % 60))
                    .minusMinutes(schedule.reminderMinutes.toLong())
                val end = date.atTime(LocalTime.of(lesson.endMinutes / 60, lesson.endMinutes % 60))
                val next = dayLessons.getOrNull(index + 1)

                if (start.isAfter(now)) {
                    val code = stableCode(date, lesson.id, EVENT_START)
                    scheduleOne(
                        manager,
                        context,
                        start,
                        alarmIntent(
                            context = context,
                            requestCode = code,
                            event = EVENT_START,
                            lesson = lesson,
                            next = null,
                            reminderMinutes = schedule.reminderMinutes,
                        ),
                    )
                    requestCodes += code
                }
                if (end.isAfter(now)) {
                    val code = stableCode(date, lesson.id, EVENT_END)
                    scheduleOne(
                        manager,
                        context,
                        end,
                        alarmIntent(
                            context = context,
                            requestCode = code,
                            event = EVENT_END,
                            lesson = lesson,
                            next = next,
                            reminderMinutes = schedule.reminderMinutes,
                        ),
                    )
                    requestCodes += code
                }
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IDS, requestCodes.map(Int::toString).toSet())
            .apply()
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private fun scheduleOne(
        manager: AlarmManager,
        context: Context,
        dateTime: LocalDateTime,
        pendingIntent: PendingIntent,
    ) {
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
    }

    private fun alarmIntent(
        context: Context,
        requestCode: Int,
        event: String,
        lesson: Lesson,
        next: Lesson?,
        reminderMinutes: Int,
    ): PendingIntent {
        val intent = Intent(context, LessonAlarmReceiver::class.java).apply {
            putExtra(EXTRA_EVENT, event)
            putExtra(EXTRA_LESSON_NAME, lesson.name)
            putExtra(EXTRA_ROOM, lesson.room)
            putExtra(EXTRA_NEXT_NAME, next?.name.orEmpty())
            putExtra(EXTRA_NEXT_TIME, next?.startLabel.orEmpty())
            putExtra(EXTRA_REMINDER_MINUTES, reminderMinutes)
            putExtra(EXTRA_NOTIFICATION_ID, requestCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAll(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { rawCode ->
            val code = rawCode.toIntOrNull() ?: return@forEach
            val intent = Intent(context, LessonAlarmReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                manager.cancel(pending)
                pending.cancel()
            }
        }
        prefs.edit().remove(KEY_IDS).apply()
    }

    private fun stableCode(date: LocalDate, lessonId: String, event: String): Int =
        "$date|$lessonId|$event".hashCode() and Int.MAX_VALUE
}

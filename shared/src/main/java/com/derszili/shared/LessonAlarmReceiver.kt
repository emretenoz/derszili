package com.derszili.shared

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class LessonAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT) ?: return
        val lessonName = intent.getStringExtra(AlarmScheduler.EXTRA_LESSON_NAME).orEmpty()
        val room = intent.getStringExtra(AlarmScheduler.EXTRA_ROOM).orEmpty()
        val nextName = intent.getStringExtra(AlarmScheduler.EXTRA_NEXT_NAME).orEmpty()
        val nextTime = intent.getStringExtra(AlarmScheduler.EXTRA_NEXT_TIME).orEmpty()
        val reminderMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER_MINUTES, 5)
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_NOTIFICATION_ID, lessonName.hashCode())

        if (event == AlarmScheduler.EVENT_START) {
            val location = if (room.isBlank()) "" else " • $room"
            val title = if (reminderMinutes == 0) "Şimdi: $lessonName" else "$reminderMinutes dk sonra: $lessonName"
            NotificationHelper.show(
                context,
                id,
                title,
                "Hazırlanma zamanı$location",
            )
        } else {
            val message = if (nextName.isBlank()) {
                "Bugünkü derslerin tamamlandı."
            } else {
                "Sıradaki: $nextName • $nextTime"
            }
            NotificationHelper.show(context, id, "$lessonName bitti", message)
        }

        // Ufku günde bir kez, son ders bitince ve yayın alıcısını bloklamadan uzat.
        if (event == AlarmScheduler.EVENT_END && nextName.isBlank()) {
            val pendingResult = goAsync()
            schedulerExecutor.execute {
                try {
                    AlarmScheduler.extend(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private val schedulerExecutor = Executors.newSingleThreadExecutor()
    }
}

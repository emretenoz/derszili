package com.derszili.shared

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    fun createChannel(context: Context, vibrationMode: VibrationMode = VibrationMode.NORMAL) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()
        val channel = NotificationChannel(
            channelId(vibrationMode),
            "Ders uyarıları • ${vibrationMode.label()}",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ders başlangıç ve bitiş hatırlatmaları"
            val pattern = vibrationMode.pattern()
            enableVibration(pattern != null)
            if (pattern != null) vibrationPattern = pattern
            setSound(null, audio)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        vibrationMode: VibrationMode = VibrationMode.NORMAL,
    ) {
        createChannel(context, vibrationMode)
        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, channelId(vibrationMode))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply {
                vibrationMode.pattern()?.let(::setVibrate)
                if (contentIntent != null) setContentIntent(contentIntent)
            }
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun channelId(mode: VibrationMode): String = "lesson_alerts_${mode.name.lowercase()}"

    private fun VibrationMode.pattern(): LongArray? = when (this) {
        VibrationMode.GENTLE -> longArrayOf(0, 120)
        VibrationMode.NORMAL -> longArrayOf(0, 250, 120, 250)
        VibrationMode.STRONG -> longArrayOf(0, 350, 140, 350, 140, 500)
        VibrationMode.OFF -> null
    }

    private fun VibrationMode.label(): String = when (this) {
        VibrationMode.GENTLE -> "Kısa"
        VibrationMode.NORMAL -> "Normal"
        VibrationMode.STRONG -> "Güçlü"
        VibrationMode.OFF -> "Sessiz"
    }
}

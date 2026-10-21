package com.derszili.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.derszili.shared.AlarmScheduler
import com.derszili.shared.Lesson
import com.derszili.shared.NotificationHelper
import com.derszili.shared.ScheduleStore
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.dayName
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationHelper.createChannel(this@MainActivity)
            readLatestSchedule()
        }
    }

    private fun readLatestSchedule() {
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            try {
                items.firstOrNull { it.uri.path == ScheduleDataService.PATH }?.let { item ->
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(ScheduleDataService.KEY_JSON)
                    val schedule = com.derszili.shared.scheduleFromJson(json)
                    ScheduleStore.save(this, schedule)
                    AlarmScheduler.schedule(this, schedule)
                }
            } finally {
                items.release()
            }
        }
    }
}

private val Purple = Color(0xFFB9A5FF)
private val CardPurple = Color(0xFF2D2250)

@Composable
private fun WearApp() {
    val context = LocalContext.current
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var schedule by remember { mutableStateOf(ScheduleStore.load(context)) }
    var notificationAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationAllowed = it }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            nowTick = System.currentTimeMillis()
            schedule = ScheduleStore.load(context)
        }
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 26.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    val now = LocalDateTime.now()
                    Text(
                        "%02d:%02d".format(now.hour, now.minute),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        "Ders Zili",
                        color = Purple,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (!notificationAllowed) {
                    item {
                        PermissionCard("Bildirim izni gerekli") {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }

                if (!AlarmScheduler.canScheduleExact(context)) {
                    item {
                        PermissionCard("Tam zamanlı alarmı aç") {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        }
                    }
                }

                val now = LocalDateTime.now()
                val today = schedule.lessons
                    .filter { it.dayOfWeek == now.dayOfWeek.value }
                    .sortedBy { it.startMinutes }
                val minute = now.hour * 60 + now.minute
                val current = today.firstOrNull { minute >= it.startMinutes && minute < it.endMinutes }
                val next = today.firstOrNull { it.startMinutes > minute }

                item {
                    StatusCard(current = current, next = next, schedule = schedule, nowTick = nowTick)
                }

                if (today.isEmpty()) {
                    item {
                        Text(
                            if (schedule.lessons.isEmpty()) "Programı telefondan ekle" else "Bugün ders yok 🎉",
                            textAlign = TextAlign.Center,
                            color = Color.LightGray,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else {
                    item { Text(dayName(now.dayOfWeek.value), color = Color.Gray) }
                    items(today, key = { it.id }) { lesson -> LessonRow(lesson, minute) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PermissionCard(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        backgroundPainter = androidx.wear.compose.material.CardDefaults.cardBackgroundPainter(Color(0xFF5A1A1A)),
    ) {
        Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusCard(current: Lesson?, next: Lesson?, schedule: SchoolSchedule, nowTick: Long) {
    @Suppress("UNUSED_VARIABLE")
    val refreshKey = nowTick
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        backgroundPainter = androidx.wear.compose.material.CardDefaults.cardBackgroundPainter(CardPurple),
    ) {
        when {
            current != null -> {
                Text("ŞİMDİ", color = Purple, fontWeight = FontWeight.Bold)
                Text(current.name, fontWeight = FontWeight.Bold)
                Text("${current.startLabel}–${current.endLabel}${roomSuffix(current)}")
            }
            next != null -> {
                Text("SIRADAKİ", color = Purple, fontWeight = FontWeight.Bold)
                Text(next.name, fontWeight = FontWeight.Bold)
                Text("${next.startLabel}${roomSuffix(next)}")
            }
            schedule.lessons.isNotEmpty() -> {
                Text("Bugün tamamlandı", fontWeight = FontWeight.Bold)
                Text("Yarın görüşürüz")
            }
            else -> {
                Text("Program bekleniyor", fontWeight = FontWeight.Bold)
                Text("Telefondaki Ders Zili'ni aç")
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, currentMinute: Int) {
    val active = currentMinute in lesson.startMinutes until lesson.endMinutes
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(lesson.startLabel, color = if (active) Purple else Color.Gray)
        Text(lesson.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun roomSuffix(lesson: Lesson): String = if (lesson.room.isBlank()) "" else " • ${lesson.room}"

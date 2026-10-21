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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.derszili.shared.AlarmScheduler
import com.derszili.shared.CountdownKind
import com.derszili.shared.Lesson
import com.derszili.shared.NotificationHelper
import com.derszili.shared.ScheduleStore
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.countdownAt
import com.derszili.shared.dayName
import com.derszili.shared.remainingTimeLabel
import com.derszili.shared.scheduleFromJsonOrNull
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationHelper.createChannel(
                this@MainActivity,
                ScheduleStore.load(this@MainActivity).vibrationMode,
            )
            readLatestSchedule()
        }
    }

    private fun readLatestSchedule() {
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            val latest = try {
                items
                    .filter { it.uri.path == ScheduleDataService.PATH }
                    .mapNotNull { item ->
                        val json = DataMapItem.fromDataItem(item).dataMap
                            .getString(ScheduleDataService.KEY_JSON)
                        scheduleFromJsonOrNull(json)
                    }
                    .maxByOrNull { it.updatedAt }
            } finally {
                items.release()
            }

            if (latest != null && latest.updatedAt > ScheduleStore.load(this).updatedAt) {
                lifecycleScope.launch(Dispatchers.IO) {
                    ScheduleStore.save(this@MainActivity, latest)
                    NotificationHelper.createChannel(this@MainActivity, latest.vibrationMode)
                    AlarmScheduler.schedule(this@MainActivity, latest)
                }
            }
        }
    }
}

private val Accent = Color(0xFF7BE7C4)
private val SoftPurple = Color(0xFFC7B8FF)
private val CardPurple = Color(0xFF2A2344)
private val RowBackground = Color(0xFF181525)
private val Muted = Color(0xFFA9A2B8)

@Composable
private fun WearApp() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
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
            now = LocalDateTime.now()
            schedule = ScheduleStore.load(context)
            delay(15_000)
        }
    }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color(0xFF090810))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 24.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { WatchHeader(now) }

                if (!notificationAllowed) {
                    item {
                        PermissionCard("Bildirim iznini aç") {
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

                item { CountdownCard(schedule = schedule, now = now) }

                val today = schedule.lessons
                    .filter { it.dayOfWeek == now.dayOfWeek.value }
                    .sortedBy { it.startMinutes }
                val minute = now.hour * 60 + now.minute

                if (today.isEmpty()) {
                    item {
                        Text(
                            if (schedule.lessons.isEmpty()) "Programı telefondan ekle" else "Bugün ders yok 🎉",
                            textAlign = TextAlign.Center,
                            color = Muted,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    item {
                        Text(
                            "${dayName(now.dayOfWeek.value)} • ${today.size} ders",
                            color = Muted,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    items(today, key = { it.id }) { lesson -> LessonRow(lesson, minute) }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun WatchHeader(now: LocalDateTime) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "%02d:%02d".format(now.hour, now.minute),
            color = Color.White,
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold,
        )
        Text("DERS ZİLİ", color = SoftPurple, style = MaterialTheme.typography.caption2)
    }
}

@Composable
private fun PermissionCard(label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        backgroundPainter = androidx.wear.compose.material.CardDefaults.cardBackgroundPainter(Color(0xFF642C36)),
    ) {
        Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CountdownCard(schedule: SchoolSchedule, now: LocalDateTime) {
    val countdown = schedule.countdownAt(now)
    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        backgroundPainter = androidx.wear.compose.material.CardDefaults.cardBackgroundPainter(CardPurple),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                countdown == null && schedule.lessons.isEmpty() -> {
                    Text("PROGRAM BEKLENİYOR", color = Accent, fontWeight = FontWeight.Bold)
                    Text("Telefondan ilk dersini ekle", color = Color.White, textAlign = TextAlign.Center)
                }
                countdown == null -> {
                    Text("PROGRAM TAMAMLANDI", color = Accent, fontWeight = FontWeight.Bold)
                    Text("Yaklaşan ders yok", color = Color.White)
                }
                else -> {
                    Text(
                        if (countdown.kind == CountdownKind.UNTIL_END) "BİTİŞE" else "DERS BAŞLANGICINA",
                        color = Accent,
                        style = MaterialTheme.typography.caption1,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        remainingTimeLabel(countdown.remainingMinutes),
                        color = Color.White,
                        style = MaterialTheme.typography.title2,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        countdown.lesson.name,
                        color = SoftPurple,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${countdown.lesson.startLabel}–${countdown.lesson.endLabel}${roomSuffix(countdown.lesson)}",
                        color = Muted,
                        style = MaterialTheme.typography.caption2,
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, currentMinute: Int) {
    val active = currentMinute in lesson.startMinutes until lesson.endMinutes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF31314C) else RowBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (active) Accent else Color(0xFF514B61))
                .padding(4.dp)
        )
        Text(lesson.startLabel, color = if (active) Accent else Muted)
        Text(
            lesson.name,
            color = Color.White,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun roomSuffix(lesson: Lesson): String = if (lesson.room.isBlank()) "" else " • ${lesson.room}"

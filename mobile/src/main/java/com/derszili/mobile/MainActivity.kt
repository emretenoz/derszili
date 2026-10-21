package com.derszili.mobile

import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.derszili.shared.Lesson
import com.derszili.shared.ScheduleStore
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.dayName
import com.derszili.shared.findOverlap
import com.derszili.shared.minutesToLabel
import java.time.LocalDate
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DersZiliApp() }
    }
}

private val AppPurple = Color(0xFF6750A4)
private val PageBackground = Color(0xFFF7F7FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DersZiliApp() {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf(ScheduleStore.load(context)) }
    var selectedDay by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var syncText by remember { mutableStateOf("Eşitlemeye hazır") }

    fun updateSyncText(result: ScheduleSync.Result) {
        Handler(Looper.getMainLooper()).post {
            syncText = when (result) {
                ScheduleSync.Result.SENT_TO_CONNECTED_NETWORK -> "Saat ağına gönderildi"
                ScheduleSync.Result.QUEUED_UNTIL_CONNECTED -> "Saat çevrimdışı • sıraya alındı"
                ScheduleSync.Result.FAILED -> "Gönderilemedi • tekrar dene"
            }
        }
    }

    fun persist(updated: SchoolSchedule) {
        schedule = updated.copy(updatedAt = maxOf(System.currentTimeMillis(), schedule.updatedAt + 1))
        ScheduleStore.save(context, schedule)
        syncText = "Eşitleniyor…"
        ScheduleSync.send(context, schedule, ::updateSyncText)
    }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(PageBackground),
            containerColor = PageBackground,
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground),
                    title = {
                        Column {
                            Text("Ders Zili", fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Watch, null, modifier = Modifier.width(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(syncText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = {
                                syncText = "Eşitleniyor…"
                                ScheduleSync.send(context, schedule, ::updateSyncText)
                            },
                            modifier = Modifier.padding(end = 12.dp),
                        ) { Text("Eşitle") }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { editingLesson = null; showEditor = true },
                    containerColor = AppPurple,
                    contentColor = Color.White,
                ) { Icon(Icons.Default.Add, contentDescription = "Ders ekle") }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .fillMaxSize(),
            ) {
                ReminderCard(
                    minutes = schedule.reminderMinutes,
                    onChange = { persist(schedule.copy(reminderMinutes = it)) },
                )
                DayPicker(selectedDay = selectedDay, onSelect = { selectedDay = it })
                val dayLessons = schedule.lessons
                    .filter { it.dayOfWeek == selectedDay }
                    .sortedBy { it.startMinutes }
                if (dayLessons.isEmpty()) {
                    EmptyDay(dayName(selectedDay))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(dayLessons, key = { it.id }) { lesson ->
                            LessonCard(
                                lesson = lesson,
                                onEdit = { editingLesson = lesson; showEditor = true },
                                onDelete = {
                                    persist(schedule.copy(lessons = schedule.lessons.filterNot { it.id == lesson.id }))
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showEditor) {
            LessonEditor(
                initial = editingLesson,
                defaultDay = selectedDay,
                existingLessons = schedule.lessons,
                onDismiss = { showEditor = false },
                onSave = { lesson ->
                    val updated = if (editingLesson == null) {
                        schedule.lessons + lesson
                    } else {
                        schedule.lessons.map { if (it.id == lesson.id) lesson else it }
                    }
                    selectedDay = lesson.dayOfWeek
                    showEditor = false
                    persist(schedule.copy(lessons = updated))
                },
            )
        }
    }
}

@Composable
private fun ReminderCard(minutes: Int, onChange: (Int) -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = AppPurple)
                Spacer(Modifier.width(8.dp))
                Text("Ders öncesi uyarı", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15).forEach { value ->
                    FilterChip(
                        selected = minutes == value,
                        onClick = { onChange(value) },
                        label = { Text(if (value == 0) "Tam vaktinde" else "$value dk") },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPicker(selectedDay: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).forEach { day ->
            FilterChip(
                selected = day == selectedDay,
                onClick = { onSelect(day) },
                label = { Text(dayName(day).take(3)) },
            )
        }
    }
}

@Composable
private fun EmptyDay(day: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$day günü boş", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Text("+ düğmesiyle ilk dersi ekle", color = Color.Gray)
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp)) {
                Text(lesson.startLabel, fontWeight = FontWeight.Bold, color = AppPurple)
                Text(lesson.endLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(Modifier.weight(1f)) {
                Text(lesson.name, fontWeight = FontWeight.SemiBold)
                if (lesson.room.isNotBlank()) Text(lesson.room, color = Color.Gray)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Düzenle") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Sil", tint = Color(0xFFB3261E)) }
        }
    }
}

@Composable
private fun LessonEditor(
    initial: Lesson?,
    defaultDay: Int,
    existingLessons: List<Lesson>,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var day by remember { mutableIntStateOf(initial?.dayOfWeek ?: defaultDay) }
    var start by remember { mutableIntStateOf(initial?.startMinutes ?: 8 * 60 + 30) }
    var end by remember { mutableIntStateOf(initial?.endMinutes ?: 9 * 60 + 10) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickTime(value: Int, update: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> update(hour * 60 + minute) },
            value / 60,
            value % 60,
            true,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Ders ekle" else "Dersi düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Ders adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Sınıf / oda (isteğe bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { value ->
                        FilterChip(
                            selected = day == value,
                            onClick = { day = value },
                            label = { Text(dayName(value).take(3)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pickTime(start) { start = it } }, modifier = Modifier.weight(1f)) {
                        Text("Başlangıç ${minutesToLabel(start)}")
                    }
                    OutlinedButton(onClick = { pickTime(end) { end = it } }, modifier = Modifier.weight(1f)) {
                        Text("Bitiş ${minutesToLabel(end)}")
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                Text("Kaydedince program saate otomatik gönderilir.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Ders adını yazmalısın."
                    end <= start -> error = "Bitiş saati başlangıçtan sonra olmalı."
                    else -> {
                        val candidate = Lesson(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            room = room.trim(),
                            dayOfWeek = day,
                            startMinutes = start,
                            endMinutes = end,
                        )
                        val overlap = SchoolSchedule(lessons = existingLessons).findOverlap(candidate)
                        if (overlap != null) {
                            error = "Bu saat aralığı ${overlap.name} dersiyle çakışıyor."
                        } else {
                            onSave(candidate)
                        }
                    }
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

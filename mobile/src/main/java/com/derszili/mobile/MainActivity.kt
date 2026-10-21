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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.derszili.shared.CountdownKind
import com.derszili.shared.Lesson
import com.derszili.shared.ScheduleStore
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.VibrationMode
import com.derszili.shared.countdownAt
import com.derszili.shared.dayName
import com.derszili.shared.dayShortName
import com.derszili.shared.findOverlap
import com.derszili.shared.minutesToLabel
import com.derszili.shared.remainingTimeLabel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DersZiliApp() }
    }
}

private val DeepIndigo = Color(0xFF201A3D)
private val AppPurple = Color(0xFF6750A4)
private val AccentMint = Color(0xFF73E2C1)
private val PageBackground = Color(0xFFF7F5FC)
private val MutedText = Color(0xFF6F6A79)

private val AppColors = lightColorScheme(
    primary = AppPurple,
    onPrimary = Color.White,
    secondary = Color(0xFF356A5D),
    background = PageBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFECE8F4),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DersZiliApp() {
    val context = LocalContext.current
    var schedule by remember { mutableStateOf(ScheduleStore.load(context)) }
    var selectedDay by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }
    var draftLesson by remember { mutableStateOf<Lesson?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var syncText by remember { mutableStateOf("Eşitlemeye hazır") }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }

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

    fun nextDraft(day: Int): Lesson {
        val lastEnd = schedule.lessons
            .filter { it.dayOfWeek == day }
            .maxOfOrNull { it.endMinutes }
        val proposedStart = lastEnd?.plus(10) ?: (8 * 60 + 30)
        val start = if (proposedStart + 40 < 24 * 60) proposedStart else 8 * 60 + 30
        return Lesson(
            id = UUID.randomUUID().toString(),
            name = "",
            dayOfWeek = day,
            startMinutes = start,
            endMinutes = start + 40,
        )
    }

    fun openNewLesson(day: Int) {
        editingLesson = null
        draftLesson = nextDraft(day)
        showEditor = true
    }

    fun openCopy(lesson: Lesson) {
        val nextDay = if (lesson.dayOfWeek == 7) 1 else lesson.dayOfWeek + 1
        editingLesson = null
        draftLesson = lesson.copy(id = UUID.randomUUID().toString(), dayOfWeek = nextDay)
        showEditor = true
    }

    MaterialTheme(colorScheme = AppColors) {
        Scaffold(
            modifier = Modifier.fillMaxSize().background(PageBackground),
            containerColor = PageBackground,
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepIndigo,
                        titleContentColor = Color.White,
                    ),
                    title = {
                        Column {
                            Text("Ders Zili", fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Watch,
                                    contentDescription = null,
                                    tint = AccentMint,
                                    modifier = Modifier.width(15.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    syncText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD4CCE8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                    onClick = { openNewLesson(selectedDay) },
                    containerColor = AppPurple,
                    contentColor = Color.White,
                ) { Icon(Icons.Default.Add, contentDescription = "Ders ekle") }
            },
        ) { padding ->
            val dayLessons = schedule.lessons
                .filter { it.dayOfWeek == selectedDay }
                .sortedBy { it.startMinutes }

            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .navigationBarsPadding()
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { CountdownCard(schedule = schedule, now = now) }
                item {
                    AlertSettingsCard(
                        minutes = schedule.reminderMinutes,
                        vibrationMode = schedule.vibrationMode,
                        onMinutesChange = { persist(schedule.copy(reminderMinutes = it)) },
                        onVibrationChange = { persist(schedule.copy(vibrationMode = it)) },
                    )
                }
                item { DayPicker(selectedDay = selectedDay, onSelect = { selectedDay = it }) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(dayName(selectedDay), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${dayLessons.size} ders", color = MutedText)
                        }
                        FilledTonalButton(onClick = { openNewLesson(selectedDay) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ders ekle")
                        }
                    }
                }

                if (dayLessons.isEmpty()) {
                    item { EmptyDay(dayName(selectedDay), onAdd = { openNewLesson(selectedDay) }) }
                } else {
                    items(dayLessons, key = { it.id }) { lesson ->
                        LessonCard(
                            lesson = lesson,
                            onCopy = { openCopy(lesson) },
                            onEdit = {
                                editingLesson = lesson
                                draftLesson = lesson
                                showEditor = true
                            },
                            onDelete = {
                                persist(schedule.copy(lessons = schedule.lessons.filterNot { it.id == lesson.id }))
                            },
                        )
                    }
                }
            }
        }

        if (showEditor && draftLesson != null) {
            LessonEditor(
                initial = editingLesson,
                draft = draftLesson!!,
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
private fun CountdownCard(schedule: SchoolSchedule, now: LocalDateTime) {
    val countdown = schedule.countdownAt(now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DeepIndigo, contentColor = Color.White),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                when (countdown?.kind) {
                    CountdownKind.UNTIL_END -> "ŞİMDİ DERSTE"
                    CountdownKind.UNTIL_START -> "SIRADAKİ DERS"
                    null -> "PROGRAM ÖZETİ"
                },
                color = AccentMint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            if (countdown == null) {
                Text(
                    if (schedule.lessons.isEmpty()) "İlk dersini ekleyerek başla" else "Yaklaşan ders bulunmuyor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    remainingTimeLabel(countdown.remainingMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (countdown.kind == CountdownKind.UNTIL_END) {
                        "${countdown.lesson.name} dersinin bitmesine"
                    } else {
                        "${countdown.lesson.name} dersinin başlamasına"
                    },
                    color = Color(0xFFE5DFF3),
                )
                val dayPrefix = if (countdown.target.toLocalDate() == now.toLocalDate()) {
                    "Bugün"
                } else {
                    dayName(countdown.target.dayOfWeek.value)
                }
                Text(
                    "$dayPrefix • ${countdown.lesson.startLabel}–${countdown.lesson.endLabel}" +
                        if (countdown.lesson.room.isBlank()) "" else " • ${countdown.lesson.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBFB5D5),
                )
            }
        }
    }
}

@Composable
private fun AlertSettingsCard(
    minutes: Int,
    vibrationMode: VibrationMode,
    onMinutesChange: (Int) -> Unit,
    onVibrationChange: (VibrationMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AppPurple)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Ders uyarıları", fontWeight = FontWeight.Bold)
                    Text("Ne zaman ve nasıl titreşsin?", style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
            }
            Text("Uyarı zamanı", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 1, 3, 5, 10, 15, 20, 30, 45, 60).forEach { value ->
                    FilterChip(
                        selected = minutes == value,
                        onClick = { onMinutesChange(value) },
                        label = { Text(if (value == 0) "Şimdi" else "$value dk") },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFECE8F4))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Vibration, contentDescription = null, tint = AppPurple)
                Spacer(Modifier.width(8.dp))
                Text("Titreşim", style = MaterialTheme.typography.labelLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VibrationMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vibrationMode == mode,
                        onClick = { onVibrationChange(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPicker(selectedDay: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).forEach { day ->
            FilterChip(
                selected = day == selectedDay,
                onClick = { onSelect(day) },
                label = { Text(dayShortName(day)) },
            )
        }
    }
}

@Composable
private fun EmptyDay(day: String, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEAF8)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("$day günü boş", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("İlk dersin saati otomatik hazırlanır.", color = MutedText)
            Button(onClick = onAdd) { Text("İlk dersi ekle") }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(72.dp)) {
                Text(lesson.startLabel, fontWeight = FontWeight.Bold, color = AppPurple)
                Text(lesson.endLabel, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Box(Modifier.width(3.dp).height(38.dp).background(AccentMint, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (lesson.room.isBlank()) "Oda belirtilmedi" else lesson.room,
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Başka güne kopyala", tint = AppPurple) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Düzenle") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Sil", tint = Color(0xFFB3261E)) }
        }
    }
}

@Composable
private fun LessonEditor(
    initial: Lesson?,
    draft: Lesson,
    existingLessons: List<Lesson>,
    onDismiss: () -> Unit,
    onSave: (Lesson) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(draft.name) }
    var room by remember { mutableStateOf(draft.room) }
    var day by remember { mutableIntStateOf(draft.dayOfWeek) }
    var start by remember { mutableIntStateOf(draft.startMinutes) }
    var end by remember { mutableIntStateOf(draft.endMinutes) }
    var error by remember { mutableStateOf<String?>(null) }
    val isCopy = initial == null && draft.name.isNotBlank()

    fun pickStart() {
        val duration = (end - start).coerceAtLeast(30)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                start = hour * 60 + minute
                end = (start + duration).coerceAtMost(23 * 60 + 59)
                error = null
            },
            start / 60,
            start % 60,
            true,
        ).show()
    }

    fun pickEnd() {
        TimePickerDialog(
            context,
            { _, hour, minute -> end = hour * 60 + minute; error = null },
            end / 60,
            end % 60,
            true,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial != null -> "Dersi düzenle"
                    isCopy -> "Dersi başka güne kopyala"
                    else -> "Yeni ders"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Ders adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial == null && name.isBlank()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Matematik", "Türkçe", "İngilizce", "Fizik", "Kimya", "Tarih").forEach { subject ->
                            FilterChip(selected = false, onClick = { name = subject }, label = { Text(subject) })
                        }
                    }
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Sınıf / oda (isteğe bağlı)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Gün", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { value ->
                        FilterChip(
                            selected = day == value,
                            onClick = { day = value; error = null },
                            label = { Text(dayShortName(value)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = ::pickStart, modifier = Modifier.weight(1f)) {
                        Text("Başlangıç\n${minutesToLabel(start)}")
                    }
                    OutlinedButton(onClick = ::pickEnd, modifier = Modifier.weight(1f)) {
                        Text("Bitiş\n${minutesToLabel(end)}")
                    }
                }
                Text("Ders süresi", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 40, 45, 60).forEach { duration ->
                        FilterChip(
                            selected = end - start == duration,
                            onClick = {
                                end = (start + duration).coerceAtMost(23 * 60 + 59)
                                error = null
                            },
                            label = { Text("$duration dk") },
                        )
                    }
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                Text("Kaydedince program saate otomatik gönderilir.", style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    name.isBlank() -> error = "Ders adını yazmalısın."
                    end <= start -> error = "Bitiş saati başlangıçtan sonra olmalı."
                    else -> {
                        val candidate = Lesson(
                            id = initial?.id ?: draft.id,
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
            }) { Text(if (isCopy) "Kopyala" else "Kaydet") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun VibrationMode.label(): String = when (this) {
    VibrationMode.GENTLE -> "Kısa"
    VibrationMode.NORMAL -> "Normal"
    VibrationMode.STRONG -> "Güçlü"
    VibrationMode.OFF -> "Sessiz"
}

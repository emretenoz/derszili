package com.derszili.shared

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

enum class VibrationMode {
    GENTLE,
    NORMAL,
    STRONG,
    OFF;

    companion object {
        fun fromStorage(value: String?): VibrationMode =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}

enum class CountdownKind {
    UNTIL_START,
    UNTIL_END,
}

data class ScheduleCountdown(
    val lesson: Lesson,
    val kind: CountdownKind,
    val target: LocalDateTime,
    val remainingMinutes: Long,
)

data class Lesson(
    val id: String,
    val name: String,
    val room: String = "",
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
) {
    val startLabel: String get() = minutesToLabel(startMinutes)
    val endLabel: String get() = minutesToLabel(endMinutes)
}

data class SchoolSchedule(
    val lessons: List<Lesson> = emptyList(),
    val reminderMinutes: Int = 5,
    val vibrationMode: VibrationMode = VibrationMode.NORMAL,
    val updatedAt: Long = 0L,
)

fun SchoolSchedule.toJson(): String = JSONObject().apply {
    put("reminderMinutes", reminderMinutes)
    put("vibrationMode", vibrationMode.name)
    put("updatedAt", updatedAt)
    put("lessons", JSONArray().apply {
        lessons.forEach { lesson ->
            put(JSONObject().apply {
                put("id", lesson.id)
                put("name", lesson.name)
                put("room", lesson.room)
                put("dayOfWeek", lesson.dayOfWeek)
                put("startMinutes", lesson.startMinutes)
                put("endMinutes", lesson.endMinutes)
            })
        }
    })
}.toString()

fun scheduleFromJsonOrNull(raw: String?): SchoolSchedule? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val json = JSONObject(raw)
        val array = json.getJSONArray("lessons")
        val ids = mutableSetOf<String>()
        val lessons = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val lesson = Lesson(
                    id = item.getString("id").trim(),
                    name = item.getString("name").trim(),
                    room = item.optString("room").trim(),
                    dayOfWeek = item.getInt("dayOfWeek"),
                    startMinutes = item.getInt("startMinutes"),
                    endMinutes = item.getInt("endMinutes"),
                )
                require(lesson.id.isNotBlank() && ids.add(lesson.id))
                require(lesson.name.isNotBlank())
                require(lesson.dayOfWeek in 1..7)
                require(lesson.startMinutes in 0 until 24 * 60)
                require(lesson.endMinutes in 1..24 * 60)
                require(lesson.endMinutes > lesson.startMinutes)
                add(lesson)
            }
        }
        SchoolSchedule(
            lessons = lessons,
            reminderMinutes = json.optInt("reminderMinutes", 5).coerceIn(0, 60),
            vibrationMode = VibrationMode.fromStorage(json.optString("vibrationMode")),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }.getOrNull()
}

fun scheduleFromJson(raw: String?): SchoolSchedule =
    scheduleFromJsonOrNull(raw) ?: SchoolSchedule()

fun SchoolSchedule.findOverlap(candidate: Lesson): Lesson? = lessons.firstOrNull { lesson ->
    lesson.id != candidate.id &&
        lesson.dayOfWeek == candidate.dayOfWeek &&
        candidate.startMinutes < lesson.endMinutes &&
        candidate.endMinutes > lesson.startMinutes
}

fun minutesToLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun dayName(day: Int): String = when (day) {
    1 -> "Pazartesi"
    2 -> "Salı"
    3 -> "Çarşamba"
    4 -> "Perşembe"
    5 -> "Cuma"
    6 -> "Cumartesi"
    else -> "Pazar"
}

fun dayShortName(day: Int): String = when (day) {
    1 -> "Pzt"
    2 -> "Sal"
    3 -> "Çar"
    4 -> "Per"
    5 -> "Cum"
    6 -> "Cmt"
    else -> "Paz"
}

fun Lesson.startAt(dateTime: LocalDateTime): LocalDateTime =
    dateTime.toLocalDate().atTime(LocalTime.of(startMinutes / 60, startMinutes % 60))

fun Lesson.endAt(dateTime: LocalDateTime): LocalDateTime =
    dateTime.toLocalDate().atTime(LocalTime.of(endMinutes / 60, endMinutes % 60))

fun SchoolSchedule.lessonsFor(day: DayOfWeek): List<Lesson> =
    lessons.filter { it.dayOfWeek == day.value }.sortedBy { it.startMinutes }

fun SchoolSchedule.nextLessonAfter(day: DayOfWeek, minuteOfDay: Int): Lesson? =
    lessonsFor(day).firstOrNull { it.startMinutes >= minuteOfDay }

fun SchoolSchedule.countdownAt(now: LocalDateTime): ScheduleCountdown? {
    val current = lessonsFor(now.dayOfWeek).firstOrNull { lesson ->
        val start = now.toLocalDate().atTime(lesson.startMinutes / 60, lesson.startMinutes % 60)
        val end = now.toLocalDate().atTime(lesson.endMinutes / 60, lesson.endMinutes % 60)
        !now.isBefore(start) && now.isBefore(end)
    }
    if (current != null) {
        val target = current.endAt(now)
        return ScheduleCountdown(
            lesson = current,
            kind = CountdownKind.UNTIL_END,
            target = target,
            remainingMinutes = roundedUpMinutes(now, target),
        )
    }

    val next = (0L..7L).flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset)
        lessons
            .asSequence()
            .filter { it.dayOfWeek == date.dayOfWeek.value }
            .map { lesson -> lesson to date.atTime(lesson.startMinutes / 60, lesson.startMinutes % 60) }
            .filter { (_, start) -> start.isAfter(now) }
            .toList()
    }.minByOrNull { (_, start) -> start } ?: return null

    return ScheduleCountdown(
        lesson = next.first,
        kind = CountdownKind.UNTIL_START,
        target = next.second,
        remainingMinutes = roundedUpMinutes(now, next.second),
    )
}

fun remainingTimeLabel(minutes: Long): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val days = safeMinutes / (24 * 60)
    val hours = (safeMinutes % (24 * 60)) / 60
    val mins = safeMinutes % 60
    return when {
        days > 0 -> if (hours > 0) "$days gün $hours sa" else "$days gün"
        hours > 0 -> if (mins > 0) "$hours sa $mins dk" else "$hours sa"
        else -> "$mins dk"
    }
}

private fun roundedUpMinutes(from: LocalDateTime, to: LocalDateTime): Long {
    val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
    return (seconds + 59) / 60
}

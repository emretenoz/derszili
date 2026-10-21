package com.derszili.shared

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

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
    val updatedAt: Long = System.currentTimeMillis(),
)

fun SchoolSchedule.toJson(): String = JSONObject().apply {
    put("reminderMinutes", reminderMinutes)
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

fun scheduleFromJson(raw: String?): SchoolSchedule {
    if (raw.isNullOrBlank()) return SchoolSchedule()
    return runCatching {
        val json = JSONObject(raw)
        val array = json.optJSONArray("lessons") ?: JSONArray()
        val lessons = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    Lesson(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        room = item.optString("room"),
                        dayOfWeek = item.getInt("dayOfWeek"),
                        startMinutes = item.getInt("startMinutes"),
                        endMinutes = item.getInt("endMinutes"),
                    )
                )
            }
        }
        SchoolSchedule(
            lessons = lessons,
            reminderMinutes = json.optInt("reminderMinutes", 5).coerceIn(0, 60),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }.getOrDefault(SchoolSchedule())
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

fun Lesson.startAt(dateTime: LocalDateTime): LocalDateTime =
    dateTime.toLocalDate().atTime(LocalTime.of(startMinutes / 60, startMinutes % 60))

fun Lesson.endAt(dateTime: LocalDateTime): LocalDateTime =
    dateTime.toLocalDate().atTime(LocalTime.of(endMinutes / 60, endMinutes % 60))

fun SchoolSchedule.lessonsFor(day: DayOfWeek): List<Lesson> =
    lessons.filter { it.dayOfWeek == day.value }.sortedBy { it.startMinutes }

fun SchoolSchedule.nextLessonAfter(day: DayOfWeek, minuteOfDay: Int): Lesson? =
    lessonsFor(day).firstOrNull { it.startMinutes >= minuteOfDay }

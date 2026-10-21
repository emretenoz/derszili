package com.derszili.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDateTime

class ScheduleModelTest {
    private val math = Lesson(
        id = "math",
        name = "Matematik",
        room = "A-101",
        dayOfWeek = 1,
        startMinutes = 9 * 60,
        endMinutes = 10 * 60,
    )

    @Test
    fun `valid schedule survives json round trip`() {
        val schedule = SchoolSchedule(
            lessons = listOf(math),
            reminderMinutes = 10,
            vibrationMode = VibrationMode.STRONG,
            updatedAt = 1234L,
        )

        assertEquals(schedule, scheduleFromJsonOrNull(schedule.toJson()))
    }

    @Test
    fun `malformed or unsafe data is rejected`() {
        assertNull(scheduleFromJsonOrNull("not-json"))
        assertNull(scheduleFromJsonOrNull("""{"reminderMinutes":5}"""))
        assertNull(
            scheduleFromJsonOrNull(
                """{"lessons":[{"id":"x","name":"Bozuk","dayOfWeek":1,"startMinutes":600,"endMinutes":500}]}"""
            )
        )
    }

    @Test
    fun `overlapping lesson is found on same day`() {
        val candidate = math.copy(id = "physics", name = "Fizik", startMinutes = 9 * 60 + 30)

        assertSame(math, SchoolSchedule(lessons = listOf(math)).findOverlap(candidate))
    }

    @Test
    fun `touching lessons and other days do not overlap`() {
        val after = math.copy(id = "physics", startMinutes = math.endMinutes, endMinutes = 11 * 60)
        val tomorrow = math.copy(id = "chemistry", dayOfWeek = 2, startMinutes = 9 * 60 + 30)
        val schedule = SchoolSchedule(lessons = listOf(math))

        assertNull(schedule.findOverlap(after))
        assertNull(schedule.findOverlap(tomorrow))
    }

    @Test
    fun `editing lesson does not overlap with itself`() {
        assertNull(SchoolSchedule(lessons = listOf(math)).findOverlap(math.copy(name = "Yeni ad")))
    }

    @Test
    fun `legacy schedule defaults to normal vibration`() {
        val raw = """{"lessons":[],"reminderMinutes":5,"updatedAt":12}"""

        assertEquals(VibrationMode.NORMAL, scheduleFromJsonOrNull(raw)?.vibrationMode)
    }

    @Test
    fun `countdown shows lesson end while lesson is active`() {
        val now = LocalDateTime.of(2024, 1, 1, 9, 30)

        val countdown = SchoolSchedule(lessons = listOf(math)).countdownAt(now)

        assertEquals(CountdownKind.UNTIL_END, countdown?.kind)
        assertEquals(math, countdown?.lesson)
        assertEquals(30L, countdown?.remainingMinutes)
    }

    @Test
    fun `countdown finds next lesson across days`() {
        val tuesdayLesson = math.copy(id = "physics", name = "Fizik", dayOfWeek = 2)
        val now = LocalDateTime.of(2024, 1, 1, 18, 0)

        val countdown = SchoolSchedule(lessons = listOf(tuesdayLesson)).countdownAt(now)

        assertEquals(CountdownKind.UNTIL_START, countdown?.kind)
        assertEquals(tuesdayLesson, countdown?.lesson)
        assertEquals(15 * 60L, countdown?.remainingMinutes)
    }

    @Test
    fun `remaining time label stays compact`() {
        assertEquals("8 dk", remainingTimeLabel(8))
        assertEquals("1 sa 5 dk", remainingTimeLabel(65))
        assertEquals("2 gün 3 sa", remainingTimeLabel(3060))
    }

    @Test
    fun `short day names keep friday and saturday distinct`() {
        assertEquals("Cum", dayShortName(5))
        assertEquals("Cmt", dayShortName(6))
    }
}

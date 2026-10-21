package com.derszili.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

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
}

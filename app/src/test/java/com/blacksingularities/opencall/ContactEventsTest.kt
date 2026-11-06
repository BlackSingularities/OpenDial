package com.blacksingularities.opencall

import com.blacksingularities.opencall.data.ContactEvents
import com.blacksingularities.opencall.data.contactColumns
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactEventsTest {
    private fun day(y: Int, m: Int, d: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, 10, 30)
    }

    @Test
    fun todayAndAge() {
        assertEquals(0 to 36, ContactEvents.next("1990-05-12", day(2026, 5, 12)))
    }

    @Test
    fun laterThisYearAndNextYear() {
        assertEquals(3 to 36, ContactEvents.next("1990-05-15", day(2026, 5, 12)))
        // Już minęło – następne za rok.
        assertEquals(364 to 37, ContactEvents.next("1990-05-11", day(2026, 5, 12)))
    }

    @Test
    fun withoutYear() {
        assertEquals(1 to null, ContactEvents.next("--05-13", day(2026, 5, 12)))
    }

    @Test
    fun leapDayInCommonYear() {
        // 29 lutego w roku nieprzestępnym przypada 28 lutego.
        assertEquals(0 to 26, ContactEvents.next("2000-02-29", day(2026, 2, 28)))
    }

    @Test
    fun endOfMonthDoesNotRollOver() {
        assertEquals(0 to 10, ContactEvents.next("2016-01-31", day(2026, 1, 31)))
    }

    @Test
    fun unreadableDate() {
        assertNull(ContactEvents.next("jutro", day(2026, 5, 12)))
    }

    @Test
    fun zoomColumns() {
        assertEquals(1, contactColumns(1f))
        assertEquals(1, contactColumns(1.59f))
        assertEquals(4, contactColumns(1.6f))
        assertEquals(3, contactColumns(2.2f))
        assertEquals(2, contactColumns(3f))
    }
}

package com.garagedoor.monitor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorRulesTest {
    @Test
    fun idleAtTheFloorIsClosed() {
        assertEquals(DoorPhase.Closed, snapshot(0f, Travel.Idle).phase())
    }

    @Test
    fun idleAtTheTopIsOpen() {
        assertEquals(DoorPhase.Open, snapshot(1f, Travel.Idle).phase())
    }

    @Test
    fun idleBetweenIsPartial() {
        assertEquals(DoorPhase.Partial, snapshot(0.4f, Travel.Idle).phase())
    }

    @Test
    fun travelBeatsPosition() {
        assertEquals(DoorPhase.Closing, snapshot(0.8f, Travel.Closing).phase())
        assertEquals(DoorPhase.Opening, snapshot(0.1f, Travel.Opening).phase())
    }

    @Test
    fun offlineWinsOverEverything() {
        assertEquals(
            DoorPhase.Offline,
            DoorSnapshot(0f, Travel.Idle, 0L, 0L, online = false).phase(),
        )
    }

    @Test
    fun alertIsDueAtTheChosenMinuteAndNeverWhenOff() {
        val since = 1_000_000L
        assertFalse(isDue(since + 9 * 60_000L, since, 10))
        assertTrue(isDue(since + 10 * 60_000L, since, 10))
        assertFalse(isDue(since + 60 * 60_000L, since, 0))
        assertNull(deadlineEpochMs(since, 0))
    }

    @Test
    fun durationFormatDropsLeadingZeroHours() {
        assertEquals("12s", formatDuration(12_000L))
        assertEquals("4m 12s", formatDuration(4 * 60_000L + 12_000L))
        assertEquals("2h 5m", formatDuration(2 * 3_600_000L + 5 * 60_000L))
    }

    private fun snapshot(position: Float, travel: Travel) =
        DoorSnapshot(position, travel, 0L, 0L)
}

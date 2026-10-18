package com.garagedoor.monitor.controller

import com.garagedoor.monitor.model.Command
import com.garagedoor.monitor.model.DoorSnapshot
import com.garagedoor.monitor.model.Travel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerLinkTest {
    @Test
    fun blankAddressStaysOnThePracticeDoor() {
        assertNull(controllerBaseUrl("  "))
    }

    @Test
    fun bareAddressUsesTheWallUnitPort() {
        assertEquals("http://192.168.1.50:8080", controllerBaseUrl("192.168.1.50"))
    }

    @Test
    fun privateNetworkAddressUsesTheWallUnitPort() {
        assertEquals("http://100.100.20.3:8080", controllerBaseUrl("100.100.20.3"))
        assertEquals(
            "http://garage.tailnet.ts.net:8080",
            controllerBaseUrl("garage.tailnet.ts.net"),
        )
    }

    @Test
    fun explicitPortIsKept() {
        assertEquals("http://garage.local:9090", controllerBaseUrl("http://garage.local:9090/"))
    }

    @Test
    fun statusJsonBecomesAReading() {
        val reading = parseDoorReading("""{"position":0.4,"travel":"closing"}""")
        assertEquals(0.4f, reading!!.position, 0.001f)
        assertEquals(Travel.Closing, reading.travel)
    }

    @Test
    fun commandJsonUsesTheWallUnitWords() {
        assertEquals("""{"command":"open"}""", commandJson(Command.Open))
        assertEquals("""{"command":"stop"}""", commandJson(Command.Stop))
    }

    @Test
    fun aFreshReadingStartsTheOpenTimer() {
        val previous = DoorSnapshot(0f, Travel.Idle, 1L, 1L, online = false)
        val next = applyReading(previous, DoorReading(1f, Travel.Idle), now = 50L)
        assertEquals(50L, next.stateSinceEpochMs)
        assertEquals(true, next.online)
    }

    @Test
    fun anUnchangedOpenDoorKeepsItsTimer() {
        val previous = DoorSnapshot(1f, Travel.Idle, 10L, 10L, online = true)
        val next = applyReading(previous, DoorReading(1f, Travel.Idle), now = 80L)
        assertEquals(10L, next.stateSinceEpochMs)
    }
}

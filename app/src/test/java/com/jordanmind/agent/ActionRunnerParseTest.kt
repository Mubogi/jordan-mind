package com.jordanmind.agent

import com.jordanmind.agent.agent.ActionRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Time and duration parsing is the part of the agent most likely to fail quietly — a bad parse
 * sets an alarm at the wrong time rather than throwing. These are pure functions, so they are
 * tested directly without an Android runtime.
 */
class ActionRunnerParseTest {

    @Test
    fun `parses 24-hour times`() {
        assertEquals(18 to 30, ActionRunner.parseTime("18:30"))
        assertEquals(7 to 0, ActionRunner.parseTime("07:00"))
        assertEquals(0 to 5, ActionRunner.parseTime("0:05"))
    }

    @Test
    fun `parses am and pm`() {
        assertEquals(7 to 0, ActionRunner.parseTime("7am"))
        assertEquals(19 to 0, ActionRunner.parseTime("7pm"))
        assertEquals(12 to 15, ActionRunner.parseTime("12:15 pm"))
        assertEquals(0 to 30, ActionRunner.parseTime("12:30am"))
    }

    @Test
    fun `handles spacing, case and dots`() {
        assertEquals(19 to 0, ActionRunner.parseTime(" 7 PM "))
        assertEquals(19 to 30, ActionRunner.parseTime("7.30pm"))
    }

    @Test
    fun `reads a bare hour as on the hour`() {
        assertEquals(9 to 0, ActionRunner.parseTime("9"))
    }

    @Test
    fun `rejects impossible times`() {
        assertNull(ActionRunner.parseTime("25:00"))
        assertNull(ActionRunner.parseTime("10:75"))
        assertNull(ActionRunner.parseTime("sometime later"))
        assertNull(ActionRunner.parseTime(""))
    }

    @Test
    fun `parses durations in seconds`() {
        assertEquals(600, ActionRunner.parseDuration("10 minutes"))
        assertEquals(3600, ActionRunner.parseDuration("1 hour"))
        assertEquals(45, ActionRunner.parseDuration("45 seconds"))
        assertEquals(5400, ActionRunner.parseDuration("1 hour 30 minutes"))
        assertEquals(7260, ActionRunner.parseDuration("2h 1m"))
    }

    @Test
    fun `a bare number is treated as minutes`() {
        assertEquals(900, ActionRunner.parseDuration("15"))
    }

    @Test
    fun `rejects durations it cannot read`() {
        assertNull(ActionRunner.parseDuration("a while"))
        assertNull(ActionRunner.parseDuration(""))
    }
}

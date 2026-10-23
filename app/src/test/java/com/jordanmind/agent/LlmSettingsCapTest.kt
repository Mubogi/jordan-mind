package com.jordanmind.agent

import com.jordanmind.agent.engine.DeviceProfile
import com.jordanmind.agent.engine.LlmSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The clamp is the guard that stops a context sized for a flagship from being allocated on a 4 GB
 * phone, where the allocation is what gets the process killed.
 */
class LlmSettingsCapTest {

    @Test
    fun generousSettingsAreClampedOnALeanDevice() {
        val lean = DeviceProfile.of(ramMb = 3_772, cores = 8)
        val clamped = LlmSettings(maxTokens = 4096, contextBudget = 8192).cappedFor(lean)

        assertEquals(384, clamped.maxTokens)
        assertEquals(1024, clamped.contextBudget)
    }

    @Test
    fun settingsAlreadyWithinTheCeilingAreUntouched() {
        val capable = DeviceProfile.of(ramMb = 7_600, cores = 8)
        val modest = LlmSettings(maxTokens = 256, contextBudget = 512)

        assertEquals(modest, modest.cappedFor(capable))
    }

    @Test
    fun clampingNeverRaisesAValue() {
        val capable = DeviceProfile.of(ramMb = 7_600, cores = 8)
        val tiny = LlmSettings(maxTokens = 64, contextBudget = 128)
        val clamped = tiny.cappedFor(capable)

        assertEquals(64, clamped.maxTokens)
        assertEquals(128, clamped.contextBudget)
    }
}

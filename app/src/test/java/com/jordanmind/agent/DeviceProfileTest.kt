package com.jordanmind.agent

import com.jordanmind.agent.engine.DeviceProfile
import com.jordanmind.agent.engine.DeviceTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier detection decides the context size the engine allocates. Getting it wrong on a low-RAM
 * phone shows up as the app being killed mid-reply, so the boundaries are pinned here.
 */
class DeviceProfileTest {

    /** A 4 GB Redmi 15C reports about 3.7 GB of physical memory, not 4096 MB. */
    @Test
    fun fourGigabytePhoneIsLean() {
        val profile = DeviceProfile.of(ramMb = 3_772, cores = 8)
        assertEquals(DeviceTier.LEAN, profile.tier)
        assertEquals(1024, profile.contextBudget)
        assertEquals(384, profile.maxTokens)
        assertTrue("a 4 GB phone should skip the drifting backdrop", profile.reduceMotion)
    }

    @Test
    fun sixGigabytePhoneIsBalanced() {
        val profile = DeviceProfile.of(ramMb = 5_800, cores = 8)
        assertEquals(DeviceTier.BALANCED, profile.tier)
        assertEquals(1536, profile.contextBudget)
    }

    @Test
    fun eightGigabytePhoneIsCapable() {
        val profile = DeviceProfile.of(ramMb = 7_600, cores = 8)
        assertEquals(DeviceTier.CAPABLE, profile.tier)
        assertEquals(2048, profile.contextBudget)
    }

    @Test
    fun tierCeilingsNeverIncreaseWithRam() {
        val budgets = listOf(3_772, 5_800, 7_600)
            .map { DeviceProfile.of(it, 8).contextBudget }
        assertEquals(budgets.sorted(), budgets)
    }
}

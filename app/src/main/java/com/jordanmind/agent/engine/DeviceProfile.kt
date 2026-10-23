package com.jordanmind.agent.engine

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier { LEAN, BALANCED, CAPABLE }

/**
 * Capability profile of the phone the agent runs on, used to pick defaults that stay usable on a
 * budget device.
 *
 * The Redmi 15C class (Helio G81-Ultra, 4-8 GB) lands in [DeviceTier.LEAN] or
 * [DeviceTier.BALANCED]. The values below are deliberately conservative: a reply that arrives in
 * two seconds beats a longer one that heats the phone and gets the app killed.
 *
 * There is deliberately no thread-count setting. MediaPipe's own threadpool already reads
 * `/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq` and sizes itself to the fast cluster, and
 * the public API exposes no override, so a knob here would do nothing.
 */
data class DeviceProfile(
    val tier: DeviceTier,
    val totalRamMb: Int,
    val cores: Int,
    val maxTokens: Int,
    val contextBudget: Int,
    val reduceMotion: Boolean,
) {
    /** Short line for the settings panel, so the chosen defaults are not a mystery. */
    val summary: String
        get() = "${tier.name.lowercase()} · ${cores} cores · ${totalRamMb / 1024} GB RAM"

    companion object {

        /** `totalMem` is the physical figure, so a 4 GB phone reports roughly 3.7 GB. */
        private const val LEAN_RAM_MB = 4 * 1024
        private const val BALANCED_RAM_MB = 6 * 1024

        fun detect(context: Context): DeviceProfile =
            of(totalRamMb(context), Runtime.getRuntime().availableProcessors().coerceAtLeast(1))

        fun of(ramMb: Int, cores: Int): DeviceProfile {
            val tier = when {
                ramMb <= LEAN_RAM_MB -> DeviceTier.LEAN
                ramMb <= BALANCED_RAM_MB -> DeviceTier.BALANCED
                else -> DeviceTier.CAPABLE
            }
            return DeviceProfile(
                tier = tier,
                totalRamMb = ramMb,
                cores = cores,
                maxTokens = when (tier) {
                    DeviceTier.LEAN -> 384
                    DeviceTier.BALANCED -> 512
                    DeviceTier.CAPABLE -> 768
                },
                contextBudget = when (tier) {
                    DeviceTier.LEAN -> 1024
                    DeviceTier.BALANCED -> 1536
                    DeviceTier.CAPABLE -> 2048
                },
                // A 4 GB phone should not redraw a full-screen gradient every frame.
                reduceMotion = tier == DeviceTier.LEAN,
            )
        }

        private fun totalRamMb(context: Context): Int = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)).toInt()
        }.getOrDefault(BALANCED_RAM_MB)
    }
}

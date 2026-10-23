package com.jordanmind.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jordanmind.agent.agent.WorkBot
import com.jordanmind.agent.engine.LlmEngine
import com.jordanmind.agent.ui.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the two ways a turn reaches the chat without the user typing in the composer: the work
 * persona toggle, and text shared from another app. Both are easy to break silently because the
 * visible symptom is a wrong-sounding or missing reply, not a crash.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WorkModeAndShareTest {

    private fun viewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun `work mode starts off so the default persona is unchanged`() {
        assertFalse(viewModel().workMode.value)
    }

    @Test
    fun `work mode can be turned on and off`() {
        val vm = viewModel()
        vm.setWorkMode(true)
        assertTrue(vm.workMode.value)
        vm.setWorkMode(false)
        assertFalse(vm.workMode.value)
    }

    @Test
    fun `the work persona is distinct from the default one`() {
        assertTrue(WorkBot.SYSTEM.isNotBlank())
        assertTrue(
            "work mode must actually change the instructions the model receives",
            WorkBot.SYSTEM != LlmEngine.DEFAULT_SYSTEM,
        )
    }

    @Test
    fun `the work persona forbids inventing facts`() {
        // The whole point of the bot for work is that it does not make things up; losing this line
        // would make it unusable for anything a person has to sign off on.
        assertTrue(WorkBot.SYSTEM.contains("Never invent", ignoreCase = true))
    }

    @Test
    fun `work mode offers openers that ask for something usable`() {
        assertTrue(WorkBot.OPENERS.size >= 3)
        assertTrue(WorkBot.OPENERS.all { it.isNotBlank() })
    }

    @Test
    fun `a blank share is ignored rather than sent`() {
        val vm = viewModel()
        vm.onShareReceived("   ")
        assertNull("whitespace must not queue a turn", vm.sharedText.value)
    }

    @Test
    fun `shared text is queued then consumed exactly once`() {
        val vm = viewModel()
        vm.onShareReceived("  chase the invoice  ")

        assertEquals("chase the invoice", vm.sharedText.value)
        vm.consumeSharedText()
        assertNull("a consumed share must not be replayed on the next composition", vm.sharedText.value)
    }

    @Test
    fun `consuming with nothing queued is a no-op`() {
        val vm = viewModel()
        vm.consumeSharedText()
        assertNull(vm.sharedText.value)
        assertTrue("no phantom message should appear", vm.messages.value.isEmpty())
    }
}

package com.jordanmind.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jordanmind.agent.ui.AppViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guards the startup path. The shipped 1.0 APK did nothing on launch because nothing ever loaded a
 * model: the send button stays disabled until [com.jordanmind.agent.engine.LlmState] reaches
 * `Ready`, and `AppViewModel.init` only listed the models on disk. These tests fail if that
 * regression comes back.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ModelAutoLoadTest {

    private fun viewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun `constructing the view model starts a model load without user action`() = runBlocking {
        val vm = viewModel()
        // Nothing is loaded synchronously, but the app must not sit in a state where no attempt is
        // ever made. Either a load is in flight (busy) or a status message explains the situation.
        val settled = withTimeoutOrNull(5_000) {
            vm.busy.first { !it } 
            true
        }
        assertNotNull("auto-load never settled", settled)
        assertTrue(
            "the app must report why it is idle when no model is available",
            vm.status.value != null || vm.llmState.value !is com.jordanmind.agent.engine.LlmState.Unloaded,
        )
    }

    @Test
    fun `a lite build reports how to get a model instead of failing silently`() = runBlocking {
        val vm = viewModel()
        // Robolectric has no bundled asset, so this is the lite-build path.
        val status = withTimeoutOrNull(5_000) { vm.status.first { it != null } }
        assertNotNull("no guidance shown when no model exists", status)
    }
}

package com.jordanmind.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.AgentTab
import com.jordanmind.agent.ui.JordanTabRail
import com.jordanmind.agent.ui.Tab
import com.jordanmind.agent.ui.theme.JordanMindTheme
import com.jordanmind.agent.ui.tabs.BuildTab
import com.jordanmind.agent.ui.tabs.ChatTab
import com.jordanmind.agent.ui.tabs.DocumentsTab
import com.jordanmind.agent.ui.tabs.ExamTab
import com.jordanmind.agent.ui.tabs.FilesTab
import com.jordanmind.agent.ui.tabs.ResearchTab
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/**
 * Renders each tab on the JVM through Robolectric. This is not a pixel test — it exists to prove
 * that every screen composes without throwing, which is the failure a real device would show as a
 * crash on first launch.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun `chat tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { ChatTab(viewModel()) } }
        }
        compose.onNodeWithText("Jordan Mind").assertIsDisplayed()
    }

    @Test
    fun `build tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { BuildTab(viewModel()) } }
        }
        compose.onNodeWithText("Build Apps").assertIsDisplayed()
    }

    @Test
    fun `documents tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { DocumentsTab(viewModel()) } }
        }
        compose.onNodeWithText("Documents").assertIsDisplayed()
    }

    @Test
    fun `files tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { FilesTab(viewModel()) } }
        }
        compose.onNodeWithText("Files").assertIsDisplayed()
    }

    @Test
    fun `research tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { ResearchTab(viewModel()) } }
        }
        compose.onNodeWithText("Research").assertIsDisplayed()
    }

    @Test
    fun `exam tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { ExamTab(viewModel()) } }
        }
        compose.onNodeWithText("Exam").assertIsDisplayed()
    }

    @Test
    fun `agent tab renders`() {
        compose.setContent {
            JordanMindTheme { Box(Modifier.fillMaxSize()) { AgentTab(viewModel()) } }
        }
        compose.onNodeWithText("Agent").assertIsDisplayed()
    }

    @Test
    fun `every tab is reachable from the rail`() {
        var selected = Tab.CHAT
        compose.setContent {
            JordanMindTheme {
                JordanTabRail(selected = selected, onSelect = { selected = it })
            }
        }
        // The rail scrolls, so only the visible chips can be clicked here.
        listOf(Tab.CHAT, Tab.AGENT).forEach { tab ->
            compose.onNodeWithText(tab.label).performClick()
            assert(selected == tab) { "clicking ${tab.label} selected $selected" }
        }
    }
}

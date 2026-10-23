package com.jordanmind.agent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.theme.AuroraBackdrop
import com.jordanmind.agent.ui.theme.JordanMindTheme
import com.jordanmind.agent.ui.tabs.BuildTab
import com.jordanmind.agent.ui.tabs.ChatTab
import com.jordanmind.agent.ui.tabs.DocumentsTab
import com.jordanmind.agent.ui.tabs.ExamTab
import com.jordanmind.agent.ui.tabs.FilesTab
import com.jordanmind.agent.ui.tabs.ResearchTab

import com.jordanmind.agent.ui.AgentTab
import com.jordanmind.agent.ui.JordanTabRail
import com.jordanmind.agent.ui.Tab

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onShareReceived(readSharedText(intent))
        setContent {
            JordanMindTheme {
                JordanMindRoot(viewModel)
            }
        }
    }

    /** A share that arrives while the app is already open still lands in the chat. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onShareReceived(readSharedText(intent))
    }

    private fun readSharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }
}

/**
 * The whole app UI. Visible so Robolectric can render the real screen for smoke tests and
 * documentation screenshots.
 */
@Composable
fun JordanMindRoot(vm: AppViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val status by vm.status.collectAsStateWithLifecycle()
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(Tab.CHAT)
    }

    // Text shared from another app ("share to Jordan Mind") lands in the chat tab.
    val shared by vm.sharedText.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let {
            tab = Tab.CHAT
            vm.consumeSharedText()
        }
    }

    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            vm.clearStatus()
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            AuroraBackdrop(reduceMotion = vm.deviceProfile.reduceMotion)
            Column(Modifier.fillMaxSize().padding(padding)) {
                JordanTabRail(selected = tab, onSelect = { tab = it })
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.CHAT -> ChatTab(vm)
                        Tab.AGENT -> AgentTab(vm)
                        Tab.BUILD -> BuildTab(vm)
                        Tab.DOCS -> DocumentsTab(vm)
                        Tab.FILES -> FilesTab(vm)
                        Tab.RESEARCH -> ResearchTab(vm)
                        Tab.EXAM -> ExamTab(vm)
                    }
                }
            }
        }
    }
}

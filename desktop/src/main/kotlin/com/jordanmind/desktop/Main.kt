package com.jordanmind.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) {
    // Headless verification path, used by CI to prove the packaged binary works without a display.
    if (args.contains("--selftest")) {
        kotlin.system.exitProcess(SelfTest.run())
    }
    launchUi()
}

private fun launchUi() = application {
    val store = DesktopStore()
    val model = LocalModel()
    val actions = DesktopActions()

    Window(
        onCloseRequest = {
            store.persist()
            exitApplication()
        },
        title = "Jordan Mind",
        state = rememberWindowState(width = 1080.dp, height = 760.dp),
    ) {
        JordanMindApp(store = store, model = model, actions = actions)
    }
}

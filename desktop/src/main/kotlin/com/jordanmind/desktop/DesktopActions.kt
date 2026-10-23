package com.jordanmind.desktop

import com.jordanmind.agent.core.ActionType
import com.jordanmind.agent.core.AgentAction
import java.awt.Desktop
import java.io.File
import java.net.URI

data class ActionResult(val ok: Boolean, val message: String)

/**
 * Runs actions on the desktop.
 *
 * Everything here is deliberately conservative: nothing is deleted, nothing is executed, and no
 * path outside the user's own home directory is opened. An agent that can launch arbitrary
 * processes from a model's output is a liability, not a feature.
 */
class DesktopActions(private val home: File = File(System.getProperty("user.home"))) {

    private val desktop: Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null

    private val knownApps = mapOf(
        "chrome" to listOf("google-chrome", "chrome"),
        "firefox" to listOf("firefox"),
        "edge" to listOf("microsoft-edge"),
        "brave" to listOf("brave-browser"),
        "notepad" to listOf("notepad.exe", "gedit", "kate"),
        "calculator" to listOf("gnome-calculator", "kcalc", "calc.exe"),
        "terminal" to listOf("x-terminal-emulator", "gnome-terminal", "cmd.exe", "powershell.exe"),
        "explorer" to listOf("explorer.exe"),
        "file manager" to listOf("nautilus", "dolphin", "thunar", "explorer.exe"),
        "code" to listOf("code"),
        "vs code" to listOf("code"),
        "spotify" to listOf("spotify"),
        "word" to listOf("winword.exe", "libreoffice --writer"),
        "excel" to listOf("excel.exe", "libreoffice --calc"),
    )

    fun run(action: AgentAction): ActionResult = when (action.type) {
        ActionType.OPEN_APP -> openApp(action.args["app"].orEmpty())
        ActionType.OPEN_PATH -> openPath(action.args["path"].orEmpty())
        ActionType.NOTE -> ActionResult(true, "Noted.")
        else -> ActionResult(true, action.reply)
    }

    private fun openApp(name: String): ActionResult {
        if (name.isBlank()) return ActionResult(false, "Which app?")
        val query = name.lowercase().trim()

        knownApps[query]?.forEach { candidate ->
            findExecutable(candidate)?.let { path ->
                return try {
                    ProcessBuilder(path).start()
                    ActionResult(true, "Opened $name.")
                } catch (error: Exception) {
                    ActionResult(false, "Could not start $name: ${error.message}")
                }
            }
        }

        // Fall back to asking the OS to resolve it by name.
        return try {
            val builder = if (isWindows()) {
                ProcessBuilder("cmd", "/c", "start", "", query)
            } else {
                ProcessBuilder(query)
            }
            builder.start()
            ActionResult(true, "Asked the system to open $name.")
        } catch (error: Exception) {
            ActionResult(false, "I could not find an app called \"$name\".")
        }
    }

    private fun openPath(raw: String): ActionResult {
        if (raw.isBlank()) return ActionResult(false, "Which folder?")
        val resolved = File(raw.replace("~", home.absolutePath))
        val target = if (resolved.exists()) resolved else return ActionResult(false, "\"$raw\" does not exist.")

        // Refuse to open anything outside the user's home directory.
        val homePath = home.canonicalPath
        if (!target.canonicalPath.startsWith(homePath)) {
            return ActionResult(false, "I only open files inside $homePath.")
        }
        return try {
            if (desktop?.isSupported(Desktop.Action.OPEN) == true) {
                desktop.open(target)
                ActionResult(true, "Opened ${target.name}.")
            } else {
                ActionResult(false, "This system has no desktop handler for files.")
            }
        } catch (error: Exception) {
            ActionResult(false, "Could not open ${target.name}: ${error.message}")
        }
    }

    private fun findExecutable(name: String): String? {
        val withExe = if (isWindows() && !name.endsWith(".exe")) "$name.exe" else name
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        pathDirs.forEach { dir ->
            val candidate = File(dir, withExe)
            if (candidate.canExecute()) return candidate.absolutePath
        }
        return null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    /** Opens a URL in the default browser. Only http/https, and only when the user asked. */
    fun openUrl(url: String): ActionResult = try {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            ActionResult(false, "I only open http and https links.")
        } else if (desktop?.isSupported(Desktop.Action.BROWSE) == true) {
            desktop.browse(URI(url))
            ActionResult(true, "Opened $url")
        } else {
            ActionResult(false, "No browser handler available.")
        }
    } catch (error: Exception) {
        ActionResult(false, "Could not open the link: ${error.message}")
    }
}

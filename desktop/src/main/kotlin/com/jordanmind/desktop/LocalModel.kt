package com.jordanmind.desktop

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Offline language model for the desktop.
 *
 * There is no cross-platform on-device runtime that can be bundled and called from the JVM without
 * native bindings, so this drives an external `llama-cli` if the user has one. When it is absent the
 * agent still works: templates and learned patterns cover the common instructions, and the model
 * step is simply skipped with an honest message rather than a fabricated answer.
 */
class LocalModel(
    private val modelFile: File? = defaultModelFile(),
    private val binary: String = defaultBinary(),
) {

    val available: Boolean
        get() = modelFile != null && modelFile.exists() && resolveBinary() != null

    fun describe(): String = when {
        modelFile == null || !modelFile.exists() ->
            "No model found. Put a .gguf file in ~/.jordanmind/models, or set JORDANMIND_MODEL."
        resolveBinary() == null ->
            "Found ${modelFile.name} but no llama-cli. Set JORDANMIND_LLAMA to its full path."
        else ->
            "Using ${modelFile.name} via ${resolveBinary()}"
    }

    /** Runs one prompt to completion. Blocking; call from a background thread. */
    fun generate(prompt: String, system: String = DEFAULT_SYSTEM, maxTokens: Int = 512): String {
        if (!available) return ""
        val exe = resolveBinary() ?: return ""
        val command = mutableListOf(
            exe,
            "-m", modelFile!!.absolutePath,
            "-p", prompt,
            "-n", maxTokens.toString(),
            "--no-display-prompt",
            "--simple-io",
            // Without --single-turn llama-cli waits on stdin for another turn after answering,
            // and the reader below blocks on its stdout until the timeout kills it.
            "-st",
            "-t", Runtime.getRuntime().availableProcessors().coerceAtMost(8).toString(),
        )
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            // A closed stdin makes llama-cli treat the prompt as the only turn.
            process.outputStream.close()
            val output = StringBuilder()
            // Read on a worker so a stalled llama-cli cannot block past the deadline; reading
            // inline would park here forever and never reach the waitFor below.
            val reader = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).use { input ->
                        input.forEachLine { line ->
                            synchronized(output) { output.appendLine(line) }
                        }
                    }
                }
            }
            reader.isDaemon = true
            reader.start()
            if (!process.waitFor(180, TimeUnit.SECONDS)) process.destroyForcibly()
            reader.join(5_000)
            extractReply(output.toString(), prompt)
        }.getOrElse { "" }
    }

    /**
     * Pulls the answer out of llama-cli's stdout.
     *
     * llama-cli always prints a startup banner, echoes the prompt as `> <prompt>`, then the reply,
     * then a `[ Prompt: ... ]` timing line. No flag suppresses the banner, so the reply is taken as
     * the text between the echoed prompt and the timing line.
     */
    private fun extractReply(raw: String, prompt: String): String {
        val lines = raw.lines()
        val promptLines = prompt.trim().lines()
        val echo = "> " + promptLines.first().trim()
        val start = lines.indexOfFirst { it.trim() == echo }
        val body = if (start >= 0) lines.drop(start + promptLines.size) else lines
        return body
            .takeWhile { !it.trimStart().startsWith("[ Prompt:") && it.trim() != "Exiting..." }
            .joinToString("\n")
            .trim()
    }

    private fun resolveBinary(): String? {
        val direct = File(binary)
        if (direct.canExecute()) return direct.absolutePath
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        pathDirs.forEach { dir ->
            listOf(binary, "$binary.exe").forEach { name ->
                val candidate = File(dir, name)
                if (candidate.canExecute()) return candidate.absolutePath
            }
        }
        return null
    }

    companion object {
        const val DEFAULT_SYSTEM = "You are a concise, helpful offline assistant."

        /**
         * Directory shipped alongside the application, used when the installer bundles a model and
         * the llama.cpp runtime. Empty when running from a plain build.
         */
        private val bundledDir: File? by lazy {
            val home = System.getProperty("compose.application.resources.dir") ?: return@lazy null
            val app = File(home).parentFile ?: return@lazy null
            val dir = File(app, "models")
            if (dir.isDirectory) dir else null
        }

        fun defaultModelFile(): File? {
            System.getenv("JORDANMIND_MODEL")?.let { return File(it) }
            // The user's own models win, so dropping in a bigger model overrides the bundled one.
            val userDir = File(System.getProperty("user.home"), ".jordanmind/models")
            userDir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
                ?.sortedBy { it.name }
                ?.firstOrNull()
                ?.let { return it }
            return bundledDir?.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
                ?.sortedBy { it.name }
                ?.firstOrNull()
        }

        /** Prefers a bundled llama-cli so the app works with nothing installed on the machine. */
        fun defaultBinary(): String {
            System.getenv("JORDANMIND_LLAMA")?.let { return it }
            bundledDir?.listFiles { f ->
                f.isFile && f.nameWithoutExtension.equals("llama-cli", ignoreCase = true)
            }?.firstOrNull()?.let { return it.absolutePath }
            return if (System.getProperty("os.name").startsWith("Windows")) "llama-cli.exe" else "llama-cli"
        }
    }
}

/** Prompt builders shared by the desktop features. */
object DesktopPrompts {

    fun article(topic: String) = """
Write a complete blog article about "$topic".
Format: a specific title on the first line, then the body in short paragraphs.
Plain, direct tone. 400 to 600 words. No markdown headers or bullet symbols.
""".trimIndent()

    fun money(topic: String): String {
        val focus = topic.ifBlank { "the user's situation" }
        return """
Give practical, honest ways to earn money related to: $focus
Rules: only realistic options needing little or no capital; for each give what it is, what it takes,
roughly what it pays, and the first step; say plainly when something is hard; never promise specific
income; never suggest anything illegal, deceptive or financially risky.
Give 5 ideas ordered by how quickly the user could start.
""".trimIndent()
    }

    fun catchUp(reminders: List<com.jordanmind.agent.core.Reminder>, facts: Map<String, String>): String =
        buildString {
            append("Catch-up\n\n")
            if (reminders.isNotEmpty()) {
                append("Still open:\n")
                reminders.take(6).forEach { append("- ${it.task} (${DesktopStore.format(it.at)})\n") }
            }
            if (facts.isNotEmpty()) {
                append("\nRemembered for you:\n")
                facts.entries.take(6).forEach { append("- ${it.key}: ${it.value}\n") }
            }
            if (reminders.isEmpty() && facts.isEmpty()) {
                append("Nothing outstanding. You are all caught up.")
            }
        }.trim()
}

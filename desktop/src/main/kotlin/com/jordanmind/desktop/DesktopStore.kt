package com.jordanmind.desktop

import com.jordanmind.agent.core.AgentState
import com.jordanmind.agent.core.Article
import com.jordanmind.agent.core.Reminder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local storage for the desktop agent.
 *
 * Everything lives in one JSON file under the user's home directory. No network, no database,
 * no daemon — the file is small enough to read and write whole.
 */
class DesktopStore(private val root: File = File(System.getProperty("user.home"), ".jordanmind")) {

    private val stateFile = File(root, "state.json")
    private val articlesDir = File(root, "articles").apply { mkdirs() }

    var state: AgentState = AgentState()
        private set

    init {
        load()
    }

    private fun load() {
        state = if (stateFile.exists()) {
            AgentState.load(stateFile.readText())
        } else {
            AgentState()
        }
    }

    fun persist() {
        root.mkdirs()
        runCatching { stateFile.writeText(state.export()) }
    }

    fun remember(utterance: String, action: String, args: Map<String, String>) {
        state.remember(utterance, action, args)
        persist()
    }

    fun addReminder(task: String, at: Long): Reminder {
        val reminder = state.addReminder(task, at)
        persist()
        return reminder
    }

    fun addArticle(article: Article) {
        state.addArticle(article)
        persist()
    }

    /** Articles are also written out as readable .md files so they are usable elsewhere. */
    fun exportArticle(article: Article): File {
        val safe = article.topic.ifBlank { "article" }
            .replace(Regex("[^a-zA-Z0-9 _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
            .take(60)
        val file = File(articlesDir, "$safe.md")
        runCatching {
            file.writeText("# ${article.title}\n\n${article.body}\n")
        }
        return file
    }

    fun listArticleFiles(): List<File> =
        articlesDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun memorySummary(): String = buildString {
        append("Learned patterns: ${state.learnedCount()}\n")
        append("Things remembered: ${state.facts().size}\n")
        append("Reminders pending: ${state.pendingReminders().size}\n")
        append("Articles: ${state.articles().size}\n")
        append("Storage: $root")
    }

    companion object {
        fun format(millis: Long): String =
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

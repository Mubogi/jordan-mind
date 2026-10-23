package com.jordanmind.agent.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A drafted or scheduled piece of content. */
data class Article(
    val id: String,
    val title: String,
    val body: String,
    val topic: String,
    /** Epoch millis at which the user wants to be nudged to publish. 0 = draft only. */
    val publishAt: Long = 0L,
    val published: Boolean = false,
)

/**
 * Stores articles and scheduled posts on the device.
 *
 * Scheduling is deliberately local-only: the app has no network permission, so it cannot post to
 * WordPress, Blogger, Medium or anywhere else. What it does instead is prepare the text, keep it
 * safe, and fire a notification at the chosen time so the user can paste it wherever they like.
 * Anything claiming to auto-publish from an offline app would be lying about the platform.
 */
class ContentStudio(context: Context) {

    private val file = File(context.filesDir, "agent/articles.json")
    private val articles = mutableListOf<Article>()

    init {
        load()
    }

    fun all(): List<Article> = articles.sortedByDescending { it.publishAt }

    fun scheduled(): List<Article> = articles.filter { !it.published && it.publishAt > 0 }
        .sortedBy { it.publishAt }

    fun drafts(): List<Article> = articles.filter { it.publishAt == 0L }

    fun add(article: Article) {
        articles.add(0, article)
        save()
    }

    fun update(id: String, transform: (Article) -> Article) {
        val index = articles.indexOfFirst { it.id == id }
        if (index < 0) return
        articles[index] = transform(articles[index])
        save()
    }

    fun delete(id: String) {
        articles.removeAll { it.id == id }
        save()
    }

    /** Called on app start and by the reminder worker to clear past-due entries. */
    fun dueNow(now: Long = System.currentTimeMillis()): List<Article> =
        articles.filter { !it.published && it.publishAt in 1..now }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                articles.add(
                    Article(
                        id = obj.getString("id"),
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        topic = obj.optString("topic"),
                        publishAt = obj.optLong("publishAt", 0L),
                        published = obj.optBoolean("published", false),
                    ),
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        articles.forEach { article ->
            array.put(
                JSONObject().apply {
                    put("id", article.id)
                    put("title", article.title)
                    put("body", article.body)
                    put("topic", article.topic)
                    put("publishAt", article.publishAt)
                    put("published", article.published)
                },
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    companion object {
        fun formatTime(millis: Long): String =
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

/** Prompt builders for the content and ideas features. */
object Prompts {

    fun article(topic: String): String = """
Write a complete blog article about "$topic".

Format:
- A specific, non-clickbait title on the first line
- Then the article body in short paragraphs
- Use a plain, direct tone
- Around 400 to 600 words
- No markdown headers, no bullet symbols

Write it now.""".trimIndent()

    fun money(topic: String): String {
        val focus = if (topic.isBlank()) "the user's situation" else topic
        return """
Give practical, honest ways to earn money related to: $focus

Rules:
- Only realistic options that need little or no starting capital
- For each idea give: what it is, what it takes to start, roughly what it pays, and the first step
- Say plainly when something is hard or unlikely to work
- Do not promise specific income
- Do not suggest anything illegal, deceptive or risky with the user's money

Give 5 ideas, ordered by how quickly the user could start.""".trimIndent()
    }

    /** Builds the digest the floating bot shows. */
    fun catchUp(
        scheduled: List<Article>,
        pendingTasks: List<String>,
        facts: Map<String, String>,
    ): String = buildString {
        append("Catch-up for you.\n\n")
        if (scheduled.isNotEmpty()) {
            append("Scheduled posts waiting:\n")
            scheduled.take(5).forEach { article ->
                append("- \"${article.title.ifBlank { article.topic }}\" at ")
                append(ContentStudio.formatTime(article.publishAt))
                append("\n")
            }
            append("\n")
        }
        if (pendingTasks.isNotEmpty()) {
            append("Reminders still open:\n")
            pendingTasks.take(5).forEach { append("- $it\n") }
            append("\n")
        }
        if (facts.isNotEmpty()) {
            append("Things you asked me to remember:\n")
            facts.entries.take(5).forEach { append("- ${it.key}: ${it.value}\n") }
            append("\n")
        }
        if (scheduled.isEmpty() && pendingTasks.isEmpty() && facts.isEmpty()) {
            append("Nothing is outstanding. You are all caught up.")
        }
    }.trim()
}

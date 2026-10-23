package com.jordanmind.agent.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Everything the agent can do. Shared verbatim between the Android app and the desktop app so the
 * two behave identically for the same instruction.
 */
object ActionType {
    const val OPEN_APP = "open_app"
    const val SEND_MESSAGE = "send_message"
    const val SET_ALARM = "set_alarm"
    const val SET_TIMER = "set_timer"
    const val REMIND = "remind"
    const val NOTE = "note"
    const val DRAFT_ARTICLE = "draft_article"
    const val SCHEDULE_ARTICLE = "schedule_article"
    const val MONEY = "money"
    const val OPEN_PATH = "open_path"
    const val ANSWER = "answer"

    val all = listOf(
        OPEN_APP, SEND_MESSAGE, SET_ALARM, SET_TIMER, REMIND, NOTE,
        DRAFT_ARTICLE, SCHEDULE_ARTICLE, MONEY, OPEN_PATH, ANSWER,
    )
}

@Serializable
data class AgentAction(
    val type: String,
    val args: Map<String, String> = emptyMap(),
    val reply: String = "",
)

enum class PlanSource { MEMORY, TEMPLATE, MODEL, FALLBACK }

data class Plan(
    val action: AgentAction,
    val source: PlanSource,
    val raw: String = "",
)

@Serializable
data class Pattern(
    val phrase: String,
    val action: String,
    val args: Map<String, String>,
    val hits: Int = 0,
    val learned: Boolean = false,
)

@Serializable
data class Reminder(
    val id: Int,
    val task: String,
    val at: Long,
    val done: Boolean = false,
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    val body: String,
    val topic: String,
    val publishAt: Long = 0L,
    val published: Boolean = false,
)

@Serializable
data class AgentMemory(
    val patterns: List<Pattern> = emptyList(),
    val facts: Map<String, String> = emptyMap(),
    val reminders: List<Reminder> = emptyList(),
    val articles: List<Article> = emptyList(),
)

/**
 * In-memory agent state with JSON import/export.
 *
 * Storage is the caller's problem: Android passes a files directory, the desktop app passes
 * `~/.jordanmind`. Keeping IO out of this class is what makes it testable and shareable.
 */
class AgentState(initial: AgentMemory = AgentMemory()) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val patterns = LinkedHashMap<String, Pattern>()
    private val facts = LinkedHashMap<String, String>()
    private val reminders = mutableListOf<Reminder>()
    private val articles = mutableListOf<Article>()

    init {
        initial.patterns.forEach { patterns[it.phrase] = it }
        facts.putAll(initial.facts)
        reminders.addAll(initial.reminders)
        articles.addAll(initial.articles)
        if (patterns.isEmpty()) seed()
    }

    // ------------------------------------------------------------------ access

    fun patterns(): List<Pattern> = patterns.values.toList()
    fun learnedCount(): Int = patterns.values.count { it.learned }
    fun facts(): Map<String, String> = facts.toMap()
    fun reminders(): List<Reminder> = reminders.sortedBy { it.at }
    fun pendingReminders(): List<Reminder> = reminders.filter { !it.done }.sortedBy { it.at }
    fun articles(): List<Article> = articles.sortedByDescending { it.publishAt }
    fun scheduled(): List<Article> = articles.filter { !it.published && it.publishAt > 0 }.sortedBy { it.publishAt }

    // ---------------------------------------------------------------- mutation

    fun remember(utterance: String, action: String, args: Map<String, String>) {
        val key = normalise(utterance)
        if (key.isBlank()) return
        val existing = patterns[key]
        if (existing != null) {
            patterns[key] = existing.copy(hits = existing.hits + 1)
            return
        }
        patterns[key] = Pattern(key, action, args, hits = 1, learned = true)
    }

    fun forget(phrase: String) {
        patterns.remove(normalise(phrase))
    }

    fun resetLearned() {
        patterns.entries.removeAll { it.value.learned }
    }

    fun putFact(key: String, value: String) {
        facts[normalise(key)] = value
    }

    fun forgetFact(key: String) {
        facts.remove(normalise(key))
    }

    fun addReminder(task: String, at: Long): Reminder {
        val reminder = Reminder(nextId(), task, at)
        reminders.add(reminder)
        return reminder
    }

    fun completeReminder(id: Int) {
        val index = reminders.indexOfFirst { it.id == id }
        if (index >= 0) reminders[index] = reminders[index].copy(done = true)
    }

    fun removeReminder(id: Int) {
        reminders.removeAll { it.id == id }
    }

    fun addArticle(article: Article) {
        articles.add(0, article)
    }

    fun deleteArticle(id: String) {
        articles.removeAll { it.id == id }
    }

    fun dueReminders(now: Long = System.currentTimeMillis()): List<Reminder> =
        reminders.filter { !it.done && it.at in 1..now }

    fun dueArticles(now: Long = System.currentTimeMillis()): List<Article> =
        articles.filter { !it.published && it.publishAt in 1..now }

    // -------------------------------------------------------------------- json

    fun export(): String = json.encodeToString(
        AgentMemory(
            patterns = patterns(),
            facts = facts(),
            reminders = reminders(),
            articles = articles(),
        ),
    )

    fun factBlock(): String =
        if (facts.isEmpty()) "" else facts.entries.joinToString(
            prefix = "\nThings you know about the user:\n",
            separator = "\n",
        ) { "- ${it.key}: ${it.value}" }

    private var counter = 0
    private fun nextId(): Int = ((System.currentTimeMillis() + counter++) % Int.MAX_VALUE).toInt()

    private fun seed() {
        // Starter patterns so the agent is useful before it has learned anything.
        listOf(
            "open {app}" to ActionType.OPEN_APP,
            "launch {app}" to ActionType.OPEN_APP,
            "text {contact} {body}" to ActionType.SEND_MESSAGE,
            "send a message to {contact} saying {body}" to ActionType.SEND_MESSAGE,
            "set an alarm for {time}" to ActionType.SET_ALARM,
            "set a timer for {time}" to ActionType.SET_TIMER,
            "remind me to {task} at {time}" to ActionType.REMIND,
            "note that {text}" to ActionType.NOTE,
            "write an article about {topic}" to ActionType.DRAFT_ARTICLE,
            "schedule an article about {topic}" to ActionType.SCHEDULE_ARTICLE,
            "how can i make money with {topic}" to ActionType.MONEY,
        ).forEach { (phrase, action) ->
            val args = Regex("\\{(\\w+)}").findAll(phrase)
                .associate { it.groupValues[1] to "{${it.groupValues[1]}}" }
            patterns[phrase] = Pattern(phrase, action, args)
        }
    }

    companion object {
        fun load(json: String): AgentState = runCatching {
            AgentState(Json { ignoreUnknownKeys = true }.decodeFromString<AgentMemory>(json))
        }.getOrElse { AgentState() }

        /** Filler that carries no meaning for matching; dropped before scoring. */
        private val STOPWORDS = setOf(
            "the", "and", "for", "with", "that", "this", "please", "you", "your", "my", "me",
            "to", "of", "in", "on", "at", "is", "it", "now", "app", "can", "could", "would",
            "hey", "ok", "okay", "just", "some", "get", "got",
        )

        fun normalise(text: String): String =
            text.lowercase()
                // Apostrophes join rather than split, so "I'm" becomes "im" not "i m".
                .replace(Regex("['\u2019]"), "")
                .replace(Regex("[^a-z0-9\\s{}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun tokens(text: String): Set<String> =
            normalise(text).split(" ")
                .filter { it.length > 2 && it !in STOPWORDS }
                .toSet()

        /**
         * Fills {slots} by turning the stored pattern into a regex and matching the instruction
         * against it. The template is the source of truth, so "remind me to {task} at {time}"
         * correctly yields task="call the bank" rather than guessing word boundaries.
         */
        fun extractSlots(template: String, instruction: String): Map<String, String> {
            val slotPattern = Regex("\\{(\\w+)}")
            val names = slotPattern.findAll(template).map { it.groupValues[1] }.toList()
            if (names.isEmpty()) return emptyMap()

            val builder = StringBuilder("^")
            var cursor = 0
            slotPattern.findAll(template).forEach { match ->
                builder.append(Regex.escape(template.substring(cursor, match.range.first)))
                builder.append("(.+?)")
                cursor = match.range.last + 1
            }
            builder.append(Regex.escape(template.substring(cursor))).append("$")

            val match = Regex(builder.toString(), RegexOption.IGNORE_CASE).find(instruction.trim())
                ?: return emptyMap()
            return names.mapIndexedNotNull { index, name ->
                match.groupValues.getOrNull(index + 1)?.trim()?.takeIf { it.isNotEmpty() }?.let { name to it }
            }.toMap()
        }

        fun similarity(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val union = a.union(b).size
            if (union == 0) return 0.0
            return a.intersect(b).size.toDouble() / union
        }

        /** Cosine similarity, used for the local knowledge base. */
        fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var dot = 0.0
            a.forEach { (key, value) -> b[key]?.let { dot += value * it } }
            val normA = sqrt(a.values.sumOf { it * it })
            val normB = sqrt(b.values.sumOf { it * it })
            return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
        }

        fun fuzzyEquals(a: String, b: String, tolerance: Int = 1): Boolean {
            if (a == b) return true
            if (abs(a.length - b.length) > tolerance) return false
            return levenshtein(a, b) <= tolerance
        }

        fun levenshtein(a: String, b: String): Int {
            val dp = IntArray(b.length + 1) { it }
            a.forEachIndexed { i, ca ->
                var previous = dp[0]
                dp[0] = i + 1
                b.forEachIndexed { j, cb ->
                    val temp = dp[j + 1]
                    dp[j + 1] = minOf(
                        dp[j + 1] + 1,
                        dp[j] + 1,
                        previous + if (ca == cb) 0 else 1,
                    )
                    previous = temp
                }
            }
            return dp[b.length]
        }
    }
}

package com.jordanmind.agent.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A remembered instruction -> action mapping. */
data class Pattern(
    val phrase: String,
    val action: String,
    val args: Map<String, String>,
    var hits: Int = 0,
    var learned: Boolean = false,
)

/**
 * Persistent store for the agent's memory: learned command patterns and free-form facts.
 *
 * "Learning" here is deliberately concrete and honest: when the language model successfully
 * turns an unfamiliar instruction into an action plan, that mapping is cached keyed on the
 * normalised instruction. The next time the user phrases it the same way the plan is reused
 * without invoking the model at all, which is both faster and more reliable. This is template
 * memorisation, not weight training — nothing here modifies the model itself.
 */
class MemoryStore(context: Context) {

    private val dir = File(context.filesDir, "agent").apply { mkdirs() }
    private val patternFile = File(dir, "patterns.json")
    private val factFile = File(dir, "facts.json")

    private val patterns = LinkedHashMap<String, Pattern>()
    private val facts = LinkedHashMap<String, String>()

    init {
        loadPatterns()
        loadFacts()
    }

    // ---------------------------------------------------------------- patterns

    fun all(): List<Pattern> = patterns.values.toList()

    fun learnedCount(): Int = patterns.values.count { it.learned }

    /** Exact match on the normalised phrase. */
    fun exact(utterance: String): Pattern? =
        patterns[normalise(utterance)]?.also { it.hits++ }

    /**
     * Falls back to token-overlap scoring so "open whatsapp" also satisfies
     * "please open whatsapp now". Threshold is intentionally high to avoid misfires.
     */
    fun closest(utterance: String, minScore: Double = 0.75): Pattern? {
        val target = tokens(utterance)
        if (target.isEmpty()) return null
        var best: Pattern? = null
        var bestScore = 0.0
        patterns.forEach { (_, pattern) ->
            val score = jaccard(target, tokens(pattern.phrase))
            if (score > bestScore) {
                bestScore = score
                best = pattern
            }
        }
        return if (bestScore >= minScore) best?.also { it.hits++ } else null
    }

    fun remember(utterance: String, action: String, args: Map<String, String>) {
        val key = normalise(utterance)
        if (key.isBlank()) return
        val existing = patterns[key]
        if (existing != null) {
            existing.hits++
            return
        }
        patterns[key] = Pattern(key, action, args, hits = 1, learned = true)
        savePatterns()
    }

    fun forget(phrase: String) {
        patterns.remove(normalise(phrase))
        savePatterns()
    }

    fun clearPatterns() {
        patterns.clear()
        savePatterns()
    }

    // ------------------------------------------------------------------- facts

    fun putFact(key: String, value: String) {
        facts[normalise(key)] = value
        saveFacts()
    }

    fun fact(key: String): String? = facts[normalise(key)]

    fun allFacts(): Map<String, String> = facts.toMap()

    fun forgetFact(key: String) {
        facts.remove(normalise(key))
        saveFacts()
    }

    /** Rendered into the system prompt so the model can use what it has been told. */
    fun factBlock(): String =
        if (facts.isEmpty()) "" else facts.entries.joinToString(
            prefix = "\nThings you know about the user:\n",
            separator = "\n",
        ) { "- ${it.key}: ${it.value}" }

    // ------------------------------------------------------------------- seeds

    /**
     * Starter patterns so the agent is useful before it has learned anything.
     * {name}, {contact}, {time} and {body} are filled from the instruction at run time.
     */
    private fun seedIfEmpty() {
        if (patterns.isNotEmpty()) return
        listOf(
            Pattern("open {app}", ActionType.OPEN_APP, mapOf("app" to "{app}")),
            Pattern("launch {app}", ActionType.OPEN_APP, mapOf("app" to "{app}")),
            Pattern("text {contact} {body}", ActionType.SEND_SMS, mapOf("contact" to "{contact}", "body" to "{body}")),
            Pattern("send a message to {contact} saying {body}", ActionType.SEND_SMS, mapOf("contact" to "{contact}", "body" to "{body}")),
            Pattern("set an alarm for {time}", ActionType.SET_ALARM, mapOf("time" to "{time}")),
            Pattern("set a timer for {time}", ActionType.SET_TIMER, mapOf("time" to "{time}")),
            Pattern("remind me to {task} at {time}", ActionType.REMIND, mapOf("task" to "{task}", "time" to "{time}")),
            Pattern("note that {text}", ActionType.NOTE, mapOf("text" to "{text}")),
            Pattern("write an article about {topic}", ActionType.DRAFT_ARTICLE, mapOf("topic" to "{topic}")),
            Pattern("schedule an article about {topic}", ActionType.SCHEDULE_ARTICLE, mapOf("topic" to "{topic}")),
            Pattern("how can i make money with {topic}", ActionType.MONEY, mapOf("topic" to "{topic}")),
        ).forEach { patterns[it.phrase] = it }
    }

    // -------------------------------------------------------------------- json

    private fun loadPatterns() {
        if (!patternFile.exists()) {
            seedIfEmpty()
            savePatterns()
            return
        }
        runCatching {
            val array = JSONArray(patternFile.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val argsObj = obj.optJSONObject("args") ?: JSONObject()
                val args = argsObj.keys().asSequence().associateWith { argsObj.getString(it) }
                val pattern = Pattern(
                    phrase = obj.getString("phrase"),
                    action = obj.getString("action"),
                    args = args,
                    hits = obj.optInt("hits", 0),
                    learned = obj.optBoolean("learned", false),
                )
                patterns[pattern.phrase] = pattern
            }
        }
        if (patterns.isEmpty()) seedIfEmpty()
    }

    private fun savePatterns() {
        val array = JSONArray()
        patterns.values.forEach { pattern ->
            array.put(
                JSONObject().apply {
                    put("phrase", pattern.phrase)
                    put("action", pattern.action)
                    put("args", JSONObject(pattern.args as Map<*, *>))
                    put("hits", pattern.hits)
                    put("learned", pattern.learned)
                },
            )
        }
        runCatching { patternFile.writeText(array.toString()) }
    }

    private fun loadFacts() {
        if (!factFile.exists()) return
        runCatching {
            val obj = JSONObject(factFile.readText())
            obj.keys().forEach { facts[it] = obj.getString(it) }
        }
    }

    private fun saveFacts() {
        runCatching { factFile.writeText(JSONObject(facts as Map<*, *>).toString()) }
    }

    companion object {
        fun normalise(text: String): String =
            text.lowercase()
                .replace(Regex("[^a-z0-9\\s{}]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        fun tokens(text: String): Set<String> =
            normalise(text).split(" ").filter { it.length > 2 }.toSet()

        fun jaccard(a: Set<String>, b: Set<String>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            val intersection = a.intersect(b).size
            val union = a.union(b).size
            return intersection.toDouble() / union
        }
    }
}

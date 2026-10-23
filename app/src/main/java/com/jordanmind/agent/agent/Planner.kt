package com.jordanmind.agent.agent

import org.json.JSONObject

/**
 * Decides what an instruction means.
 *
 * Two stages, cheapest first:
 *  1. Memory and built-in templates — no model call, instant, works with no model loaded.
 *  2. The language model, asked for a strict JSON action. On success the mapping is written to
 *     [MemoryStore] so the same phrasing never needs the model again.
 */
class Planner(
    private val memory: MemoryStore,
    private val askModel: suspend (String) -> String,
) {

    /** Outcome of planning, including whether the model was consulted. */
    data class Plan(val action: AgentAction, val source: Source, val raw: String = "") {
        enum class Source { MEMORY, TEMPLATE, MODEL, FALLBACK }
    }

    suspend fun plan(instruction: String): Plan {
        // Stage 1a: something we have already learned or seeded, matched exactly.
        memory.exact(instruction)?.let { pattern ->
            return Plan(
                AgentAction(pattern.action, resolve(pattern.phrase, pattern.args, instruction)),
                Plan.Source.MEMORY,
            )
        }

        // Stage 1b: built-in templates with slot extraction.
        TemplateMatcher.match(instruction)?.let { action ->
            return Plan(action, Plan.Source.TEMPLATE)
        }

        // Stage 1c: a near-miss on a learned pattern.
        memory.closest(instruction)?.let { pattern ->
            return Plan(
                AgentAction(pattern.action, resolve(pattern.phrase, pattern.args, instruction)),
                Plan.Source.MEMORY,
            )
        }

        // Stage 2: ask the model.
        val raw = runCatching { askModel(buildPrompt(instruction)) }.getOrElse { "" }
        val parsed = parse(raw)
        if (parsed != null) {
            memory.remember(instruction, parsed.type, parsed.args)
            return Plan(parsed, Plan.Source.MODEL, raw)
        }

        return Plan(
            AgentAction(ActionType.ANSWER, reply = raw.trim().ifBlank { "I could not work that out." }),
            Plan.Source.FALLBACK,
            raw,
        )
    }

    /** Substitutes {slots} in a stored pattern using the current instruction. */
    private fun resolve(template: String, args: Map<String, String>, instruction: String): Map<String, String> {
        val extracted = TemplateMatcher.extractSlots(template, instruction)
        return args.mapValues { (_, template) ->
            var value = template
            extracted.forEach { (key, replacement) -> value = value.replace("{$key}", replacement) }
            value
        }
    }

    private fun buildPrompt(instruction: String): String = """
You convert a phone instruction into one action. Reply with JSON only, no prose.

Actions and their arguments:
  open_app        app
  send_sms        contact, body
  set_alarm       time
  set_timer       time
  remind          task, time
  note            text
  draft_article   topic
  schedule_article topic
  money           topic
  answer          (no arguments, use when nothing above fits)

Examples:
  "open whatsapp" -> {"action":"open_app","args":{"app":"whatsapp"}}
  "text mum i am late" -> {"action":"send_sms","args":{"contact":"mum","body":"i am late"}}
  "remind me to call the bank at 6pm" -> {"action":"remind","args":{"task":"call the bank","time":"6pm"}}
  "what is the capital of kenya" -> {"action":"answer","args":{}}

Instruction: "$instruction"
JSON:""".trimIndent()

    /** Tolerant JSON extraction — models often wrap output in code fences or add a sentence. */
    private fun parse(raw: String): AgentAction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = JSONObject(raw.substring(start, end + 1))
            val type = obj.optString("action").takeIf { it.isNotBlank() } ?: return null
            if (type !in ActionType.all) return null
            val argsObj = obj.optJSONObject("args") ?: JSONObject()
            val args = argsObj.keys().asSequence()
                .associateWith { argsObj.optString(it) }
                .filterValues { it.isNotBlank() }
            AgentAction(type, args)
        }.getOrNull()
    }
}

/**
 * Rule-based slot filling for common phrasings. This is what makes the agent usable
 * instantly, with no model loaded and no prior learning.
 */
object TemplateMatcher {

    private val openApp = Regex(
        "^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?([a-z0-9 .]+?)(?:\\s+app)?$",
        RegexOption.IGNORE_CASE,
    )
    private val sms = Regex(
        "^(?:please\\s+)?(?:text|message|sms|whatsapp)\\s+([a-z0-9 .]+?)\\s+(?:saying\\s+|that\\s+|to say\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val remind = Regex(
        "^(?:please\\s+)?remind\\s+me\\s+to\\s+(.+?)\\s+(?:at|by|around)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val alarm = Regex(
        "^(?:please\\s+)?set\\s+(?:an\\s+)?alarm\\s+(?:for\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val timer = Regex(
        "^(?:please\\s+)?(?:set\\s+a\\s+timer|timer)\\s+(?:for\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val note = Regex(
        "^(?:please\\s+)?(?:note|remember|write down)\\s+(?:that\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val article = Regex(
        "^(?:please\\s+)?(?:write|draft|compose)\\s+(?:an?\\s+)?article\\s+(?:about|on)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val schedule = Regex(
        "^(?:please\\s+)?(?:schedule|plan|queue)\\s+(?:an?\\s+)?(?:article|post|blog)\\s+(?:about|on)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val money = Regex(
        "^(?:please\\s+)?(?:how\\s+can\\s+i\\s+make\\s+money|make\\s+money|earn\\s+money|ways\\s+to\\s+make\\s+money)\\s*(?:with|from|on|using)?\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )

    fun match(instruction: String): AgentAction? {
        val text = instruction.trim()

        remind.find(text)?.let { m ->
            return AgentAction(ActionType.REMIND, mapOf("task" to m.groupValues[1].trim(), "time" to m.groupValues[2].trim()))
        }
        schedule.find(text)?.let { m ->
            return AgentAction(ActionType.SCHEDULE_ARTICLE, mapOf("topic" to m.groupValues[1].trim()))
        }
        article.find(text)?.let { m ->
            return AgentAction(ActionType.DRAFT_ARTICLE, mapOf("topic" to m.groupValues[1].trim()))
        }
        // Before openApp: "start a timer" would otherwise look like an app launch.
        timer.find(text)?.let { m ->
            return AgentAction(ActionType.SET_TIMER, mapOf("time" to m.groupValues[1].trim()))
        }
        alarm.find(text)?.let { m ->
            return AgentAction(ActionType.SET_ALARM, mapOf("time" to m.groupValues[1].trim()))
        }
        sms.find(text)?.let { m ->
            val contact = m.groupValues[1].trim()
            val body = m.groupValues[2].trim()
            // "text mum about dinner" is a request for help, not a message to send.
            if (body.split(" ").size <= 12 && !body.startsWith("about ")) {
                return AgentAction(ActionType.SEND_SMS, mapOf("contact" to contact, "body" to body))
            }
        }
        note.find(text)?.let { m ->
            return AgentAction(ActionType.NOTE, mapOf("text" to m.groupValues[1].trim()))
        }
        money.find(text)?.let { m ->
            val topic = m.groupValues.getOrNull(1).orEmpty().trim()
            return AgentAction(ActionType.MONEY, mapOf("topic" to topic))
        }
        openApp.find(text)?.let { m ->
            val app = m.groupValues[1].trim()
            if (app.isNotEmpty() && app.split(" ").size <= 3) {
                return AgentAction(ActionType.OPEN_APP, mapOf("app" to app))
            }
        }
        return null
    }

    /**
     * Fills {slots} by compiling the stored template into a regex. The template is the source of
     * truth, so "remind me to {task} at {time}" yields task="call the bank" rather than guessing
     * where one slot ends and the next begins.
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
}

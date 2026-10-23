package com.jordanmind.desktop

import com.jordanmind.agent.core.ActionType
import com.jordanmind.agent.core.AgentAction
import com.jordanmind.agent.core.AgentState
import com.jordanmind.agent.core.Plan
import com.jordanmind.agent.core.PlanSource

/**
 * Turns an instruction into an action.
 *
 * Same two-stage design as the Android app: templates and learned patterns first, so the agent
 * works instantly and offline; the model only sees phrasings it has never met, and whatever it
 * works out is written back to memory.
 */
class Planner(
    private val state: AgentState,
    private val askModel: suspend (String) -> String,
) {

    suspend fun plan(instruction: String): Plan {
        val exact = state.patterns().firstOrNull { it.phrase == AgentState.normalise(instruction) }
        if (exact != null) {
            return Plan(AgentAction(exact.action, fill(exact.phrase, exact.args, instruction)), PlanSource.MEMORY)
        }

        TemplateMatcher.match(instruction)?.let { return Plan(it, PlanSource.TEMPLATE) }

        val target = AgentState.tokens(instruction)
        state.patterns()
            .map { it to AgentState.similarity(target, AgentState.tokens(it.phrase)) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= 0.75 }
            ?.let { (pattern, _) ->
                return Plan(
                    AgentAction(pattern.action, fill(pattern.phrase, pattern.args, instruction)),
                    PlanSource.MEMORY,
                )
            }

        val raw = runCatching { askModel(buildPrompt(instruction)) }.getOrElse { "" }
        parse(raw)?.let { action ->
            state.remember(instruction, action.type, action.args)
            return Plan(action, PlanSource.MODEL, raw)
        }

        return Plan(
            AgentAction(ActionType.ANSWER, reply = raw.trim().ifBlank { "I could not work that out." }),
            PlanSource.FALLBACK,
            raw,
        )
    }

    private fun fill(template: String, args: Map<String, String>, instruction: String): Map<String, String> {
        val extracted = AgentState.extractSlots(template, instruction)
        return args.mapValues { (_, template) ->
            var value = template
            extracted.forEach { (key, replacement) -> value = value.replace("{$key}", replacement) }
            value
        }
    }

    private fun buildPrompt(instruction: String) = """
You convert a desktop instruction into one action. Reply with JSON only, no prose.

Actions and arguments:
  open_app          app
  send_message      contact, body
  set_alarm         time
  set_timer         time
  remind            task, time
  note              text
  draft_article     topic
  schedule_article  topic
  money             topic
  open_path         path
  answer            (no arguments, use when nothing above fits)

Examples:
  "open chrome" -> {"action":"open_app","args":{"app":"chrome"}}
  "remind me to email the client at 5pm" -> {"action":"remind","args":{"task":"email the client","time":"5pm"}}
  "what is compound interest" -> {"action":"answer","args":{}}

Instruction: "$instruction"
JSON:""".trimIndent()

    private fun parse(raw: String): AgentAction? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val body = raw.substring(start, end + 1)
        return runCatching {
            val action = Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                ?: return null
            if (action !in ActionType.all) return null
            val argsBlock = Regex("\"args\"\\s*:\\s*\\{([^}]*)}").find(body)?.groupValues?.get(1).orEmpty()
            val args = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(argsBlock)
                .associate { it.groupValues[1] to it.groupValues[2] }
                .filterValues { it.isNotBlank() }
            AgentAction(action, args)
        }.getOrNull()
    }
}

/** Rule-based matching, shared in spirit with the Android implementation. */
object TemplateMatcher {

    private val rules: List<Pair<Regex, (MatchResult) -> AgentAction>> = listOf(
        Regex("^(?:please\\s+)?remind\\s+me\\s+to\\s+(.+?)\\s+(?:at|by|around)\\s+(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.REMIND, mapOf("task" to m.groupValues[1].trim(), "time" to m.groupValues[2].trim()))
        },
        Regex("^(?:please\\s+)?(?:write|draft|compose)\\s+(?:an?\\s+)?article\\s+(?:about|on)\\s+(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.DRAFT_ARTICLE, mapOf("topic" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?(?:schedule|plan|queue)\\s+(?:an?\\s+)?(?:article|post|blog)\\s+(?:about|on)\\s+(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.SCHEDULE_ARTICLE, mapOf("topic" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?(?:how\\s+can\\s+i\\s+make\\s+money|make\\s+money|earn\\s+money|ways\\s+to\\s+make\\s+money)\\s*(?:with|from|on|using)?\\s*(.*)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.MONEY, mapOf("topic" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?set\\s+(?:an?\\s+)?timer\\s+(?:for\\s+)?(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.SET_TIMER, mapOf("time" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?set\\s+(?:an?\\s+)?alarm\\s+(?:for\\s+)?(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.SET_ALARM, mapOf("time" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?(?:note|remember|write\\s+down)\\s+(?:that\\s+)?(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.NOTE, mapOf("text" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?(?:open|go\\s+to)\\s+(?:the\\s+)?(?:folder\\s+|file\\s+|path\\s+)?([/~][^\\s]+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.OPEN_PATH, mapOf("path" to m.groupValues[1].trim()))
        },
        Regex("^(?:please\\s+)?(?:text|message|whatsapp)\\s+([a-z0-9 .]+?)\\s+(?:saying\\s+|that\\s+|to\\s+say\\s+)?(.+)$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(
                ActionType.SEND_MESSAGE,
                mapOf("contact" to m.groupValues[1].trim(), "body" to m.groupValues[2].trim()),
            )
        },
        Regex("^(?:please\\s+)?(?:open|launch|start|run)\\s+(?:the\\s+)?([a-z0-9 .]+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE) to { m ->
            AgentAction(ActionType.OPEN_APP, mapOf("app" to m.groupValues[1].trim()))
        },
    )

    /** Words that are never an app name, so "open the door" is not read as a launch. */
    private val FILLER = setOf(
        "the", "a", "an", "my", "me", "it", "this", "that", "up", "now", "door", "window", "file",
    )

    fun match(instruction: String): AgentAction? {
        val text = instruction.trim()
        rules.forEach { (regex, build) ->
            regex.find(text)?.let { match ->
                val action = build(match)
                // Guard the greedy rules against swallowing whole sentences or bare filler.
                if (action.type == ActionType.OPEN_APP) {
                    val app = action.args["app"].orEmpty()
                    if (app.isBlank() || app in FILLER || app.split(" ").size > 3) return@forEach
                }
                if (action.type == ActionType.SEND_MESSAGE) {
                    val body = action.args["body"].orEmpty()
                    // "text mum about X" is a request for advice, not a message to send.
                    if (body.startsWith("about ") || body.split(" ").size > 12) return@forEach
                }
                return action
            }
        }
        return null
    }
}

package com.jordanmind.agent.agent

/** Everything the agent is able to do on the device. */
object ActionType {
    const val OPEN_APP = "open_app"
    const val SEND_SMS = "send_sms"
    const val SET_ALARM = "set_alarm"
    const val SET_TIMER = "set_timer"
    const val REMIND = "remind"
    const val NOTE = "note"
    const val DRAFT_ARTICLE = "draft_article"
    const val SCHEDULE_ARTICLE = "schedule_article"
    const val MONEY = "money"
    const val ANSWER = "answer"
    const val UNKNOWN = "unknown"

    val all = listOf(
        OPEN_APP, SEND_SMS, SET_ALARM, SET_TIMER, REMIND, NOTE,
        DRAFT_ARTICLE, SCHEDULE_ARTICLE, MONEY, ANSWER,
    )
}

/** A concrete thing the agent decided to do. */
data class AgentAction(
    val type: String,
    val args: Map<String, String> = emptyMap(),
    val reply: String = "",
    val confident: Boolean = true,
)

/** Result of running an action, shown back to the user verbatim. */
data class ActionResult(val ok: Boolean, val message: String)

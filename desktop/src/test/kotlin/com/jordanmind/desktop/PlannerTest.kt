package com.jordanmind.desktop

import com.jordanmind.agent.core.ActionType
import com.jordanmind.agent.core.AgentState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The planner is the part most likely to break silently, so these tests cover both stages:
 * rule matching (no model) and the model fallback path.
 */
class PlannerTest {

    private fun planner(
        state: AgentState = AgentState(),
        modelReply: String = "",
    ): Pair<Planner, () -> Int> {
        var calls = 0
        val planner = Planner(state) { _ ->
            calls++
            modelReply
        }
        return planner to { calls }
    }

    @Test
    fun `template match plans an app launch without calling the model`() = runBlocking {
        val (planner, calls) = planner()
        val plan = planner.plan("open whatsapp")

        assertEquals(ActionType.OPEN_APP, plan.action.type)
        assertEquals("whatsapp", plan.action.args["app"])
        assertEquals(0, calls(), "a known template must not hit the model")
    }

    @Test
    fun `template strips filler words and the trailing app noun`() = runBlocking {
        val (planner, _) = planner()
        assertEquals("chrome", planner.plan("please open the chrome app").action.args["app"])
        assertEquals("spotify", planner.plan("launch spotify").action.args["app"])
    }

    @Test
    fun `reminder template splits task from time`() = runBlocking {
        val (planner, _) = planner()
        val plan = planner.plan("remind me to call the bank at 6pm")
        assertEquals(ActionType.REMIND, plan.action.type)
        assertEquals("call the bank", plan.action.args["task"])
        assertEquals("6pm", plan.action.args["time"])
    }

    @Test
    fun `timer is recognised before the generic open rule`() = runBlocking {
        val (planner, _) = planner()
        // "start a timer" would otherwise look like launching an app called "a timer".
        assertEquals(ActionType.SET_TIMER, planner.plan("set a timer for 10 minutes").action.type)
        assertEquals("10 minutes", planner.plan("set a timer for 10 minutes").action.args["time"])
    }

    @Test
    fun `article and schedule templates capture the topic`() = runBlocking {
        val (planner, _) = planner()
        assertEquals(ActionType.DRAFT_ARTICLE, planner.plan("write an article about solar power").action.type)
        assertEquals("solar power", planner.plan("write an article about solar power").action.args["topic"])

        val scheduled = planner.plan("schedule an article about beekeeping")
        assertEquals(ActionType.SCHEDULE_ARTICLE, scheduled.action.type)
        assertEquals("beekeeping", scheduled.action.args["topic"])
    }

    @Test
    fun `money template works with and without a topic`() = runBlocking {
        val (planner, _) = planner()
        assertEquals(ActionType.MONEY, planner.plan("how can i make money with coding").action.type)
        assertEquals("coding", planner.plan("how can i make money with coding").action.args["topic"])
        assertEquals(ActionType.MONEY, planner.plan("ways to make money").action.type)
    }

    @Test
    fun `absolute path is opened rather than treated as an app`() = runBlocking {
        val (planner, _) = planner()
        val plan = planner.plan("open /home/user/documents")
        assertEquals(ActionType.OPEN_PATH, plan.action.type)
        assertEquals("/home/user/documents", plan.action.args["path"])
    }

    @Test
    fun `a learned pattern is reused and never reaches the model`() = runBlocking {
        val state = AgentState()
        state.remember("brew me a coffee now", ActionType.NOTE, mapOf("text" to "coffee"))
        val (planner, calls) = planner(state)

        val plan = planner.plan("Brew me a coffee now")
        assertEquals(ActionType.NOTE, plan.action.type)
        assertEquals(0, calls())
    }

    @Test
    fun `near-identical phrasing falls back to the closest learned pattern`() = runBlocking {
        val state = AgentState()
        state.remember("start my focus music please", ActionType.OPEN_APP, mapOf("app" to "spotify"))
        val (planner, calls) = planner(state)

        val plan = planner.plan("start my focus music")
        assertEquals(ActionType.OPEN_APP, plan.action.type)
        assertEquals(0, calls(), "fuzzy memory match should avoid the model")
    }

    @Test
    fun `an unknown phrasing is sent to the model and the result is learned`() = runBlocking {
        val state = AgentState()
        val reply = """{"action":"open_app","args":{"app":"slack"}}"""
        val (planner, calls) = planner(state, reply)

        val plan = planner.plan("pull up my team chat thing")
        assertEquals(1, calls())
        assertEquals(ActionType.OPEN_APP, plan.action.type)
        assertEquals("slack", plan.action.args["app"])

        // Second time round it comes from memory.
        val again = planner.plan("pull up my team chat thing")
        assertEquals(1, calls(), "the learned mapping must be reused")
        assertEquals("slack", again.action.args["app"])
        assertEquals(1, state.learnedCount())
    }

    @Test
    fun `model output wrapped in prose or fences is still parsed`() = runBlocking {
        val (planner, _) = planner(
            modelReply = "Sure! Here you go:\n```json\n{\"action\":\"note\",\"args\":{\"text\":\"buy milk\"}}\n```",
        )
        val plan = planner.plan("something i have never said before")
        assertEquals(ActionType.NOTE, plan.action.type)
        assertEquals("buy milk", plan.action.args["text"])
    }

    @Test
    fun `an invalid action name is rejected instead of executed`() = runBlocking {
        val (planner, _) = planner(modelReply = """{"action":"delete_everything","args":{}}""")
        val plan = planner.plan("wipe my drive please")
        // Falls through to a plain answer, so an unexpected action can never be run.
        assertEquals(ActionType.ANSWER, plan.action.type)
    }

    @Test
    fun `no model available yields a readable answer instead of a crash`() = runBlocking {
        val (planner, _) = planner(modelReply = "")
        val plan = planner.plan("what is the meaning of life")
        assertEquals(ActionType.ANSWER, plan.action.type)
        assertTrue(plan.action.reply.isNotBlank())
    }

    @Test
    fun `slots are filled from the template, not by guessing word boundaries`() {
        val slots = AgentState.extractSlots(
            "text {contact} {body}",
            "text mum i am running late",
        )
        assertEquals("mum", slots["contact"])
        assertEquals("i am running late", slots["body"])
    }

    @Test
    fun `a template with two slots splits on its own literal text`() {
        // The " at " between the slots is what disambiguates them.
        val slots = AgentState.extractSlots(
            "remind me to {task} at {time}",
            "remind me to call the bank at 6pm",
        )
        assertEquals("call the bank", slots["task"])
        assertEquals("6pm", slots["time"])
    }

    @Test
    fun `a template with no slots yields nothing`() {
        assertTrue(AgentState.extractSlots("open chrome", "open chrome").isEmpty())
    }

    @Test
    fun `an instruction that does not fit the template yields nothing`() {
        assertTrue(AgentState.extractSlots("open {app}", "something else entirely").isEmpty())
    }

    @Test
    fun `matcher returns null for a sentence it does not understand`() {
        assertNull(TemplateMatcher.match("could you summarise the third quarter results"))
    }

    @Test
    fun `matcher does not mistake a long sentence for an app launch`() {
        // Guards the greedy app rule: >3 words must not be read as an app name.
        assertNull(TemplateMatcher.match("open the door and let the dog out please"))
    }

    @Test
    fun `matcher does not mistake a question for an outgoing message`() {
        assertNull(TemplateMatcher.match("text mum about whether i should take the job offer in nairobi"))
    }

    @Test
    fun `a real short message is still planned`() {
        val action = TemplateMatcher.match("text mum i am late")
        assertNotNull(action)
        assertEquals(ActionType.SEND_MESSAGE, action!!.type)
        assertEquals("mum", action.args["contact"])
        assertEquals("i am late", action.args["body"])
    }
}

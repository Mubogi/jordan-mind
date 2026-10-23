package com.jordanmind.agent.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentStateTest {

    @Test
    fun `normalise strips punctuation and collapses whitespace`() {
        assertEquals("open whatsapp", AgentState.normalise("Open  WhatsApp!!"))
        assertEquals("text mum im late", AgentState.normalise("Text mum, I'm late."))
        // Slot braces survive so stored templates stay matchable.
        assertEquals("open {app}", AgentState.normalise("Open {app}"))
    }

    @Test
    fun `tokens drops very short words and filler`() {
        assertEquals(setOf("open", "whatsapp"), AgentState.tokens("open the whatsapp"))
        assertEquals(setOf("open", "whatsapp"), AgentState.tokens("please open my whatsapp app now"))
    }

    @Test
    fun `similarity is 1 for identical token sets and 0 for disjoint`() {
        assertEquals(1.0, AgentState.similarity(setOf("open", "chrome"), setOf("chrome", "open")), 0.0001)
        assertEquals(0.0, AgentState.similarity(setOf("open", "chrome"), setOf("send", "email")), 0.0001)
        assertEquals(0.0, AgentState.similarity(emptySet(), setOf("a")), 0.0001)
    }

    @Test
    fun `levenshtein measures edit distance`() {
        assertEquals(0, AgentState.levenshtein("chrome", "chrome"))
        assertEquals(1, AgentState.levenshtein("chrome", "chrom"))
        assertEquals(3, AgentState.levenshtein("kitten", "sitting"))
    }

    @Test
    fun `fuzzyEquals tolerates one typo but not two`() {
        assertTrue(AgentState.fuzzyEquals("whatsapp", "whatsap"))
        assertFalse(AgentState.fuzzyEquals("whatsapp", "whats"))
    }

    @Test
    fun `cosine handles empty and orthogonal vectors`() {
        assertEquals(1.0, AgentState.cosine(mapOf("a" to 1.0, "b" to 1.0), mapOf("a" to 1.0, "b" to 1.0)), 0.0001)
        assertEquals(0.0, AgentState.cosine(mapOf("a" to 1.0), mapOf("b" to 1.0)), 0.0001)
        assertEquals(0.0, AgentState.cosine(emptyMap(), mapOf("a" to 1.0)), 0.0001)
    }

    @Test
    fun `fresh state is seeded with usable patterns`() {
        val state = AgentState()
        assertTrue(state.patterns().isNotEmpty())
        assertEquals(0, state.learnedCount(), "seeded patterns are not counted as learned")

        val openApp = state.patterns().first { it.phrase == "open {app}" }
        assertEquals(ActionType.OPEN_APP, openApp.action)
        assertEquals("{app}", openApp.args["app"])
    }

    @Test
    fun `remember stores a learned pattern and counts repeat hits`() {
        val state = AgentState()
        state.remember("Brew me a coffee now", ActionType.NOTE, mapOf("text" to "coffee"))
        assertEquals(1, state.learnedCount())

        val stored = state.patterns().first { it.learned }
        assertEquals("brew me a coffee now", stored.phrase, "phrase is normalised on the way in")
        assertEquals(1, stored.hits)

        state.remember("Brew me a coffee now", ActionType.NOTE, mapOf("text" to "coffee"))
        assertEquals(1, state.learnedCount(), "same phrase must not create a second entry")
        assertEquals(2, state.patterns().first { it.learned }.hits)
    }

    @Test
    fun `forget removes only the named pattern`() {
        val state = AgentState()
        state.remember("alpha one", ActionType.NOTE, emptyMap())
        state.remember("beta two", ActionType.NOTE, emptyMap())
        state.forget("Alpha One")
        assertEquals(1, state.learnedCount())
        assertNull(state.patterns().firstOrNull { it.phrase == "alpha one" })
    }

    @Test
    fun `resetLearned keeps seeded patterns`() {
        val seeded = AgentState().patterns().size
        val state = AgentState()
        state.remember("gamma three", ActionType.NOTE, emptyMap())
        state.resetLearned()
        assertEquals(0, state.learnedCount())
        assertEquals(seeded, state.patterns().size)
    }

    @Test
    fun `facts are keyed by normalised text and feed the prompt block`() {
        val state = AgentState()
        state.putFact("My Sister's Name", "Amina")
        assertEquals("Amina", state.facts()["my sisters name"])

        val block = state.factBlock()
        assertTrue(block.contains("Amina"))
        assertTrue(block.contains("Things you know about the user"))

        state.forgetFact("my sister's name")
        assertFalse(state.factBlock().contains("Amina"))
    }

    @Test
    fun `reminders track completion and due time`() {
        val state = AgentState()
        val past = System.currentTimeMillis() - 60_000
        val future = System.currentTimeMillis() + 3_600_000
        val due = state.addReminder("call the bank", past)
        state.addReminder("water the plants", future)

        assertEquals(2, state.pendingReminders().size)
        assertEquals(1, state.dueReminders().size)
        assertEquals("call the bank", state.dueReminders().first().task)

        state.completeReminder(due.id)
        assertEquals(1, state.pendingReminders().size)
        assertEquals(0, state.dueReminders().size)
    }

    @Test
    fun `reminder ids are unique`() {
        val state = AgentState()
        val ids = (1..50).map { state.addReminder("task $it", System.currentTimeMillis()).id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `scheduled articles are separated from drafts`() {
        val state = AgentState()
        state.addArticle(Article("1", "Draft", "body", "topic"))
        state.addArticle(Article("2", "Queued", "body", "topic", publishAt = System.currentTimeMillis() + 1000))
        state.addArticle(Article("3", "Old", "body", "topic", publishAt = 1, published = true))

        assertEquals(3, state.articles().size)
        assertEquals(listOf("Queued"), state.scheduled().map { it.title })
    }

    @Test
    fun `export and load round-trips every part of the memory`() {
        val state = AgentState()
        state.remember("brew coffee", ActionType.NOTE, mapOf("text" to "coffee"))
        state.putFact("sister", "Amina")
        val reminder = state.addReminder("call the bank", 1_700_000_000_000)
        state.addArticle(Article("a1", "Solar", "body text", "solar", publishAt = 1_700_000_500_000))

        val restored = AgentState.load(state.export())

        assertEquals(state.learnedCount(), restored.learnedCount())
        assertEquals(state.patterns().size, restored.patterns().size)
        assertEquals("Amina", restored.facts()["sister"])
        assertEquals(1, restored.pendingReminders().size)
        assertEquals(reminder.id, restored.pendingReminders().first().id)
        assertEquals("Solar", restored.articles().first().title)
        assertEquals(1_700_000_500_000, restored.articles().first().publishAt)
    }

    @Test
    fun `load falls back to a seeded state when the json is corrupt`() {
        val restored = AgentState.load("{ this is not json")
        assertNotNull(restored)
        assertTrue(restored.patterns().isNotEmpty(), "a corrupt file must not leave the agent empty")
    }

    @Test
    fun `load tolerates unknown fields so old files keep working`() {
        val restored = AgentState.load("""{"patterns":[],"facts":{},"futureField":42}""")
        assertNotNull(restored)
    }
}

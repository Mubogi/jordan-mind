package com.jordanmind.agent

import com.jordanmind.agent.data.Embedder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbedderTest {

    @Test
    fun `identical text scores one`() {
        val a = Embedder.embed("photosynthesis converts light into chemical energy")
        val b = Embedder.embed("photosynthesis converts light into chemical energy")
        assertEquals(1f, Embedder.cosine(a, b), 0.001f)
    }

    @Test
    fun `related text outscores unrelated text`() {
        val query = Embedder.embed("how does photosynthesis work in plants")
        val related = Embedder.embed("photosynthesis is the process by which plants use light")
        val unrelated = Embedder.embed("the quarterly invoice totals were recalculated")

        val relatedScore = Embedder.cosine(query, related)
        val unrelatedScore = Embedder.cosine(query, unrelated)

        assertTrue(
            "related=$relatedScore should beat unrelated=$unrelatedScore",
            relatedScore > unrelatedScore,
        )
    }

    @Test
    fun `blob round trip preserves vector`() {
        val original = Embedder.embed("distributed consensus and raft leader election")
        val restored = Embedder.fromBlob(Embedder.toBlob(original))
        assertEquals(original.size, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 0.0001f)
        }
    }

    @Test
    fun `empty text produces zero vector with no similarity`() {
        val empty = Embedder.embed("")
        val text = Embedder.embed("anything at all")
        assertEquals(Embedder.DIMS, empty.size)
        assertEquals(0f, Embedder.cosine(empty, text), 0.0001f)
    }

    @Test
    fun `short tokens are ignored`() {
        // "a", "of", "to" are below the 3-char floor, so only "algebra" contributes.
        val short = Embedder.embed("a of to")
        assertEquals(0f, short.sumOf { kotlin.math.abs(it).toDouble() }.toFloat(), 0.0001f)
    }
}

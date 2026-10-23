package com.jordanmind.agent

import com.jordanmind.agent.ui.MdBlock
import com.jordanmind.agent.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Markdown reader is small and hand-written, so it is tested directly rather than through the
 * UI. The cases below are the shapes a phone-sized model actually emits; the last few guard the
 * rule that unrecognised input must still render as text instead of vanishing.
 */
class MarkdownTest {

    @Test
    fun `plain text becomes a single paragraph`() {
        val blocks = parseMarkdown("Just a sentence.")
        assertEquals(1, blocks.size)
        assertEquals(MdBlock.Paragraph("Just a sentence."), blocks[0])
    }

    @Test
    fun `wrapped lines are joined into one paragraph`() {
        val blocks = parseMarkdown("first line\nsecond line")
        assertEquals(listOf(MdBlock.Paragraph("first line second line")), blocks)
    }

    @Test
    fun `headings keep their level and text`() {
        val blocks = parseMarkdown("# Title\n\n### Smaller")
        assertEquals(
            listOf(MdBlock.Heading(1, "Title"), MdBlock.Heading(3, "Smaller")),
            blocks,
        )
    }

    @Test
    fun `bullet lists group consecutive items`() {
        val blocks = parseMarkdown("- one\n- two\n* three\n+ four")
        assertEquals(listOf(MdBlock.Bullet(listOf("one", "two", "three", "four"))), blocks)
    }

    @Test
    fun `ordered lists group and strip their markers`() {
        val blocks = parseMarkdown("1. first\n2. second")
        assertEquals(listOf(MdBlock.Ordered(listOf("first", "second"))), blocks)
    }

    @Test
    fun `a fenced block keeps its language and body`() {
        val source = "```kotlin\nval x = 1\n```"
        val blocks = parseMarkdown(source)
        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun `an unterminated fence still yields the code`() {
        // The model may still be streaming, so a missing closing fence must not swallow the body.
        val blocks = parseMarkdown("```python\nprint(1)")
        val code = blocks.single() as MdBlock.Code
        assertEquals("python", code.language)
        assertEquals("print(1)", code.code)
    }

    @Test
    fun `a fence with no language reports none`() {
        val code = parseMarkdown("```\nplain\n```").single() as MdBlock.Code
        assertEquals(null, code.language)
        assertEquals("plain", code.code)
    }

    @Test
    fun `asterisks inside code are not treated as emphasis`() {
        // A fence body is verbatim; parsing it would corrupt a glob or a multiplication.
        val code = parseMarkdown("```\n**not bold**\n```").single() as MdBlock.Code
        assertEquals("**not bold**", code.code)
    }

    @Test
    fun `a divider is recognised`() {
        assertEquals(listOf(MdBlock.Divider), parseMarkdown("---"))
        assertEquals(listOf(MdBlock.Divider), parseMarkdown("***"))
    }

    @Test
    fun `a quote is recognised`() {
        assertEquals(listOf(MdBlock.Quote("be careful")), parseMarkdown("> be careful"))
    }

    @Test
    fun `mixed content keeps its order`() {
        val source = """
            ## Steps

            Do this first.

            1. open the file
            2. save it

            ```bash
            ./run.sh
            ```
        """.trimIndent()
        val kinds = parseMarkdown(source).map { it::class.simpleName }
        assertEquals(
            listOf("Heading", "Paragraph", "Ordered", "Code"),
            kinds,
        )
    }

    @Test
    fun `empty input yields no blocks`() {
        assertTrue(parseMarkdown("").isEmpty())
        assertTrue(parseMarkdown("\n\n   \n").isEmpty())
    }

    @Test
    fun `text after a list is still rendered`() {
        val blocks = parseMarkdown("- item\n\nclosing line")
        assertEquals(
            listOf(MdBlock.Bullet(listOf("item")), MdBlock.Paragraph("closing line")),
            blocks,
        )
    }

    @Test
    fun `a lone asterisk is not an emphasis marker`() {
        // "2 * 3" must survive intact rather than being read as the start of an italic run.
        val blocks = parseMarkdown("2 * 3")
        assertEquals(listOf(MdBlock.Paragraph("2 * 3")), blocks)
    }

    @Test
    fun `inline emphasis round trips through the renderer`() {
        val rendered = com.jordanmind.agent.ui.inlineMarkdown(
            "use **bold** and `code` here",
            androidx.compose.ui.graphics.Color.Red,
        )
        // The markers themselves must be gone; the text must remain.
        val plain = rendered.text
        assertTrue(plain.contains("bold"))
        assertTrue(plain.contains("code"))
        assertTrue("markers should not survive", !plain.contains("**") && !plain.contains("`"))
    }
}

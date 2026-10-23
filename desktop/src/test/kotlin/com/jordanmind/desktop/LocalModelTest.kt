package com.jordanmind.desktop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Exercises [LocalModel.generate] against a real `llama-cli` and model when both are present.
 *
 * The point is termination: llama-cli stays in interactive mode and waits on stdin unless
 * `--single-turn` is passed, which previously made generation hang until the timeout. Set
 * `JORDANMIND_TEST_MODEL` to a `.gguf` path to enable these; otherwise they are skipped.
 */
class LocalModelTest {

    private fun model(): File? =
        System.getenv("JORDANMIND_TEST_MODEL")?.let { File(it) }?.takeIf { it.isFile }

    private fun binary(): String? {
        System.getenv("JORDANMIND_TEST_LLAMA")?.let { if (File(it).canExecute()) return it }
        return System.getenv("PATH")?.split(File.pathSeparator)
            ?.map { File(it, "llama-cli") }
            ?.firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    private fun subject(): LocalModel? {
        val model = model()
        val llama = binary()
        if (model == null || llama == null) return null
        return LocalModel(modelFile = model, binary = llama)
    }

    @Test
    @Timeout(value = 170, unit = TimeUnit.SECONDS)
    fun `generates text and terminates without hanging on stdin`() {
        val local = subject()
        assumeTrue(local != null, "JORDANMIND_TEST_MODEL and a llama-cli on PATH are required")

        val started = System.nanoTime()
        val answer = local!!.generate("Reply with the single word: ready", maxTokens = 24)
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0

        assertTrue(answer.isNotBlank(), "expected generated text, got blank")
        // The answer must be only the reply, not llama-cli's startup banner or timing footer.
        assertFalse(answer.contains("Loading model"), "banner leaked into the reply: $answer")
        assertFalse(answer.contains("available commands"), "banner leaked into the reply: $answer")
        assertFalse(answer.contains("[ Prompt:"), "timing footer leaked into the reply: $answer")
        assertFalse(answer.contains("Exiting"), "shutdown notice leaked into the reply: $answer")
        // The hang this guards against ran until the timeout, so anything near it means a regression.
        assertTrue(seconds < 150, "generation took ${seconds}s; it likely hung waiting on stdin")
    }

    @Test
    fun `reports the model it will use`() {
        val local = subject()
        assumeTrue(local != null, "JORDANMIND_TEST_MODEL and a llama-cli on PATH are required")
        assertEquals(true, local!!.available)
        assertTrue(local.describe().contains(model()!!.name))
    }
}

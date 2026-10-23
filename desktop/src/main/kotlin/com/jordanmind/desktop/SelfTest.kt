package com.jordanmind.desktop

import com.jordanmind.agent.core.ActionType
import com.jordanmind.agent.core.AgentState
import com.jordanmind.agent.core.Article
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Headless self-test.
 *
 * The packaged launcher cannot be exercised in a normal unit test, so this mode runs the whole
 * agent stack end to end — planner, memory, storage, article export — against a throwaway
 * directory and prints what it did. It is what CI runs to prove the shipped binary actually works.
 */
object SelfTest {

    fun run(): Int {
        val root = File(System.getProperty("java.io.tmpdir"), "jordanmind-selftest-${System.nanoTime()}")
        val store = DesktopStore(root)
        val actions = DesktopActions(root)
        var failures = 0

        fun check(label: String, condition: Boolean, detail: String = "") {
            if (condition) {
                println("  PASS  $label")
            } else {
                failures++
                println("  FAIL  $label ${if (detail.isNotEmpty()) "($detail)" else ""}")
            }
        }

        println("Jordan Mind self-test")
        println("Working directory: $root")

        // 1. Templates must work with no model present at all.
        println("\nPlanning (no model configured)")
        val planner = Planner(store.state) { "" }
        runBlocking {
            val open = planner.plan("open chrome")
            check("open chrome -> open_app", open.action.type == ActionType.OPEN_APP, open.action.type)
            check("app argument extracted", open.action.args["app"] == "chrome", open.action.args["app"].orEmpty())

            val remind = planner.plan("remind me to call the bank at 6pm")
            check("reminder recognised", remind.action.type == ActionType.REMIND, remind.action.type)
            check("task extracted", remind.action.args["task"] == "call the bank", remind.action.args["task"].orEmpty())
            check("time extracted", remind.action.args["time"] == "6pm", remind.action.args["time"].orEmpty())

            val unknown = planner.plan("summarise the quarterly report")
            check("unknown input degrades to an answer", unknown.action.type == ActionType.ANSWER, unknown.action.type)
        }

        // 2. Learning: a model-shaped reply must be remembered and reused.
        println("\nLearning")
        val learning = Planner(store.state) { """{"action":"open_app","args":{"app":"slack"}}""" }
        runBlocking {
            val first = learning.plan("pull up the team chat thing")
            check("model reply is parsed", first.action.args["app"] == "slack", first.action.args["app"].orEmpty())
            check("pattern stored", store.state.learnedCount() == 1, store.state.learnedCount().toString())

            var calls = 0
            val second = Planner(store.state) { calls++; "" }
            val reuse = second.plan("pull up the team chat thing")
            check("learned pattern reused without the model", calls == 0 && reuse.action.args["app"] == "slack")
        }

        // 3. Reminders and facts.
        println("\nMemory")
        val at = System.currentTimeMillis() + 3_600_000
        store.addReminder("call the bank", at)
        store.state.putFact("sister", "Amina")
        store.persist()
        check("reminder stored", store.state.pendingReminders().size == 1)
        check("fact stored", store.state.facts()["sister"] == "Amina")

        // 4. Article export writes a real file.
        println("\nContent")
        val article = Article("t1", "Solar for small farms", "Body text here.", "solar")
        store.addArticle(article)
        val file = store.exportArticle(article)
        check("article file written", file.exists() && file.length() > 0, file.absolutePath)
        check("article file listed", store.listArticleFiles().isNotEmpty())

        // 5. Persistence survives a restart.
        println("\nPersistence")
        val reopened = DesktopStore(root)
        check("learned pattern survived", reopened.state.learnedCount() == 1, reopened.state.learnedCount().toString())
        check("reminder survived", reopened.state.pendingReminders().size == 1)
        check("fact survived", reopened.state.facts()["sister"] == "Amina")
        check("article survived", reopened.state.articles().isNotEmpty())

        // 6. Guards: the agent must refuse to open paths outside the working root.
        println("\nSafety")
        val outside = actions.run(com.jordanmind.agent.core.AgentAction(ActionType.OPEN_PATH, mapOf("path" to "/etc/passwd")))
        check("refuses paths outside the working root", !outside.ok, outside.message)
        val url = actions.openUrl("file:///etc/passwd")
        check("refuses non-http links", !url.ok, url.message)

        println("\n${if (failures == 0) "ALL CHECKS PASSED" else "$failures CHECK(S) FAILED"}")
        root.deleteRecursively()
        return if (failures == 0) 0 else 1
    }
}

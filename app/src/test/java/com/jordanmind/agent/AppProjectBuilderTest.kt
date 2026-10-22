package com.jordanmind.agent

import com.jordanmind.agent.build.AppProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppProjectBuilderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `every template generates a complete project tree`() {
        AppProjectBuilder.templates.forEach { template ->
            val parent = temp.newFolder("parent-${template.id}")
            val result = AppProjectBuilder.generate(
                parentDir = parent,
                template = template,
                appName = "Test ${template.label}",
                summary = "Generated during unit test",
                features = listOf("First feature", "Second feature"),
            )

            assertTrue("${template.id} failed: ${result.exceptionOrNull()}", result.isSuccess)
            val project = result.getOrThrow()

            val required = listOf(
                "settings.gradle.kts",
                "build.gradle.kts",
                "gradle.properties",
                "README.md",
                "app/build.gradle.kts",
                "app/src/main/AndroidManifest.xml",
                "app/src/main/res/values/strings.xml",
                "app/src/main/res/values/themes.xml",
            )
            required.forEach { path ->
                assertTrue("${template.id} missing $path", File(project, path).exists())
            }

            val activity = project.walkTopDown().first { it.name == "MainActivity.kt" }
            val source = activity.readText()
            assertTrue("${template.id} did not interpolate the package", !source.contains("__PACKAGE__"))
            assertTrue("${template.id} did not interpolate the app name", !source.contains("__APP_NAME__"))
            assertTrue("${template.id} did not interpolate features", !source.contains("__FEATURES__"))
            assertTrue("${template.id} did not interpolate summary", !source.contains("__SUMMARY__"))
            assertTrue("${template.id} kept a placeholder marker", !source.contains("__"))
        }
    }

    @Test
    fun `refuses to overwrite an existing project`() {
        val parent = temp.newFolder("dup")
        val template = AppProjectBuilder.find("tool")

        assertTrue(
            AppProjectBuilder.generate(parent, template, "Duplicate", "first", emptyList()).isSuccess
        )
        val second = AppProjectBuilder.generate(parent, template, "Duplicate", "second", emptyList())
        assertTrue("second generation should fail", second.isFailure)
    }

    @Test
    fun `app name is sanitised into a valid identifier`() {
        val parent = temp.newFolder("sanitise")
        val project = AppProjectBuilder.generate(
            parentDir = parent,
            template = AppProjectBuilder.find("business"),
            appName = "Joe's Coffee & Co!",
            summary = "Test",
            features = emptyList(),
        ).getOrThrow()

        val name = project.name
        assertTrue("unexpected project dir name: $name", name.matches(Regex("[A-Za-z0-9]+")))
    }

    @Test
    fun `generated gradle files reference the sanitised name`() {
        val parent = temp.newFolder("gradle")
        val project = AppProjectBuilder.generate(
            parentDir = parent,
            template = AppProjectBuilder.find("game"),
            appName = "Space Rocks",
            summary = "Test",
            features = listOf("Score keeping"),
        ).getOrThrow()

        val settings = File(project, "settings.gradle.kts").readText()
        assertTrue(settings.contains("SpaceRocks"))
    }

    @Test
    fun `find falls back to the first template for unknown ids`() {
        assertEquals(AppProjectBuilder.templates.first(), AppProjectBuilder.find("does-not-exist"))
    }
}

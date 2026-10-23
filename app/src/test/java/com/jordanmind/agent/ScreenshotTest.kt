package com.jordanmind.agent

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jordanmind.agent.ui.MarkdownText
import com.jordanmind.agent.ui.TabRailTestTag
import com.jordanmind.agent.ui.theme.JordanMindTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real Android UI on the JVM and writes PNGs to `docs/screenshots/android/`.
 *
 * A device or emulator is not available here (no KVM), so Robolectric is what actually produces
 * the images. It draws with the native graphics pipeline, so the output is the same Compose
 * layout and colours a phone would show, at the device size declared by [Config].
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "com.jordanmind.agent.ScreenshotTest"
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("../docs/screenshots/android").apply { mkdirs() }

    private fun capture(name: String) {
        compose.waitForIdle()
        val root = (compose.activity as Activity).window.decorView
        val bitmap = Bitmap.createBitmap(
            root.width.coerceAtLeast(1),
            root.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        root.draw(Canvas(bitmap))
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `capture every tab`() {
        compose.setContent { JordanMindTheme { JordanMindRoot() } }

        val tabs = listOf("Chat", "Agent", "Build", "Docs", "Files", "Research", "Exam")
        tabs.forEachIndexed { index, label ->
            // The rail is a LazyRow, so trailing chips are not composed until scrolled into view.
            // Scroll the rail itself first, then the chip exists to be clicked.
            runCatching { compose.onNodeWithTag(TabRailTestTag).performScrollToNode(hasText(label)) }
            compose.onNodeWithText(label).performClick()
            compose.waitForIdle()
            capture("%02d-%s".format(index + 1, label.lowercase()))
        }
    }

    @Test
    fun `renders the theme wrapper`() {
        compose.setContent { JordanMindTheme { Box(Modifier.fillMaxSize()) { Text("Jordan Mind") } } }
        compose.onNodeWithText("Jordan Mind").assertExists()
        capture("00-theme")
    }

    /**
     * Captures the markdown reader with a reply shaped like the ones this model produces. The
     * empty-model screenshots above only show the setup panel, so without this there is no image of
     * what an answer actually looks like.
     */
    @Test
    fun `renders a markdown reply`() {
        compose.setContent {
            JordanMindTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        MarkdownText(SAMPLE_REPLY)
                    }
                }
            }
        }
        compose.waitForIdle()
        capture("08-markdown")
    }
}

private val SAMPLE_REPLY = """
    ## Retry the upload

    The failure is a **400**, not a size limit, so the file itself is fine. Two things to check:

    1. Send the body as `multipart/form-data` rather than a raw stream
    2. Retry once before surfacing the error

    ```bash
    gh release upload v1.2.0 build/app-release.apk --clobber
    ```

    > The token needs `repo` scope or the upload is rejected before it starts.
""".trimIndent()

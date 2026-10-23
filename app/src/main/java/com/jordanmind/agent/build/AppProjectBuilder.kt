package com.jordanmind.agent.build

import java.io.File

/**
 * Generates complete, Gradle-buildable Android projects from templates.
 *
 * Scope note: these projects are emitted as real source trees plus a ZIP export. They are built by
 * Android Studio, `./gradlew assembleDebug`, or a Termux toolchain — not by this app. Compiling an
 * APK on-device would require bundling AGP, AAPT2, D8 and a full JVM class library; that is out of
 * scope here and is not claimed.
 */
object AppProjectBuilder {

    data class Template(
        val id: String,
        val label: String,
        val description: String,
        val defaultName: String,
        val packageSuffix: String,
    )

    val templates = listOf(
        Template("bot", "Bot", "Chat-style assistant with a message list and reply logic", "MyBot", "bot"),
        Template("business", "Business App", "Landing screen, service list and contact call-to-action", "MyBusiness", "business"),
        Template("tool", "Tool App", "Single-purpose utility with input, action and result panels", "MyTool", "tool"),
        Template("game", "Game", "Canvas-based tap/reaction game with score tracking", "MyGame", "game"),
        Template("webview", "WebView App", "Full-screen browser shell with progress and back navigation", "MyWebApp", "webview"),
        Template("automation", "Automation App", "WorkManager-driven scheduled task runner with a log view", "MyAutomation", "automation"),
    )

    fun find(id: String): Template = templates.firstOrNull { it.id == id } ?: templates.first()

    /**
     * Materialises a project under [parentDir].
     *
     * @param appName human-facing app name, e.g. "Coffee Shop"
     * @param summary one-line description of what the app does
     * @param features bullet list of requested capabilities, rendered into the app's home screen
     */
    fun generate(
        parentDir: File,
        template: Template,
        appName: String,
        summary: String,
        features: List<String>,
    ): Result<File> = runCatching {
        val safeName = appName.trim().ifBlank { template.defaultName }
            .replace(Regex("[^A-Za-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar(Char::uppercase) }
            .ifBlank { template.defaultName }

        val packageName = "com.jordanmind.generated.${template.packageSuffix}${safeName.lowercase()}"
        val projectDir = File(parentDir, safeName)
        require(!projectDir.exists()) { "\"$safeName\" already exists in Projects" }
        val module = File(projectDir, "app/src/main")
        val javaDir = File(module, "java/" + packageName.replace('.', '/'))
        javaDir.mkdirs()

        val featureList = features.filter { it.isNotBlank() }
        val featureBullets = featureList.joinToString("\n") { "• $it" }

        writeGradle(projectDir, module, safeName)
        writeManifest(module, safeName)
        writeStrings(module, safeName)
        writeTheme(module, appName)
        writeMainActivity(javaDir, packageName, template, appName, summary, featureList)
        if (template.id == "webview") writeWebViewExtras(javaDir, packageName)
        writeReadme(projectDir, appName, summary, template, featureBullets, packageName)

        projectDir
    }

    private fun writeGradle(projectDir: File, module: File, safeName: String) {
        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { google(); mavenCentral(); gradlePluginPortal() }
            }
            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "$safeName"
            include(":app")
            """.trimIndent() + "\n"
        )

        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "8.7.3" apply false
                id("org.jetbrains.kotlin.android") version "2.0.21" apply false
            }
            """.trimIndent() + "\n"
        )

        File(projectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m
            android.useAndroidX=true
            kotlin.code.style=official
            """.trimIndent() + "\n"
        )

        File(projectDir, "app/build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
            }

            android {
                namespace = "$safeName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "com.jordanmind.generated.${safeName.lowercase()}"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                kotlinOptions { jvmTarget = "17" }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("com.google.android.material:material:1.12.0")
                implementation("androidx.constraintlayout:constraintlayout:2.1.4")
            }
            """.trimIndent() + "\n"
        )
    }

    private fun writeManifest(module: File, safeName: String) {
        val manifestDir = File(module, "AndroidManifest.xml")
        manifestDir.parentFile?.mkdirs()
        manifestDir.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Generated">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
"""
        )
    }

    private fun writeStrings(module: File, safeName: String) {
        val dir = File(module, "res/values").apply { mkdirs() }
        File(dir, "strings.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$safeName</string>
</resources>
"""
        )
    }

    private fun writeTheme(module: File, appName: String) {
        val dir = File(module, "res/values").apply { mkdirs() }
        File(dir, "themes.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Generated" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">#5EEAD4</item>
        <item name="colorOnPrimary">#04231F</item>
        <item name="colorSecondary">#A78BFA</item>
        <item name="android:statusBarColor">#0B0F14</item>
    </style>
</resources>
"""
        )
    }

    private fun writeMainActivity(
        javaDir: File,
        packageName: String,
        template: Template,
        appName: String,
        summary: String,
        features: List<String>,
    ) {
        val bulletLiteral = features.joinToString(",\n            ") { "\"• ${it.replace("\"", "\\\"")}\"" }
        val body = when (template.id) {
            "bot" -> BOT_ACTIVITY
            "game" -> GAME_ACTIVITY
            "webview" -> WEBVIEW_ACTIVITY
            "automation" -> AUTOMATION_ACTIVITY
            else -> STANDARD_ACTIVITY
        }
        val source = body
            .replace("__PACKAGE__", packageName)
            .replace("__APP_NAME__", appName.replace("\"", "\\\""))
            .replace("__SUMMARY__", summary.replace("\"", "\\\""))
            .replace("__FEATURES__", bulletLiteral)
        File(javaDir, "MainActivity.kt").writeText(source)
    }

    private fun writeWebViewExtras(javaDir: File, packageName: String) {
        // The WebView template keeps everything in MainActivity to stay single-file.
    }

    private fun writeReadme(
        projectDir: File,
        appName: String,
        summary: String,
        template: Template,
        features: String,
        packageName: String,
    ) {
        File(projectDir, "README.md").writeText(
            """
            # $appName

            Generated offline by Jordan Mind from the **${template.label}** template.

            ## What it does

            $summary

            ## Features requested

            ${features.ifBlank { "• (none specified)" }}

            ## Building

            This is a complete Android project. Build it with any of:

            1. **Android Studio** — open this folder, let Gradle sync, press Run.
            2. **Command line** — `./gradlew assembleDebug` (needs a JDK 17+ and the Android SDK).
            3. **Termux on device** — install `openjdk-17` and the Android SDK, then run the same
               Gradle command.

            The resulting APK lands in `app/build/outputs/apk/debug/`.

            ## Package

            `$packageName`
            """.trimIndent() + "\n"
        )
    }

    // ------------------------------------------------------------- templates

    private const val STANDARD_ACTIVITY = """package __PACKAGE__

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val features = listOf(
            __FEATURES__
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "__APP_NAME__"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "__SUMMARY__"
            textSize = 15f
            setPadding(0, 16, 0, 24)
        })

        features.forEach { feature ->
            root.addView(TextView(this).apply {
                text = feature
                textSize = 14f
                setPadding(0, 8, 0, 8)
            })
        }

        val input = EditText(this).apply {
            hint = "Type here…"
            setPadding(0, 32, 0, 16)
        }
        val output = TextView(this).apply {
            textSize = 15f
            setPadding(0, 24, 0, 0)
        }
        val action = Button(this).apply {
            text = "Run"
            setOnClickListener {
                val value = input.text.toString().trim()
                output.text = if (value.isEmpty()) "Enter something first." else "You said: ${'$'}value"
            }
        }

        root.addView(input)
        root.addView(action)
        root.addView(output)
        setContentView(root)
    }
}
"""

    private const val BOT_ACTIVITY = """package __PACKAGE__

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val transcript = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "__APP_NAME__"
            textSize = 26f
        })

        val log = TextView(this).apply { textSize = 14f }
        val scroller = ScrollView(this).apply { addView(log) }

        val input = EditText(this).apply { hint = "Message the bot…" }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener {
                val message = input.text.toString().trim()
                if (message.isEmpty()) return@setOnClickListener
                transcript.append("You: ").append(message).append('\n')
                transcript.append("Bot: ").append(reply(message)).append("\n\n")
                log.text = transcript.toString()
                input.setText("")
            }
        }

        root.addView(scroller, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(input)
        root.addView(send)
        setContentView(root)
    }

    /**
     * Replace this with your own logic. It is deliberately a plain function so you can drop in a
     * rules engine, a local model, or a network call later.
     */
    private fun reply(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "Hey! How can I help?"
            lower.contains("price") -> "Our standard plan starts at ${'$'}10 per month."
            lower.contains("bye") -> "Goodbye!"
            else -> "I heard: ${'$'}message"
        }
    }
}
"""

    private const val GAME_ACTIVITY = """package __PACKAGE__

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GameView(this))
    }

    private class GameView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var targetX = 300f
        private var targetY = 300f
        private var score = 0
        private var radius = 90f

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            relocate()
        }

        private fun relocate() {
            targetX = Random.nextFloat() * (width.coerceAtLeast(1) - 2 * radius) + radius
            targetY = Random.nextFloat() * (height.coerceAtLeast(1) - 2 * radius) + radius
            radius = (radius - 2f).coerceAtLeast(36f)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#0B0F14"))

            paint.color = Color.parseColor("#5EEAD4")
            canvas.drawCircle(targetX, targetY, radius, paint)

            paint.color = Color.WHITE
            paint.textSize = 52f
            canvas.drawText("Score: ${'$'}score", 48f, 90f, paint)

            paint.textSize = 32f
            canvas.drawText("Tap the circle", 48f, 140f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val dx = event.x - targetX
                val dy = event.y - targetY
                if (dx * dx + dy * dy <= radius * radius) {
                    score++
                    relocate()
                    invalidate()
                }
            }
            return true
        }
    }
}
"""

    private const val WEBVIEW_ACTIVITY = """package __PACKAGE__

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(START_URL)
        }
        setContentView(webView)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    companion object {
        /** Point this at your own site or a bundled asset in app/src/main/assets. */
        const val START_URL = "https://example.com"
    }
}
"""

    private const val AUTOMATION_ACTIVITY = """package __PACKAGE__

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Skeleton for a scheduled task runner. Wire the body of [runTask] to your real work, then move the
 * trigger to WorkManager (PeriodicWorkRequest) so it survives the app being closed.
 */
class MainActivity : AppCompatActivity() {

    private val log = StringBuilder()
    private lateinit var output: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        root.addView(TextView(this).apply { text = "__APP_NAME__"; textSize = 26f })
        root.addView(TextView(this).apply { text = "__SUMMARY__"; textSize = 15f })

        output = TextView(this).apply { textSize = 13f; setPadding(0, 24, 0, 0) }
        root.addView(output)

        root.addView(Button(this).apply {
            text = "Run task now"
            setOnClickListener { runTask() }
        })

        setContentView(root)
        runTask()
    }

    private fun runTask() {
        handler.post {
            log.insert(0, "${'$'}{clock.format(Date())}  task executed\n")
            output.text = log.toString()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
"""
}

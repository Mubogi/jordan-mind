package com.jordanmind.agent.data

import android.content.Context
import java.io.File

/**
 * App-private workspace. The requested `/sdcard/JordanMind/` layout is mirrored here because
 * scoped storage on Android 10+ does not allow arbitrary top-level writes without a user-granted
 * tree URI; the app can export any folder out to real shared storage on demand.
 */
class Workspace(context: Context) {

    val root: File = File(context.filesDir, "JordanMind").apply { mkdirs() }

    val builtApks: File = dir("Built_APKs")
    val documents: File = dir("Documents")
    val projects: File = dir("Projects")
    val knowledge: File = dir("Knowledge")
    val models: File = dir("Models")

    fun dir(name: String): File = File(root, name).apply { mkdirs() }

    /** Short label shown in the UI, e.g. `JordanMind/Documents`. */
    fun label(file: File): String =
        file.absolutePath.removePrefix(root.parentFile?.absolutePath ?: "").trimStart('/')
}

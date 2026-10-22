package com.jordanmind.agent.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Locates the on-device LLM weights.
 *
 * Two sources are supported:
 *  - `assets/models/qwen-bundled.task` — baked into the APK by `-PbundleModel=...`
 *  - anything the user picks from storage (a `.task` / `.litertlm` / `.bin` file)
 *
 * Bundled assets are copied to app storage on first run because the inference API needs a real
 * file path, not an AssetFileDescriptor.
 */
class ModelRepository(private val context: Context) {

    val modelDir: File = File(context.filesDir, "models").apply { mkdirs() }

    val installedModels: List<File>
        get() = modelDir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun bundledModelName(): String? = runCatching {
        context.assets.list("models")?.firstOrNull { it.endsWith(".task") || it.endsWith(".litertlm") }
    }.getOrNull()

    suspend fun extractBundledModel(onProgress: (String) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = bundledModelName() ?: error("No bundled model in this build")
                val target = File(modelDir, "qwen-bundled.task")
                val assetLength = context.assets.openFd("models/$name").use { it.length }
                if (target.exists() && target.length() == assetLength) {
                    onProgress("Bundled model already installed")
                    return@runCatching target
                }
                onProgress("Copying bundled model (${assetLength / 1_048_576} MB)…")
                context.assets.open("models/$name").use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 20)
                        var copied = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (copied - lastReport > 64L * 1_048_576) {
                                lastReport = copied
                                onProgress("Copied ${copied / 1_048_576} / ${assetLength / 1_048_576} MB")
                            }
                        }
                    }
                }
                onProgress("Bundled model ready")
                target
            }
        }

    suspend fun importFromUri(uri: Uri, onProgress: (String) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val display = queryDisplayName(uri) ?: "imported-model.task"
                val safe = display.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val target = File(modelDir, safe)
                onProgress("Importing $safe…")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open $display" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                onProgress("Imported $safe (${target.length() / 1_048_576} MB)")
                target
            }
        }

    fun delete(file: File): Boolean = file.delete()

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
}

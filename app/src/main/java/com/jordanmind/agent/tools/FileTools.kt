package com.jordanmind.agent.tools

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Filesystem operations for the Files tab. All synchronous; callers wrap in Dispatchers.IO. */
object FileTools {

    data class Entry(val file: File, val isDirectory: Boolean) {
        val name: String get() = file.name
        val sizeLabel: String get() = if (isDirectory) "—" else humanSize(file.length())
    }

    fun list(directory: File): List<Entry> =
        directory.listFiles()
            ?.map { Entry(it, it.isDirectory) }
            ?.sortedWith(compareByDescending<Entry> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "%.2f GB".format(bytes / 1_073_741_824.0)
    }

    fun createFolder(parent: File, name: String): Result<File> = runCatching {
        val target = File(parent, name)
        require(!target.exists()) { "\"$name\" already exists" }
        require(target.mkdirs()) { "Could not create \"$name\"" }
        target
    }

    fun createFile(parent: File, name: String, content: String = ""): Result<File> = runCatching {
        val target = File(parent, name)
        require(!target.exists()) { "\"$name\" already exists" }
        target.writeText(content)
        target
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        val target = File(file.parentFile, newName)
        require(!target.exists()) { "\"$newName\" already exists" }
        require(file.renameTo(target)) { "Rename failed" }
        target
    }

    fun delete(file: File): Boolean =
        if (file.isDirectory) file.deleteRecursively() else file.delete()

    fun copy(source: File, destinationDir: File): Result<File> = runCatching {
        val target = uniqueTarget(destinationDir, source.name)
        if (source.isDirectory) source.copyRecursively(target, overwrite = false)
        else source.copyTo(target, overwrite = false)
        target
    }

    fun move(source: File, destinationDir: File): Result<File> = runCatching {
        val target = uniqueTarget(destinationDir, source.name)
        require(source.renameTo(target)) { "Move failed" }
        target
    }

    /** Adds ` (1)`, ` (2)`… until the name is free. */
    private fun uniqueTarget(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    fun zip(source: File, targetZip: File): Result<File> = runCatching {
        ZipOutputStream(FileOutputStream(targetZip)).use { out ->
            val root = source.parentFile ?: source
            val base = source
            source.walkTopDown().forEach { entry ->
                val relative = entry.relativeTo(base).path
                val entryName = if (entry.isDirectory) "$relative/" else relative
                out.putNextEntry(ZipEntry(entryName))
                if (entry.isFile) FileInputStream(entry).use { it.copyTo(out) }
                out.closeEntry()
            }
            if (source.isFile) {
                // walkTopDown on a file yields the file itself; the loop above handled it.
            }
        }
        targetZip
    }

    fun unzip(zipFile: File, destinationDir: File): Result<File> = runCatching {
        destinationDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val outFile = File(destinationDir, entry.name)
                // Guard against zip-slip.
                require(outFile.canonicalPath.startsWith(destinationDir.canonicalPath)) {
                    "Refusing unsafe entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { input.copyTo(it) }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        destinationDir
    }

    fun search(root: File, query: String, limit: Int = 200): List<Entry> {
        if (query.isBlank()) return emptyList()
        val needle = query.lowercase()
        val results = mutableListOf<Entry>()
        root.walkTopDown()
            .onEnter { it.name != ".git" }
            .forEach {
                if (results.size >= limit) return@forEach
                if (it.name.lowercase().contains(needle)) results.add(Entry(it, it.isDirectory))
            }
        return results
    }

    /** Recursively sums file sizes under [directory]. */
    fun sizeOf(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

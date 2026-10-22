package com.jordanmind.agent.data

import android.content.Context
import java.io.File

/**
 * Offline retrieval store.
 *
 * Embeddings are a deterministic hashed bag-of-words (256 dims, L2-normalised) stored as a float
 * blob per chunk. That is enough to pull the right passage out of a few hundred pages without
 * shipping a neural encoder, and it keeps the whole index on-device with no model download.
 */
class RagStore(context: Context) {

    private val helper = RagDb(context.applicationContext)

    data class Hit(val document: String, val text: String, val score: Float)

    fun addDocument(file: File): Result<Int> = runCatching {
        val text = when (file.extension.lowercase()) {
            "pdf" -> PdfText.extract(file)
            "txt", "md", "csv", "json", "kt", "java", "py" -> file.readText()
            else -> file.readText()
        }
        require(text.isNotBlank()) { "No readable text found in ${file.name}" }
        val chunks = chunk(text)
        helper.insertDocument(file.name, chunks)
        chunks.size
    }

    fun addText(name: String, text: String): Result<Int> = runCatching {
        require(text.isNotBlank()) { "Nothing to index" }
        val chunks = chunk(text)
        helper.insertDocument(name, chunks)
        chunks.size
    }

    fun documents(): List<RagDb.DocumentRow> = helper.documents()

    fun deleteDocument(id: Long) = helper.deleteDocument(id)

    fun search(query: String, limit: Int = 4): List<Hit> {
        if (query.isBlank()) return emptyList()
        val queryVector = Embedder.embed(query)
        return helper.allChunks()
            .map { (doc, text, blob) ->
                Hit(doc, text, Embedder.cosine(queryVector, Embedder.fromBlob(blob)))
            }
            .filter { it.score > 0.05f }
            .sortedByDescending { it.score }
            .take(limit)
    }

    fun chunkCount(): Int = helper.chunkCount()

    private fun chunk(text: String, targetChars: Int = 900): List<String> {
        val paragraphs = text.split(Regex("\n\\s*\n"))
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            if (current.length + trimmed.length > targetChars && current.isNotEmpty()) {
                out.add(current.toString().trim())
                current.clear()
            }
            if (trimmed.length > targetChars * 2) {
                trimmed.chunked(targetChars).forEach { out.add(it.trim()) }
            } else {
                current.append(trimmed).append("\n\n")
            }
        }
        if (current.isNotBlank()) out.add(current.toString().trim())
        return out.filter { it.isNotBlank() }
    }
}

/** Hashed bag-of-words embedding. Deterministic, dependency-free, fast enough for phone-sized corpora. */
object Embedder {

    const val DIMS = 256

    fun embed(text: String): FloatArray {
        val vector = FloatArray(DIMS)
        val tokens = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 }
        if (tokens.isEmpty()) return vector
        val idfLike = 1f / kotlin.math.sqrt(tokens.size.toFloat())
        for (token in tokens) {
            val h = token.hashCode()
            val index = ((h % DIMS) + DIMS) % DIMS
            val sign = if ((h shr 8) and 1 == 0) 1f else -1f
            vector[index] += sign * idfLike
        }
        return normalize(vector)
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0f || nb == 0f) return 0f
        return dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = kotlin.math.sqrt(sum)
        if (norm == 0f) return vector
        for (i in vector.indices) vector[i] /= norm
        return vector
    }

    fun toBlob(vector: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(vector.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun fromBlob(blob: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(blob).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(blob.size / 4)
        for (i in out.indices) out[i] = buffer.getFloat()
        return out
    }
}

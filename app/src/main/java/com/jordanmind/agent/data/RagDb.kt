package com.jordanmind.agent.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RagDb(context: Context) :
    SQLiteOpenHelper(context, "jordanmind_rag.db", null, 1) {

    data class DocumentRow(val id: Long, val name: String, val chunks: Int)
    data class ChunkRow(val document: String, val text: String, val embedding: ByteArray)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE chunks(id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL, ordinal INTEGER NOT NULL, text TEXT NOT NULL, embedding BLOB NOT NULL)")
        db.execSQL("CREATE INDEX idx_chunks_doc ON chunks(document_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS chunks")
        db.execSQL("DROP TABLE IF EXISTS documents")
        onCreate(db)
    }

    fun insertDocument(name: String, chunks: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val documentId = db.insert(
                "documents", null,
                ContentValues().apply {
                    put("name", name)
                    put("added_at", System.currentTimeMillis())
                },
            )
            chunks.forEachIndexed { index, text ->
                db.insert(
                    "chunks", null,
                    ContentValues().apply {
                        put("document_id", documentId)
                        put("ordinal", index)
                        put("text", text)
                        put("embedding", Embedder.toBlob(Embedder.embed(text)))
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun documents(): List<DocumentRow> {
        val out = mutableListOf<DocumentRow>()
        readableDatabase.rawQuery(
            "SELECT d.id, d.name, COUNT(c.id) FROM documents d LEFT JOIN chunks c ON c.document_id = d.id GROUP BY d.id ORDER BY d.added_at DESC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(DocumentRow(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)))
        }
        return out
    }

    fun deleteDocument(id: Long) {
        val db = writableDatabase
        db.delete("chunks", "document_id = ?", arrayOf(id.toString()))
        db.delete("documents", "id = ?", arrayOf(id.toString()))
    }

    fun allChunks(): List<ChunkRow> {
        val out = mutableListOf<ChunkRow>()
        readableDatabase.rawQuery(
            "SELECT d.name, c.text, c.embedding FROM chunks c JOIN documents d ON d.id = c.document_id",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(ChunkRow(cursor.getString(0), cursor.getString(1), cursor.getBlob(2)))
            }
        }
        return out
    }

    fun chunkCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM chunks", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
}

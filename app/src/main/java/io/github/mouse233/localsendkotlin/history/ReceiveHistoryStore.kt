package io.github.mouse233.localsendkotlin.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import io.github.mouse233.localsendkotlin.model.ReceiveHistoryEntry
import io.github.mouse233.localsendkotlin.model.ReceivedFile

/** Persistent history of successfully received files. Deleting it never deletes files. */
class ReceiveHistoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_NAME TEXT NOT NULL, " +
                "$COLUMN_URI TEXT NOT NULL, " +
                "$COLUMN_MIME_TYPE TEXT NOT NULL, " +
                "$COLUMN_SIZE INTEGER NOT NULL, " +
                "$COLUMN_SENDER TEXT NOT NULL, " +
                "$COLUMN_RECEIVED_AT INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(file: ReceivedFile, senderAlias: String?) {
        writableDatabase.insert(TABLE_NAME, null, ContentValues().apply {
            put(COLUMN_NAME, file.displayName)
            put(COLUMN_URI, file.uri.toString())
            put(COLUMN_MIME_TYPE, file.mimeType)
            put(COLUMN_SIZE, file.size)
            put(COLUMN_SENDER, senderAlias?.takeIf { it.isNotBlank() } ?: UNKNOWN_SENDER)
            put(COLUMN_RECEIVED_AT, System.currentTimeMillis())
        })
    }

    fun list(): List<ReceiveHistoryEntry> = readableDatabase.query(
        TABLE_NAME,
        null,
        null,
        null,
        null,
        null,
        "$COLUMN_RECEIVED_AT DESC, $COLUMN_ID DESC"
    ).use { cursor ->
        buildList {
            val id = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val name = cursor.getColumnIndexOrThrow(COLUMN_NAME)
            val uri = cursor.getColumnIndexOrThrow(COLUMN_URI)
            val mimeType = cursor.getColumnIndexOrThrow(COLUMN_MIME_TYPE)
            val size = cursor.getColumnIndexOrThrow(COLUMN_SIZE)
            val sender = cursor.getColumnIndexOrThrow(COLUMN_SENDER)
            val receivedAt = cursor.getColumnIndexOrThrow(COLUMN_RECEIVED_AT)
            while (cursor.moveToNext()) {
                add(ReceiveHistoryEntry(
                    cursor.getLong(id),
                    cursor.getString(name),
                    Uri.parse(cursor.getString(uri)),
                    cursor.getString(mimeType),
                    cursor.getLong(size),
                    cursor.getString(sender),
                    cursor.getLong(receivedAt)
                ))
            }
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }

    private companion object {
        const val DATABASE_NAME = "receive-history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "receive_history"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "display_name"
        const val COLUMN_URI = "file_uri"
        const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_SIZE = "file_size"
        const val COLUMN_SENDER = "sender_alias"
        const val COLUMN_RECEIVED_AT = "received_at"
        const val UNKNOWN_SENDER = "未知设备"
    }
}

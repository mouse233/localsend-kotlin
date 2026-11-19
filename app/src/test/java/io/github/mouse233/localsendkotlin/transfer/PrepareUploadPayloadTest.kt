package io.github.mouse233.localsendkotlin.transfer

import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrepareUploadPayloadTest {
    private val gson = Gson()

    /** Shape sent by the official LocalSend client, including the optional metadata object. */
    private val officialJson = """
        {
          "info": {
            "alias": "Nice Orange",
            "version": "2.1",
            "deviceModel": "Samsung",
            "deviceType": "mobile",
            "fingerprint": "ABCDEF0123456789",
            "port": 53317,
            "protocol": "https",
            "download": false
          },
          "files": {
            "file-1": {
              "id": "file-1",
              "fileName": "photo.jpg",
              "size": 324242,
              "fileType": "image/jpeg",
              "sha256": null,
              "preview": null,
              "metadata": { "modified": "2021-01-01T12:34:56Z", "accessed": null }
            },
            "file-2": {
              "id": "file-2",
              "fileName": "notes.pdf",
              "size": 1024,
              "fileType": "application/pdf",
              "sha256": "abc123"
            }
          }
        }
    """.trimIndent()

    @Test
    fun officialRequestIsConverted() {
        val request = parse(officialJson)!!
        assertEquals("Nice Orange", request.info.alias)
        assertEquals(listOf("file-1", "file-2"), request.files.keys.toList())
        assertEquals(
            IncomingTransferManager.IncomingFile("file-1", "photo.jpg", 324242L, "image/jpeg", null, null),
            request.files.getValue("file-1")
        )
        assertEquals("abc123", request.files.getValue("file-2").sha256)
    }

    @Test
    fun textMessageKeepsItsPreview() {
        val json = mutate { files ->
            files.remove("file-2")
            files.getAsJsonObject("file-1").apply {
                addProperty("fileName", "message.txt")
                addProperty("fileType", "text/plain")
                addProperty("preview", "hello")
            }
        }
        assertEquals("hello", parse(json)!!.messageText())
    }

    @Test
    fun fileWithoutTypeIsRejected() {
        // A single file without fileType used to crash messageText() on the main thread.
        val json = mutate { files ->
            files.remove("file-2")
            files.getAsJsonObject("file-1").remove("fileType")
        }
        assertNull(parse(json))
    }

    @Test
    fun fileWithoutNameIsRejected() {
        assertNull(parse(mutate { it.getAsJsonObject("file-2").remove("fileName") }))
    }

    @Test
    fun fileWithoutSizeIsRejected() {
        assertNull(parse(mutate { it.getAsJsonObject("file-2").remove("size") }))
    }

    @Test
    fun negativeSizeIsRejected() {
        assertNull(parse(mutate { it.getAsJsonObject("file-2").addProperty("size", -1) }))
    }

    @Test
    fun nullFileEntryIsRejected() {
        assertNull(parse(mutate { it.add("file-2", JsonNull.INSTANCE) }))
    }

    @Test
    fun mismatchedFileIdIsRejected() {
        assertNull(parse(mutate { it.getAsJsonObject("file-2").addProperty("id", "file-1") }))
    }

    @Test
    fun missingFileIdFallsBackToMapKey() {
        val request = parse(mutate { it.getAsJsonObject("file-2").remove("id") })!!
        assertEquals("file-2", request.files.getValue("file-2").id)
    }

    @Test
    fun emptyFilesAreRejected() {
        assertNull(parse(mutate { files -> files.keySet().toList().forEach(files::remove) }))
    }

    @Test
    fun missingOrIncompleteSenderIsRejected() {
        val root = gson.fromJson(officialJson, JsonObject::class.java)
        assertNull(parse(root.deepCopy().apply { remove("info") }.toString()))
        assertNull(parse(root.deepCopy().apply { getAsJsonObject("info").remove("alias") }.toString()))
    }

    private fun parse(json: String): IncomingTransferManager.PrepareUploadRequest? =
        gson.fromJson(json, PrepareUploadPayload::class.java)?.toRequest()

    private fun mutate(change: (JsonObject) -> Unit): String {
        val root = gson.fromJson(officialJson, JsonObject::class.java)
        change(root.getAsJsonObject("files"))
        return root.toString()
    }
}

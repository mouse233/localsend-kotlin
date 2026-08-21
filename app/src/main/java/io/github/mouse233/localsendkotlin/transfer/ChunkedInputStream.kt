package io.github.mouse233.localsendkotlin.transfer

import java.io.IOException
import java.io.InputStream

/** Decodes an HTTP/1.1 chunked request body without loading the file into memory. */
class ChunkedInputStream(private val source: InputStream) : InputStream() {
    private var remainingInChunk = 0L
    private var finished = false

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (finished) return -1
        if (remainingInChunk == 0L) readNextChunk()
        if (finished) return -1

        val count = source.read(buffer, offset, minOf(length.toLong(), remainingInChunk).toInt())
        if (count < 0) throw IOException("Unexpected end of chunked body")
        remainingInChunk -= count
        if (remainingInChunk == 0L) consumeCrlf()
        return count
    }

    private fun readNextChunk() {
        val sizeLine = readAsciiLine()
        val hexSize = sizeLine.substringBefore(';').trim()
        remainingInChunk = hexSize.toLongOrNull(16) ?: throw IOException("Invalid chunk size")
        if (remainingInChunk < 0) throw IOException("Negative chunk size")
        if (remainingInChunk == 0L) {
            while (readAsciiLine().isNotEmpty()) Unit
            finished = true
        }
    }

    private fun consumeCrlf() {
        if (source.read() != '\r'.code || source.read() != '\n'.code) {
            throw IOException("Invalid chunk terminator")
        }
    }

    private fun readAsciiLine(): String {
        val output = StringBuilder()
        while (output.length < MAX_LINE_LENGTH) {
            val next = source.read()
            if (next < 0) throw IOException("Unexpected end of chunked body")
            if (next == '\r'.code) {
                if (source.read() != '\n'.code) throw IOException("Invalid chunk line terminator")
                return output.toString()
            }
            output.append(next.toChar())
        }
        throw IOException("Chunk line is too long")
    }

    private companion object {
        const val MAX_LINE_LENGTH = 8 * 1024
    }
}

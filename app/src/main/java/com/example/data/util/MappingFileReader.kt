package com.example.data.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

data class MappingFileContent(
    val fileName: String,
    val text: String,
    val byteSize: Int
)

class MappingFileReadException(
    val reason: Reason
) : IOException(reason.name) {
    enum class Reason {
        UNSUPPORTED_EXTENSION,
        EMPTY,
        TOO_LARGE,
        INVALID_TEXT
    }
}

/**
 * Reads only supported text mapping files and enforces the same size limit as the repository.
 * The stream is always closed, including when validation fails.
 */
object MappingFileReader {
    const val MAX_MAPPING_FILE_BYTES = 10 * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024

    fun read(
        fileName: String,
        inputStream: InputStream,
        maxBytes: Int = MAX_MAPPING_FILE_BYTES
    ): MappingFileContent {
        require(maxBytes > 0) { "The maximum file size must be positive." }

        val normalizedName = fileName.trim()
        if (
            normalizedName.isEmpty() ||
            (!normalizedName.endsWith(".txt", ignoreCase = true) &&
                !normalizedName.endsWith(".map", ignoreCase = true))
        ) {
            throw MappingFileReadException(MappingFileReadException.Reason.UNSUPPORTED_EXTENSION)
        }

        val bytes = inputStream.use { stream ->
            val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytes = 0

            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count > maxBytes - totalBytes) {
                    throw MappingFileReadException(MappingFileReadException.Reason.TOO_LARGE)
                }
                output.write(buffer, 0, count)
                totalBytes += count
            }
            output.toByteArray()
        }

        if (bytes.isEmpty()) {
            throw MappingFileReadException(MappingFileReadException.Reason.EMPTY)
        }

        val decoded = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw MappingFileReadException(MappingFileReadException.Reason.INVALID_TEXT)
        }

        val text = decoded.removePrefix("\uFEFF")
        if (text.isBlank() || '\u0000' in text) {
            throw MappingFileReadException(MappingFileReadException.Reason.INVALID_TEXT)
        }

        return MappingFileContent(
            fileName = normalizedName,
            text = text,
            byteSize = bytes.size
        )
    }
}

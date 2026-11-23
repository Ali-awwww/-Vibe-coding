package com.example.data.util

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingFileReaderTest {

    @Test
    fun readsUtf8MappingAndRemovesByteOrderMark() {
        val bytes = "\uFEFFcom.example.MainActivity -> a:\n".toByteArray(Charsets.UTF_8)

        val result = MappingFileReader.read(" mapping.TXT ", ByteArrayInputStream(bytes))

        assertEquals("mapping.TXT", result.fileName)
        assertEquals("com.example.MainActivity -> a:\n", result.text)
        assertEquals(bytes.size, result.byteSize)
    }

    @Test
    fun rejectsUnsupportedExtension() {
        assertFailure(
            MappingFileReadException.Reason.UNSUPPORTED_EXTENSION,
            "mapping.json",
            "content".toByteArray()
        )
    }

    @Test
    fun rejectsEmptyFile() {
        assertFailure(MappingFileReadException.Reason.EMPTY, "mapping.txt", byteArrayOf())
    }

    @Test
    fun rejectsFileLargerThanConfiguredLimit() {
        assertFailure(
            MappingFileReadException.Reason.TOO_LARGE,
            "mapping.map",
            byteArrayOf(1, 2, 3, 4, 5),
            maxBytes = 4
        )
    }

    @Test
    fun acceptsFileExactlyAtConfiguredLimit() {
        val result = MappingFileReader.read(
            "mapping.map",
            ByteArrayInputStream("abcd".toByteArray()),
            maxBytes = 4
        )

        assertEquals(4, result.byteSize)
        assertEquals("abcd", result.text)
    }

    @Test
    fun rejectsMalformedUtf8AndBinaryContent() {
        assertFailure(
            MappingFileReadException.Reason.INVALID_TEXT,
            "mapping.txt",
            byteArrayOf(0xC3.toByte(), 0x28)
        )
        assertFailure(
            MappingFileReadException.Reason.INVALID_TEXT,
            "mapping.txt",
            byteArrayOf(0x61, 0x00, 0x62)
        )
    }

    private fun assertFailure(
        expected: MappingFileReadException.Reason,
        fileName: String,
        bytes: ByteArray,
        maxBytes: Int = MappingFileReader.MAX_MAPPING_FILE_BYTES
    ) {
        try {
            MappingFileReader.read(fileName, ByteArrayInputStream(bytes), maxBytes)
            throw AssertionError("Expected MappingFileReadException($expected)")
        } catch (error: MappingFileReadException) {
            assertEquals(expected, error.reason)
        }
    }
}

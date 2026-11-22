package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProguardMappingParserTest {
    private val mappingText = """
        com.example.MainActivity -> a:
            1:1:void onCreate(android.os.Bundle):42:42 -> a
        com.example.Repository -> b:
            2:4:java.lang.String load():10:12 -> c
    """.trimIndent()

    @Test
    fun parsesClassAndMethodMappings() {
        val parsed = ProguardMappingParser.parse(mappingText)
        assertEquals("com.example.MainActivity", parsed["a"]?.originalName)
        assertEquals("onCreate", parsed["a"]?.methods?.single()?.originalName)
    }

    @Test
    fun resolvesObfuscatedFrameAndMapsLineNumber() {
        val parsed = ProguardMappingParser.parse(mappingText)
        val frame = ProguardMappingParser.resolveFrame("    at a.a(SourceFile:1)", parsed)
        assertNotNull(frame)
        assertEquals("com.example.MainActivity", frame?.libraryName)
        assertEquals("onCreate", frame?.methodName)
        assertEquals(42, frame?.lineNumber)
    }

    @Test
    fun leavesUnknownClassUnresolved() {
        val parsed = ProguardMappingParser.parse(mappingText)
        assertNull(ProguardMappingParser.resolveFrame("    at x.y(SourceFile:7)", parsed))
    }
}

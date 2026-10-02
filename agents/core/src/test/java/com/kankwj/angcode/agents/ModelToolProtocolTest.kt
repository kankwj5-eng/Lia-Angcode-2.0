package com.kankwj.angcode.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelToolProtocolTest {
    @Test
    fun parsesToolRequestWithStructuredArguments() {
        val response = ModelToolProtocol.parse(
            """prefix {"type":"tool","tool":"file.read","arguments":{"path":"README.md","maxBytes":2000}}"""
        )

        assertEquals("file.read", response.requestedTool)
        assertEquals("README.md", response.toolArguments["path"])
        assertEquals("2000", response.toolArguments["maxBytes"])
    }

    @Test
    fun parsesFinalResponse() {
        val response = ModelToolProtocol.parse(
            """{"type":"final","text":"terminado"}"""
        )
        assertTrue(response.requestedTool == null)
        assertEquals("terminado", response.text)
    }
}

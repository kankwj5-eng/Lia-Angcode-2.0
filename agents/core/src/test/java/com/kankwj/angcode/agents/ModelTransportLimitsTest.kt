package com.kankwj.angcode.agents

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelTransportLimitsTest {
    @Test
    fun boundedReaderAcceptsSmallUtf8() {
        val text = "hola ñ"
        assertEquals(
            text,
            readUtf8Bounded(
                ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
                64
            )
        )
    }

    @Test
    fun boundedReaderRejectsOversizeResponse() {
        assertThrows(IllegalStateException::class.java) {
            readUtf8Bounded(
                ByteArrayInputStream(ByteArray(65) { 1 }),
                64
            )
        }
    }
}

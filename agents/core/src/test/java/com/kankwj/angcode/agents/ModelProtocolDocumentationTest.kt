package com.kankwj.angcode.agents

import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProtocolDocumentationTest {
    @Test
    fun toolDescriptionsContainIdAndMeaning() {
        val description = "file.read — Lee un archivo"
        assertTrue(description.contains("file.read"))
        assertTrue(description.contains("Lee un archivo"))
    }
}

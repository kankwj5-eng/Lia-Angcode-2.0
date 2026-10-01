package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuntimeBootstrapPathTest {
    @Test
    fun runtimePrefixMatchesAngCodePrivateFilesLayout() {
        val root = createTempDirectory("angcode-files-").toFile()
        val layout = RuntimeLayout(root)

        assertTrue(layout.prefix.path.endsWith(File.separator + "usr"))
        assertTrue(layout.home.path.endsWith(File.separator + "home"))
    }
}

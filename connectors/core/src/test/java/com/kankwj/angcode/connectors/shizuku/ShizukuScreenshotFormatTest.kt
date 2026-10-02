package com.kankwj.angcode.connectors.shizuku

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShizukuScreenshotFormatTest {
    @Test
    fun validatesPngMagic() {
        val root = createTempDirectory("angcode-png-").toFile()
        val png = File(root, "ok.png")
        png.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3
            )
        )
        assertTrue(isPngFile(png))

        val bad = File(root, "bad.png")
        bad.writeText("not png")
        assertFalse(isPngFile(bad))
    }
}

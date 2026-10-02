package com.kankwj.angcode.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidApkCatalogTest {
    @Test
    fun apkInspectionIsDiscoverable() {
        assertTrue(
            ToolCatalog.capabilities.any { it.id == "android.apk.inspect" }
        )
    }
}

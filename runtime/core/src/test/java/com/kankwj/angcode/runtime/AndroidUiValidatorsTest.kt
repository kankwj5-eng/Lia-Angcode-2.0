package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUiValidatorsTest {
    @Test
    fun acceptsOnlyHttpUrls() {
        assertTrue(AndroidUiValidators.validHttpUrl("https://example.com/path"))
        assertTrue(AndroidUiValidators.validHttpUrl("http://127.0.0.1:8080"))
        assertFalse(AndroidUiValidators.validHttpUrl("file:///etc/passwd"))
        assertFalse(AndroidUiValidators.validHttpUrl("javascript:alert(1)"))
    }

    @Test
    fun validatesAndroidPackageNames() {
        assertTrue(AndroidUiValidators.validPackageName("com.example.app"))
        assertTrue(AndroidUiValidators.validPackageName("org.mozilla.firefox"))
        assertFalse(AndroidUiValidators.validPackageName("../bad"))
        assertFalse(AndroidUiValidators.validPackageName("not-a-package"))
    }
}

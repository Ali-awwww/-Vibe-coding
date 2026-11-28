package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منطق مقارنة الإصدارات في UpdateChecker (مقارنة رقمية جزءاً بجزء).
 */
class UpdateCheckerTest {

    @Test
    fun `1_0_10 is newer than 1_0_9`() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.10", "1.0.9"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.9", "1.0.10"))
    }

    @Test
    fun `leading v is ignored`() {
        assertTrue(UpdateChecker.isNewerVersion("v1.0.8", "1.0.7"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.8", "v1.0.7"))
        assertFalse(UpdateChecker.isNewerVersion("v1.0.7", "1.0.7"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(UpdateChecker.isNewerVersion("1.0.7", "1.0.7"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun `missing parts count as zero`() {
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun `firstNewsLine returns the first non-empty line`() {
        assertEquals(
            "إضافة فحص التحديث داخل التطبيق.",
            UpdateChecker.firstNewsLine("\nإضافة فحص التحديث داخل التطبيق.\n- تفصيل آخر\n")
        )
        assertEquals("", UpdateChecker.firstNewsLine("   \n  "))
    }
}

package com.vayro.browser.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val info = DiagnosticsInfo(
        appVersion = "0.8.0", appBuild = 10, geckoView = "143.0", androidRelease = "14", sdk = 34,
        device = "Google Pixel", locale = "ar", protectionLevel = "advanced", dataSaver = "off", tabs = 3, privateTabs = 1
    )

    @Test fun includesCoreFields() {
        val text = DiagnosticsReport.format(info, "", null)
        assertTrue(text.contains("VAYRO Browser 0.8.0 (build 10)"))
        assertTrue(text.contains("GeckoView: 143.0"))
        assertTrue(text.contains("Android: 14 (API 34)"))
        assertTrue(text.contains("Tabs: 3 (private: 1)"))
    }

    @Test fun omitsEmptySections() {
        val text = DiagnosticsReport.format(info, "  ", null)
        assertFalse(text.contains("--- Events ---"))
        assertFalse(text.contains("--- Last crash ---"))
    }

    @Test fun includesEventsAndCrashWhenPresent() {
        val text = DiagnosticsReport.format(info, "2026-11-16 Content process crashed", "java.lang.IllegalStateException: boom")
        assertTrue(text.contains("--- Events ---"))
        assertTrue(text.contains("Content process crashed"))
        assertTrue(text.contains("--- Last crash ---"))
        assertTrue(text.contains("boom"))
    }

    @Test fun neverContainsUrls() {
        val text = DiagnosticsReport.format(info, "", null)
        assertFalse(text.contains("http"))
    }
}

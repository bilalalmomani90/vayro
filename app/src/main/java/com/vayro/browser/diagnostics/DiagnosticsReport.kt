package com.vayro.browser.diagnostics

/** معلومات تقنية عن التطبيق والجهاز. لا تتضمن أي روابط أو بيانات تصفح، وأعداد التبويبات فقط. */
data class DiagnosticsInfo(
    val appVersion: String,
    val appBuild: Long,
    val geckoView: String,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
    val locale: String,
    val protectionLevel: String,
    val dataSaver: String,
    val tabs: Int,
    val privateTabs: Int
)

/** يبني نص التقرير الذي يراجعه المستخدم قبل أن يشاركه (منطق خالص). */
object DiagnosticsReport {

    fun format(info: DiagnosticsInfo, events: String, lastCrash: String?): String = buildString {
        appendLine("VAYRO Browser ${info.appVersion} (build ${info.appBuild})")
        appendLine("GeckoView: ${info.geckoView}")
        appendLine("Android: ${info.androidRelease} (API ${info.sdk})")
        appendLine("Device: ${info.device}")
        appendLine("Locale: ${info.locale}")
        appendLine("Protection: ${info.protectionLevel} | Data saver: ${info.dataSaver}")
        appendLine("Tabs: ${info.tabs} (private: ${info.privateTabs})")
        if (events.isNotBlank()) {
            appendLine()
            appendLine("--- Events ---")
            appendLine(events.trim())
        }
        if (!lastCrash.isNullOrBlank()) {
            appendLine()
            appendLine("--- Last crash ---")
            appendLine(lastCrash.trim())
        }
    }.trimEnd()
}

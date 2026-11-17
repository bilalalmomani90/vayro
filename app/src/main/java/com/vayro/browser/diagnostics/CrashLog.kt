package com.vayro.browser.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * سجل محلي للانهيارات والأحداث المهمة. يُحفظ على الجهاز فقط ولا يُرسَل تلقائياً أبداً؛
 * المستخدم يراجع التقرير ويقرر مشاركته.
 */
object CrashLog {
    private var dir: File? = null

    /** يُستدعى مرة واحدة من Application: يثبّت معالجاً لأي استثناء غير ملتقَط ويحفظ أثره. */
    fun install(context: Context) {
        dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(LAST_CRASH, timestamp() + " thread=" + thread.name + "\n" + Log.getStackTraceString(error))
            } catch (_: Throwable) {
                // لا نفشل داخل معالج الانهيار
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** حدث عام (بدون روابط): نحتفظ بآخر 50 سطراً. */
    fun note(message: String) {
        try {
            val lines = (read(EVENTS)?.lines() ?: emptyList()).filter { it.isNotBlank() }
            write(EVENTS, (lines + "${timestamp()} $message").takeLast(MAX_EVENTS).joinToString("\n"))
        } catch (_: Throwable) {
        }
    }

    fun readLastCrash(): String? = read(LAST_CRASH)
    fun clearLastCrash() { file(LAST_CRASH)?.delete() }
    fun events(): String = read(EVENTS).orEmpty()

    private fun file(name: String) = dir?.let { File(it, name) }
    private fun read(name: String): String? = file(name)?.takeIf { it.exists() }?.readText()
    private fun write(name: String, text: String) { file(name)?.writeText(text) }
    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private const val LAST_CRASH = "last_crash.txt"
    private const val EVENTS = "events.txt"
    private const val MAX_EVENTS = 50
}

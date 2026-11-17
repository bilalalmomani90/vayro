package com.vayro.browser.datasaver

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** وضع توفير البيانات واستثناءات المواقع. المحرك يطبّق الوضع، والإضافة المدمجة تسأل عن الإعداد. */
class DataSaverManager(context: Context) {

    interface Applier {
        fun apply(mode: DataSaverMode)
    }

    var applier: Applier? = null

    private val prefs = context.getSharedPreferences("vayro_datasaver", Context.MODE_PRIVATE)

    fun mode(): DataSaverMode = DataSaverMode.fromKey(prefs.getString(KEY_MODE, null))

    fun setMode(mode: DataSaverMode) {
        prefs.edit().putString(KEY_MODE, mode.key).apply()
        applier?.apply(mode)
    }

    // نعمل على نسخة من المجموعة لأن SharedPreferences لا تسمح بتعديل المجموعة المُرجَعة
    private fun exceptions(): MutableSet<String> =
        (prefs.getStringSet(KEY_FULL_QUALITY, emptySet()) ?: emptySet()).toMutableSet()

    fun isFullQuality(host: String?): Boolean = host != null && host in exceptions()

    fun setFullQuality(host: String, enabled: Boolean) {
        val set = exceptions()
        if (enabled) set.add(host) else set.remove(host)
        prefs.edit().putStringSet(KEY_FULL_QUALITY, set).apply()
    }

    /** الإعداد الذي تقرأه إضافة التوفير عند كل صفحة رئيسية. */
    fun configJson(): JSONObject = JSONObject()
        .put("blockImages", mode() == DataSaverMode.STRICT)
        .put("exceptions", JSONArray(exceptions().toList()))

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_FULL_QUALITY = "full_quality_hosts"
    }
}

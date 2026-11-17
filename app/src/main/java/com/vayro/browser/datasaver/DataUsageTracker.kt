package com.vayro.browser.datasaver

import android.content.Context
import android.net.TrafficStats
import android.os.Process

/**
 * يقيس البيانات المستلمة فعلياً من تطبيق VAYRO عبر عدّاد النظام (TrafficStats) لكل يوم.
 * يشمل كل حركة التطبيق (Wi-Fi وبيانات الجوال) بما فيها التنزيلات. لا نقدّر أرقاماً من عندنا.
 */
class DataUsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences("vayro_data_usage", Context.MODE_PRIVATE)

    /** يسجل الاستهلاك منذ آخر قراءة. يعيد false إذا كان الجهاز لا يدعم العدّاد. */
    fun sample(now: Long = System.currentTimeMillis()): Boolean {
        val current = TrafficStats.getUidRxBytes(Process.myUid())
        if (current < 0) return false // UNSUPPORTED
        val last = prefs.getLong(KEY_LAST, -1L)
        val delta = DataUsageMath.delta(last, current)
        val dayKey = KEY_DAY + DataUsageMath.dayKey(now)
        prefs.edit()
            .putLong(KEY_LAST, current)
            .putLong(dayKey, prefs.getLong(dayKey, 0L) + delta)
            .apply()
        prune(now)
        return true
    }

    /** مجموع آخر [days] أيام (1 = اليوم فقط). */
    fun bytesForLastDays(days: Int, now: Long = System.currentTimeMillis()): Long =
        DataUsageMath.recentDayKeys(now, days).sumOf { prefs.getLong(KEY_DAY + it, 0L) }

    // نحتفظ بآخر 35 يوماً فقط
    private fun prune(now: Long) {
        val keep = DataUsageMath.recentDayKeys(now, 35).map { KEY_DAY + it }.toSet()
        val stale = prefs.all.keys.filter { it.startsWith(KEY_DAY) && it !in keep }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    private companion object {
        const val KEY_LAST = "last_rx"
        const val KEY_DAY = "day_"
    }
}

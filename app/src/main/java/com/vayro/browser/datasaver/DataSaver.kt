package com.vayro.browser.datasaver

import java.util.Calendar
import java.util.TimeZone

/** أوضاع توفير البيانات. */
enum class DataSaverMode(val key: String) {
    OFF("off"), STANDARD("standard"), STRICT("strict");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: OFF
    }
}

/** حسابات استهلاك البيانات (منطق خالص). */
object DataUsageMath {

    /**
     * الفرق بين قراءتين لعداد البايتات. عدّاد النظام يُصفَّر عند إعادة التشغيل،
     * فإذا صارت القراءة الحالية أصغر نعدّها استهلاكاً جديداً من الصفر. أول قراءة (last<0) تكون خط أساس.
     */
    fun delta(last: Long, current: Long): Long = when {
        last < 0 -> 0
        current >= last -> current - last
        else -> current
    }

    fun dayKey(timeMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(zone).apply { timeInMillis = timeMillis }
        return String.format("%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** مفاتيح آخر [days] أيام بدءاً باليوم الحالي. */
    fun recentDayKeys(now: Long, days: Int, zone: TimeZone = TimeZone.getDefault()): List<String> {
        val c = Calendar.getInstance(zone).apply { timeInMillis = now }
        return (0 until days).map {
            val key = dayKey(c.timeInMillis, zone)
            c.add(Calendar.DAY_OF_MONTH, -1)
            key
        }
    }
}

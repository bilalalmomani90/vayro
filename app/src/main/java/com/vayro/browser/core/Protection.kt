package com.vayro.browser.core

import java.net.URI

/** مستويات الحماية الثلاثة المطلوبة: عادي / متقدم / أقصى حماية. */
enum class ProtectionLevel(val key: String) {
    NORMAL("normal"), ADVANCED("advanced"), MAX("max");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: NORMAL
    }
}

/** سياسة ملفات الارتباط (Cookies). الافتراضي: حجب كوكيز المتتبعات فقط. */
enum class CookiePolicy(val key: String) {
    ALLOW_ALL("all"), BLOCK_TRACKERS("trackers"), BLOCK_THIRD_PARTY("third_party");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: BLOCK_TRACKERS
    }
}

/** أسباب الحجب المسجلة في التقارير. */
enum class BlockReason(val key: String) {
    AD("ad"), TRACKER("tracker"), POPUP("popup"), REDIRECT("redirect");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key }
    }
}

object HostUtil {
    /** اسم المضيف بحروف صغيرة وبدون www. أو null إن لم يكن الرابط صالحاً. */
    fun hostOf(url: String?): String? {
        if (url == null) return null
        return try {
            URI(url).host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    /** تقريب لـ "نفس الموقع": مضيفان متطابقان أو أحدهما نطاق فرعي للآخر أو يشتركان في آخر مقطعين. */
    fun sameSite(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (a == b || a.endsWith(".$b") || b.endsWith(".$a")) return true
        return registrable(a) == registrable(b)
    }

    // تقريب بسيط لا يعالج co.uk ونحوها؛ يكفي لهذه القاعدة الاستدلالية
    private fun registrable(host: String) = host.split('.').takeLast(2).joinToString(".")
}

/** قواعد القرار الخالصة (بلا Android) كي تُختبر بوحدة اختبار. */
object ProtectionLogic {

    /**
     * هل يُسمح بفتح نافذة جديدة؟
     * - الحماية مطفأة للموقع أو سمح المستخدم بالنوافذ: مسموح.
     * - المستوى العادي: مسموح (المحرك سمح بها أصلاً لأنها تلت لمسة).
     * - المتقدم/الأقصى: مسموح فقط إن سبقتها لمسة حديثة من المستخدم.
     */
    fun popupAllowed(
        level: ProtectionLevel,
        siteProtected: Boolean,
        sitePopupsAllowed: Boolean,
        recentUserTouch: Boolean
    ): Boolean {
        if (!siteProtected || sitePopupsAllowed) return true
        return when (level) {
            ProtectionLevel.NORMAL -> true
            else -> recentUserTouch
        }
    }

    /**
     * هل نحجب تحويلاً تلقائياً مريباً؟ (قاعدة استدلالية، في "أقصى حماية" فقط)
     * يُحجب التنقل الرئيسي إلى موقع آخر إذا لم يبدأه المستخدم (لا إدخال مباشر، ولا تحويل خادم،
     * ولا لمسة حديثة).
     */
    fun redirectBlocked(
        level: ProtectionLevel,
        siteProtected: Boolean,
        isDirectNavigation: Boolean,
        isServerRedirect: Boolean,
        crossSite: Boolean,
        recentUserTouch: Boolean
    ): Boolean =
        level == ProtectionLevel.MAX && siteProtected &&
            !isDirectNavigation && !isServerRedirect && crossSite && !recentUserTouch
}

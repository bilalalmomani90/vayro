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
    AD("ad"), TRACKER("tracker"), POPUP("popup"), REDIRECT("redirect"),
    BLOCKED("blocked"), // موقع في قائمة المواقع المحظورة التي أنشأها المستخدم
    DOWNLOAD("download"); // تنزيل لم يسبقه لمس من المستخدم

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

    /** يحوّل ما يكتبه المستخدم (رابط أو نطاق) إلى اسم مضيف نظيف، أو null إن لم يكن نطاقاً صالحاً. */
    fun normalizeInput(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val host = hostOf(if (text.contains("://")) text else "https://$text") ?: return null
        return host.takeIf { it.contains('.') && !it.startsWith('.') && !it.endsWith('.') }
    }

    /** هل المضيف (أو نطاقه الأب) في قائمة الحظر؟ مثال: sub.example.com يطابق example.com. */
    fun matchesBlocklist(host: String?, blocked: Set<String>): Boolean {
        if (host == null) return false
        return blocked.any { host == it || host.endsWith(".$it") }
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
/** حالة بطاقة ثقة الموقع (إشارات محلية فقط). */
enum class TrustLevel { GREEN, YELLOW, RED }

enum class TrustReason { USER_BLOCKED, NOT_ENCRYPTED, MANY_REDIRECTS, MANY_POPUPS, PROTECTION_OFF, NONE }

data class TrustResult(val level: TrustLevel, val reasons: List<TrustReason>)

/**
 * تقييم محلي للموقع من إشارات على الجهاز فقط: التشفير (HTTPS)، قائمتك المحظورة، وما حُجب في هذا الموقع هذا الأسبوع.
 * ليس فحصاً أمنياً ولا يستعلم عن قواعد بيانات التصيد أو البرمجيات الخبيثة؛ ولا يعتمد على HTTPS وحده.
 */
object SiteTrust {
    const val REDIRECTS_WARN = 3
    const val POPUPS_WARN = 5

    fun assess(
        url: String?,
        userBlocked: Boolean,
        protectionOn: Boolean,
        redirectsBlockedThisWeek: Int,
        popupsBlockedThisWeek: Int
    ): TrustResult {
        if (userBlocked) return TrustResult(TrustLevel.RED, listOf(TrustReason.USER_BLOCKED))
        val reasons = mutableListOf<TrustReason>()
        if (url != null && url.lowercase().startsWith("http://")) reasons.add(TrustReason.NOT_ENCRYPTED)
        if (redirectsBlockedThisWeek >= REDIRECTS_WARN) reasons.add(TrustReason.MANY_REDIRECTS)
        if (popupsBlockedThisWeek >= POPUPS_WARN) reasons.add(TrustReason.MANY_POPUPS)
        if (!protectionOn) reasons.add(TrustReason.PROTECTION_OFF)
        return if (reasons.isEmpty()) TrustResult(TrustLevel.GREEN, listOf(TrustReason.NONE))
        else TrustResult(TrustLevel.YELLOW, reasons)
    }
}

object ProtectionLogic {

    /** موقع "أنقذني" يعامَل بأقصى حماية لقواعد النوافذ والتحويلات والتنزيل، بغض النظر عن المستوى العام. */
    fun effectiveLevel(global: ProtectionLevel, siteStrict: Boolean): ProtectionLevel =
        if (siteStrict) ProtectionLevel.MAX else global

    /** تنزيل بلا لمسة حديثة يُحجب في المتقدم والأقصى (للمواقع المحمية فقط). */
    fun autoDownloadBlocked(level: ProtectionLevel, siteProtected: Boolean, recentUserTouch: Boolean): Boolean =
        level != ProtectionLevel.NORMAL && siteProtected && !recentUserTouch

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

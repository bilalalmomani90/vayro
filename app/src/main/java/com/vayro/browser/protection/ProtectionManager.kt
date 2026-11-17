package com.vayro.browser.protection

import android.content.Context
import android.os.SystemClock
import com.vayro.browser.core.BlockReason
import com.vayro.browser.core.CookiePolicy
import com.vayro.browser.core.HostUtil
import com.vayro.browser.core.ProtectionLevel
import com.vayro.browser.core.ProtectionLogic
import com.vayro.browser.data.BlockLogRepository

/**
 * مركز قرارات الحماية: المستوى، سياسة الكوكيز، استثناءات المواقع، السماح المؤقت، وتسجيل الحجب.
 * المحرك يستشيره ويبلّغه بالحجب؛ لا يعرف شيئاً عن الواجهة.
 */
class ProtectionManager(context: Context, private val log: BlockLogRepository) {

    /** تنبيهات للواجهة (تُستدعى على خيط الواجهة). */
    interface Listener {
        fun onPopupBlocked(url: String)
        fun onRedirectBlocked(url: String)
    }

    /** يطبّق المستوى على المحرك الفعلي (يضعه المحرك عند إنشاء الجلسة الأولى). */
    interface Applier {
        fun apply(level: ProtectionLevel, cookies: CookiePolicy)
    }

    var listener: Listener? = null
    var applier: Applier? = null

    private val prefs = context.getSharedPreferences("vayro_protection", Context.MODE_PRIVATE)
    private val tempAllowed = HashMap<String, Long>()

    fun level(): ProtectionLevel = ProtectionLevel.fromKey(prefs.getString(KEY_LEVEL, null))
    fun cookiePolicy(): CookiePolicy = CookiePolicy.fromKey(prefs.getString(KEY_COOKIES, null))

    fun setLevel(level: ProtectionLevel) {
        prefs.edit().putString(KEY_LEVEL, level.key).apply()
        applier?.apply(level, cookiePolicy())
    }

    fun setCookiePolicy(policy: CookiePolicy) {
        prefs.edit().putString(KEY_COOKIES, policy.key).apply()
        applier?.apply(level(), policy)
    }

    // ---- إعدادات كل موقع ----
    // ملاحظة: نعمل على نسخة من المجموعة لأن SharedPreferences لا تسمح بتعديل المجموعة المُرجَعة
    private fun hostSet(key: String): MutableSet<String> =
        (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()

    private fun setHost(key: String, host: String, present: Boolean) {
        val set = hostSet(key)
        if (present) set.add(host) else set.remove(host)
        prefs.edit().putStringSet(key, set).apply()
    }

    fun isProtected(host: String?): Boolean = host == null || host !in hostSet(KEY_OFF_HOSTS)
    fun setProtected(host: String, enabled: Boolean) = setHost(KEY_OFF_HOSTS, host, !enabled)

    fun popupsAllowed(host: String?): Boolean = host != null && host in hostSet(KEY_POPUP_HOSTS)
    fun setPopupsAllowed(host: String, allowed: Boolean) = setHost(KEY_POPUP_HOSTS, host, allowed)

    // ---- قرارات ----
    fun popupAllowed(pageUrl: String?, recentTouch: Boolean): Boolean {
        val host = HostUtil.hostOf(pageUrl)
        return ProtectionLogic.popupAllowed(level(), isProtected(host), popupsAllowed(host), recentTouch)
    }

    /** نافذة منبثقة حجبها المحرك أصلاً: لا نسمح بها إلا إن أطفأ المستخدم الحماية أو سمح للموقع. */
    fun popupExplicitlyAllowed(pageUrl: String?): Boolean {
        val host = HostUtil.hostOf(pageUrl)
        return !isProtected(host) || popupsAllowed(host)
    }

    fun shouldBlockRedirect(
        pageUrl: String?, targetUrl: String,
        isDirect: Boolean, isServerRedirect: Boolean, recentTouch: Boolean
    ): Boolean {
        val pageHost = HostUtil.hostOf(pageUrl)
        val crossSite = pageHost != null && !HostUtil.sameSite(pageHost, HostUtil.hostOf(targetUrl))
        return ProtectionLogic.redirectBlocked(
            level(), isProtected(pageHost), isDirect, isServerRedirect, crossSite, recentTouch
        )
    }

    // ---- السماح المؤقت (دقيقة واحدة) بعد تحذير واضح ----
    fun tempAllow(url: String) {
        tempAllowed[url] = SystemClock.elapsedRealtime() + TEMP_ALLOW_MS
    }

    fun isTempAllowed(url: String): Boolean {
        val until = tempAllowed[url] ?: return false
        if (SystemClock.elapsedRealtime() > until) {
            tempAllowed.remove(url)
            return false
        }
        return true
    }

    // ---- تسجيل الحجب (أسماء المضيفين فقط) ----
    fun report(reason: BlockReason, blockedUrl: String, pageUrl: String?) {
        log.record(reason, HostUtil.hostOf(blockedUrl).orEmpty(), HostUtil.hostOf(pageUrl).orEmpty())
    }

    private companion object {
        const val KEY_LEVEL = "level"
        const val KEY_COOKIES = "cookies"
        const val KEY_OFF_HOSTS = "off_hosts"
        const val KEY_POPUP_HOSTS = "popup_hosts"
        const val TEMP_ALLOW_MS = 60_000L
    }
}

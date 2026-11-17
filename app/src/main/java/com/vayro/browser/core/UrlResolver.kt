package com.vayro.browser.core

import java.net.URLEncoder

/**
 * يحوّل ما يكتبه المستخدم في شريط العنوان إلى رابط قابل للفتح أو إلى عملية بحث.
 * منطق خالص بلا اعتماد على Android كي يمكن اختباره بوحدة اختبار عادية.
 */
object UrlResolver {

    // محرك البحث الافتراضي (اختيار مبدئي: DuckDuckGo). غيّره هنا فقط عند اعتماد محرك آخر.
    private const val SEARCH_URL = "https://duckduckgo.com/?q="

    // نطاق مثل example.com أو sub.example.co.uk مع منفذ ومسار اختياريين
    private val DOMAIN = Regex(
        "^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}(:\\d+)?([/?#].*)?$",
        RegexOption.IGNORE_CASE
    )

    // عنوان IPv4 مع منفذ ومسار اختياريين
    private val IPV4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?([/?#].*)?$")

    /** يعيد null إذا كان الإدخال فارغاً. */
    fun resolve(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        val hasWhitespace = input.any { it.isWhitespace() }
        val lower = input.lowercase()

        // رابط كامل بمخطط http/https فقط؛ أي مخطط آخر (javascript: وغيره) لا يُفتح مباشرة
        if (!hasWhitespace && (lower.startsWith("http://") || lower.startsWith("https://"))) {
            return input
        }

        if (!hasWhitespace) {
            // الشبكة المحلية تُفتح عبر http لأن شهادات HTTPS غير متوفرة عادةً هناك
            if (lower.startsWith("localhost") || IPV4.matches(input)) return "http://$input"
            if (DOMAIN.matches(input)) return "https://$input"
        }

        // غير ذلك: عبارة بحث
        return SEARCH_URL + URLEncoder.encode(input, "UTF-8")
    }

    /** هل الرابط صفحة ويب (http/https)؟ يُستخدم للسجل والمفضلة. */
    fun isWebUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /** رابط موقع صالح لاختصار أو مجموعة، أو null إذا كان المدخل عبارة بحث أو غير صالح. */
    fun asSiteUrl(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty() || input.any { it.isWhitespace() }) return null
        val resolved = resolve(input) ?: return null
        return if (resolved.startsWith(SEARCH_URL)) null else resolved.takeIf { isWebUrl(it) }
    }
}

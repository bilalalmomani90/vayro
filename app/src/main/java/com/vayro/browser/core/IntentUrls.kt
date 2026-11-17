package com.vayro.browser.core

/** استخراج روابط الويب من نصوص المشاركة (منطق خالص). */
object IntentUrls {
    private val URL = Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE)

    /** أول رابط http/https في النص مع إزالة علامات الترقيم الملتصقة بنهايته. */
    fun extractWebUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL.find(text)?.value ?: return null
        val cleaned = match.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'')
        return cleaned.takeIf { UrlResolver.isWebUrl(it) && it.length > "https://".length }
    }
}

/** تهريب أحرف LIKE الخاصة كي يُبحث عنها حرفياً. */
object SqlLike {
    fun escape(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

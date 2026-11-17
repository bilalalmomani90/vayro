package com.vayro.browser.home

import com.vayro.browser.core.UrlResolver

enum class HomeSection(val key: String) {
    SHORTCUTS("shortcuts"), GROUPS("groups"), BOOKMARKS("bookmarks"), RECENT("recent");

    companion object {
        fun fromKey(key: String) = values().firstOrNull { it.key == key }
    }
}

/** حجم البطاقات = عدد الأعمدة في الشبكة. */
enum class CardSize(val key: String, val columns: Int) {
    COMPACT("compact", 4), NORMAL("normal", 3), LARGE("large", 2);

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: NORMAL
    }
}

enum class HomeBackground(val key: String) {
    DEFAULT("default"), VIOLET("violet"), CYAN("cyan"), DEEP("deep");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.key == key } ?: DEFAULT
    }
}

data class HomeSettings(
    val order: List<HomeSection> = HomeSection.values().toList(),
    val hidden: Set<HomeSection> = emptySet(),
    val cardSize: CardSize = CardSize.NORMAL,
    val background: HomeBackground = HomeBackground.DEFAULT,
    val showReload: Boolean = true, // تخصيص شريط الأدوات
    val showStar: Boolean = true
)

object HomeLayout {

    /** يقرأ ترتيباً محفوظاً ويصلحه: يتجاهل المجهول والمكرر ويضيف الأقسام الناقصة في النهاية. */
    fun parseOrder(raw: String?): List<HomeSection> {
        val parsed = raw.orEmpty().split(',').mapNotNull { HomeSection.fromKey(it.trim()) }.distinct()
        return parsed + HomeSection.values().filter { it !in parsed }
    }

    fun serializeOrder(order: List<HomeSection>): String = order.joinToString(",") { it.key }

    fun visible(settings: HomeSettings): List<HomeSection> = settings.order.filter { it !in settings.hidden }

    /** تحريك قسم لأعلى (delta=-1) أو لأسفل (+1) دون تجاوز الحدود. */
    fun move(order: List<HomeSection>, section: HomeSection, delta: Int): List<HomeSection> {
        val from = order.indexOf(section)
        if (from < 0) return order
        val to = (from + delta).coerceIn(0, order.size - 1)
        if (to == from) return order
        val mutable = order.toMutableList()
        mutable.removeAt(from)
        mutable.add(to, section)
        return mutable
    }
}

/** خطة فتح مجموعة مواقع بضغطة واحدة. */
object GroupPlan {
    const val MAX_TABS = 20
    const val CONFIRM_ABOVE = 8

    /** روابط الويب فقط، بلا تكرار، وبحد أقصى للتبويبات. */
    fun urlsToOpen(urls: List<String>): List<String> =
        urls.filter { UrlResolver.isWebUrl(it) }.distinct().take(MAX_TABS)
}

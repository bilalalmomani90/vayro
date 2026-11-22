package com.vayro.browser.tabs

import com.vayro.browser.engine.BrowserEngine
import com.vayro.browser.engine.PageTranslationState

/**
 * تبويب واحد. المحرك (الجلسة) لا يُنشأ إلا عند أول استخدام فعلي،
 * فالتبويبات المستعادة غير النشطة لا تستهلك ذاكرة حتى يفتحها المستخدم.
 * التبويب الخاص (isPrivate): لا سجل، ولا حفظ للجلسة، وكوكيز معزولة تُمسح عند إغلاقه.
 */
class Tab(val id: Long, val isPrivate: Boolean, private val createEngine: (Tab) -> BrowserEngine) {
    var url: String? = null
    var title: String? = null
    var canGoBack = false
    var canGoForward = false
    var isLoading = false
    var crashCount = 0 // مرات الانهيار المتتالية (لمنع حلقة إعادة تحميل لا تنتهي)
    var translation: PageTranslationState? = null // حالة ترجمة الصفحة الحالية
    var desktopMode = false // وضع موقع سطح المكتب لهذا التبويب
    var openerId: Long? = null // التبويب الذي فُتح هذا التبويب منه (رابط target=_blank)
    var isFullScreen = false // الصفحة في وضع ملء الشاشة (فيديو)
    var progress = 0

    /** رابط مستعاد لم يُحمَّل بعد (تحميل كسول). */
    var pendingUrl: String? = null

    private var engine: BrowserEngine? = null
    val engineOrNull: BrowserEngine? get() = engine

    fun engine(): BrowserEngine = engine ?: createEngine(this).also { engine = it }

    fun releaseEngine() {
        engine?.release()
        engine = null
    }
}

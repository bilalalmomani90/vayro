package com.vayro.browser.tabs

import com.vayro.browser.core.UrlResolver
import com.vayro.browser.data.HistorySink
import com.vayro.browser.engine.BrowserEngine
import com.vayro.browser.engine.PageTranslationState

/**
 * يدير قائمة التبويبات والتبويب المحدد، ويسجل السجل ويحفظ الجلسة.
 * دائماً يوجد تبويب واحد على الأقل. التبويبات الخاصة لا تُسجَّل في السجل ولا تُحفظ في الجلسة.
 */
class TabManager(
    private val engineFactory: (isPrivate: Boolean) -> BrowserEngine,
    private val store: SessionStore,
    private val history: HistorySink
) {
    interface Listener {
        fun onTabsChanged()
        fun onSelectedTabChanged(old: Tab?, new: Tab)
        fun onTabUpdated(tab: Tab)
    }

    var listener: Listener? = null

    private val _tabs = mutableListOf<Tab>()
    val tabs: List<Tab> get() = _tabs

    lateinit var selected: Tab
        private set

    private var nextId = 1L

    init {
        // استعادة التبويبات المحفوظة (بدون إنشاء محركات: التحميل كسول)
        val saved = store.load()
        if (saved != null && saved.tabs.isNotEmpty()) {
            saved.tabs.forEach { _tabs.add(createTab(it.url, it.title, false)) }
            selected = _tabs[saved.selectedIndex.coerceIn(0, _tabs.size - 1)]
        } else {
            val first = createTab(null, null, false)
            _tabs.add(first)
            selected = first
        }
    }

    private fun createTab(url: String?, title: String?, isPrivate: Boolean): Tab {
        val tab = Tab(nextId++, isPrivate) { t -> engineFactory(t.isPrivate).also { it.listener = TabListener(t) } }
        tab.url = url
        tab.title = title
        tab.pendingUrl = url
        return tab
    }

    /** يعيد محرك التبويب (ينشئه عند الحاجة) ويحمّل الرابط المستعاد إن وُجد. */
    fun activate(tab: Tab): BrowserEngine {
        val engine = tab.engine()
        tab.pendingUrl?.let {
            tab.pendingUrl = null
            engine.loadUrl(it)
        }
        return engine
    }

    fun newTab(url: String? = null, isPrivate: Boolean = false): Tab {
        val tab = createTab(url, null, isPrivate)
        _tabs.add(tab)
        selectInternal(tab)
        listener?.onTabsChanged()
        persist()
        return tab
    }

    fun select(tab: Tab) {
        if (tab === selected || tab !in _tabs) return
        selectInternal(tab)
        persist()
    }

    fun close(tab: Tab) {
        val index = _tabs.indexOf(tab)
        if (index < 0) return
        val wasSelected = tab === selected
        _tabs.removeAt(index)
        tab.releaseEngine() // إغلاق جلسة خاصة يمسح كوكيزها وبياناتها
        if (wasSelected) {
            // نختار الجار؛ وإن أُغلق آخر تبويب نفتح تبويباً عادياً فارغاً
            val next = if (_tabs.isEmpty()) createTab(null, null, false).also { _tabs.add(it) }
            else _tabs[minOf(index, _tabs.size - 1)]
            selectInternal(next)
        }
        listener?.onTabsChanged()
        persist()
    }

    /** إغلاق كل التبويبات الخاصة دفعة واحدة. */
    fun closePrivateTabs() {
        _tabs.filter { it.isPrivate }.forEach { close(it) }
    }

    /** يفتح رابطاً في التبويب المحدد. */
    fun loadInSelected(url: String) {
        val tab = selected
        tab.pendingUrl = null
        tab.url = url
        tab.title = null
        activate(tab).loadUrl(url)
        listener?.onTabUpdated(tab)
        persist()
    }

    /** يفتح عدة روابط كتبويبات جديدة (مجموعة مواقع)، ويحدد الأول؛ البقية تُحمَّل كسولاً عند فتحها. */
    fun openAllInNewTabs(urls: List<String>) {
        if (urls.isEmpty()) return
        val created = urls.map { createTab(it, null, false) }
        _tabs.addAll(created)
        selectInternal(created.first())
        listener?.onTabsChanged()
        persist()
    }

    /** إيقاف هذا المدير (عند التبديل بين مساحات العمل): حفظ ثم تحرير كل المحركات لتوفير الذاكرة. */
    fun shutdown() {
        persist()
        listener = null
        _tabs.forEach { it.releaseEngine() }
    }

    /** حفظ الجلسة: التبويبات الخاصة لا تُحفظ أبداً. */
    fun persist() {
        val saved = _tabs.filter { !it.isPrivate }
        store.save(
            SavedSession(
                saved.map { SavedTab(it.url, it.title) },
                saved.indexOf(selected).coerceAtLeast(0)
            )
        )
    }

    private fun selectInternal(tab: Tab) {
        val old = if (::selected.isInitialized) selected else null
        // تعليق التبويب السابق (يوفر ذاكرة وبطارية) قبل تبديل العرض
        // لا نترك تبويباً في ملء الشاشة عند الانتقال عنه
        old?.isFullScreen = false
        old?.engineOrNull?.exitFullScreen()
        old?.engineOrNull?.detach()
        selected = tab
        listener?.onSelectedTabChanged(old, tab)
    }

    private companion object {
        const val MAX_CRASH_RELOADS = 2
    }

    private inner class TabListener(private val tab: Tab) : BrowserEngine.Listener {
        override fun onUrlChanged(url: String) {
            // about:blank الأولي لا يعني أن المستخدم فتح صفحة
            if (url == "about:blank" && tab.url == null) return
            if (tab.url != url) tab.translation = null // صفحة جديدة: حالة الترجمة القديمة لم تعد صالحة
            tab.url = url
            listener?.onTabUpdated(tab)
        }

        override fun onTranslationState(state: PageTranslationState) {
            tab.translation = state
            listener?.onTabUpdated(tab)
        }

        override fun onTitleChanged(title: String) {
            tab.title = title.ifBlank { null }
            val url = tab.url
            if (!tab.isPrivate && url != null && UrlResolver.isWebUrl(url) && title.isNotBlank()) {
                history.updateTitle(url, title)
            }
            listener?.onTabUpdated(tab)
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            tab.isLoading = isLoading
            if (!isLoading) {
                tab.crashCount = 0 // اكتمل تحميل سليم
                // السجل: عند انتهاء التحميل، لصفحات الويب فقط، وليس في التبويب الخاص
                val url = tab.url
                if (!tab.isPrivate && url != null && UrlResolver.isWebUrl(url)) history.record(url, tab.title)
                persist()
            }
            listener?.onTabUpdated(tab)
        }

        override fun onProgressChanged(progress: Int) {
            tab.progress = progress
            listener?.onTabUpdated(tab)
        }

        override fun onCanGoBackChanged(canGoBack: Boolean) {
            tab.canGoBack = canGoBack
            listener?.onTabUpdated(tab)
        }

        override fun onCanGoForwardChanged(canGoForward: Boolean) {
            tab.canGoForward = canGoForward
            listener?.onTabUpdated(tab)
        }

        // انهيار: نتخلص من الجلسة التالفة ونعيد فتح الصفحة نفسها في جلسة جديدة (حتى 3 محاولات)
        override fun onCrashed() {
            val url = tab.url
            tab.crashCount++
            tab.releaseEngine()
            tab.isLoading = false
            tab.isFullScreen = false
            tab.canGoBack = false
            tab.canGoForward = false
            tab.pendingUrl = if (tab.crashCount <= MAX_CRASH_RELOADS) url else null
            if (tab === selected) listener?.onSelectedTabChanged(tab, tab) // يعيد تركيب العرض وتحميل الصفحة
            else listener?.onTabUpdated(tab)
        }

        override fun onFullScreenChanged(fullScreen: Boolean) {
            tab.isFullScreen = fullScreen
            listener?.onTabUpdated(tab)
        }

        // نافذة مسموحة (روابط target=_blank) تُفتح كتبويب جديد، وتبقى خاصة إن كان مصدرها خاصاً
        override fun onOpenInNewTab(url: String) {
            newTab(url, tab.isPrivate)
        }
    }
}

package com.vayro.browser.tabs

import android.view.ViewGroup
import com.vayro.browser.data.HistorySink
import com.vayro.browser.engine.BrowserEngine
import com.vayro.browser.engine.PageTranslationState
import com.vayro.browser.engine.PermissionHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ---- بدائل اختبار بلا Android ----
private class FakeEngine : BrowserEngine {
    override var listener: BrowserEngine.Listener? = null
    override var permissionHandler: PermissionHandler? = null
    val loaded = mutableListOf<String>()
    var released = false
    override fun attachTo(container: ViewGroup) {}
    override fun detach() {}
    override fun loadUrl(url: String) { loaded.add(url) }
    override fun reload() {}
    override fun goBack() {}
    override fun goForward() {}
    override fun exitFullScreen() {}
    var desktop: Boolean? = null
    override fun setDesktopMode(enabled: Boolean, reload: Boolean) { desktop = enabled }
    override fun checkTranslationSize(from: String, to: String, done: (Long?) -> Unit) { done(0L) }
    override fun translatePage(from: String, to: String, downloadModel: Boolean, onDone: (String?) -> Unit) { onDone(null) }
    override fun restoreOriginalPage() {}
    override fun setProtectionException(enabled: Boolean) {}
    override fun release() { released = true }
}

private class FakeStore(var saved: SavedSession? = null) : SessionStore {
    override fun load() = saved
    override fun save(session: SavedSession) { saved = session }
}

private class FakeHistory : HistorySink {
    val recorded = mutableListOf<String>()
    override fun record(url: String, title: String?) { recorded.add(url) }
    override fun updateTitle(url: String, title: String) {}
}

class TabManagerTest {

    private val engines = mutableListOf<FakeEngine>()
    private val history = FakeHistory()

    private fun manager(store: FakeStore = FakeStore()) =
        TabManager({ FakeEngine().also { engines.add(it) } }, store, history)

    @Test fun startsWithOneEmptyTab() {
        val m = manager()
        assertEquals(1, m.tabs.size)
        assertNull(m.selected.url)
    }

    @Test fun newTabIsSelected() {
        val m = manager()
        val t = m.newTab("https://a.com")
        assertEquals(2, m.tabs.size)
        assertTrue(m.selected === t)
    }

    @Test fun closingLastTabCreatesBlankTab() {
        val m = manager()
        m.close(m.selected)
        assertEquals(1, m.tabs.size)
        assertNull(m.selected.url)
    }

    @Test fun closingSelectedSelectsNeighbor() {
        val m = manager()
        m.newTab("https://a.com")
        m.newTab("https://b.com")
        val a = m.tabs[1]
        m.select(a)
        m.close(a)
        assertEquals("https://b.com", m.selected.url)
        assertEquals(2, m.tabs.size)
    }

    @Test fun restoreIsLazy() {
        val saved = SavedSession(listOf(SavedTab("https://a.com", "A"), SavedTab("https://b.com", "B")), 1)
        val m = manager(FakeStore(saved))
        assertEquals(2, m.tabs.size)
        assertEquals("https://b.com", m.selected.url)
        assertEquals(0, engines.size) // لم يُنشأ أي محرك بعد
        m.activate(m.selected)
        assertEquals(1, engines.size)
        assertEquals(listOf("https://b.com"), engines[0].loaded)
    }

    @Test fun loadInSelectedSetsUrlAndLoads() {
        val m = manager()
        m.loadInSelected("https://a.com")
        assertEquals("https://a.com", m.selected.url)
        assertEquals(listOf("https://a.com"), engines[0].loaded)
    }

    @Test fun historyRecordedOnlyForWebPagesWhenLoadStops() {
        val m = manager()
        val engine = m.activate(m.selected) as FakeEngine
        engine.listener!!.onUrlChanged("https://a.com")
        engine.listener!!.onLoadingChanged(false)
        engine.listener!!.onUrlChanged("about:config")
        engine.listener!!.onLoadingChanged(false)
        assertEquals(listOf("https://a.com"), history.recorded)
    }

    @Test fun privateTabsAreNotRecordedInHistory() {
        val m = manager()
        val tab = m.newTab("https://secret.com", isPrivate = true)
        val engine = m.activate(tab) as FakeEngine
        engine.listener!!.onUrlChanged("https://secret.com")
        engine.listener!!.onLoadingChanged(false)
        assertTrue(history.recorded.isEmpty())
    }

    @Test fun privateTabsAreNeverPersisted() {
        val store = FakeStore()
        val m = manager(store)
        m.newTab("https://a.com")
        m.newTab("https://secret.com", isPrivate = true)
        assertEquals(listOf("https://a.com"), store.saved!!.tabs.map { it.url }.filterNotNull())
    }

    @Test fun closingPrivateTabsKeepsNormalOnes() {
        val m = manager()
        m.newTab("https://a.com")
        m.newTab("https://p1.com", isPrivate = true)
        m.newTab("https://p2.com", isPrivate = true)
        m.closePrivateTabs()
        assertTrue(m.tabs.none { it.isPrivate })
        assertEquals(2, m.tabs.size)
    }

    @Test fun crashedTabIsRecreatedWithSameUrl() {
        val m = manager()
        m.loadInSelected("https://a.com")
        val engine = engines[0]
        engine.listener!!.onUrlChanged("https://a.com")
        engine.listener!!.onCrashed()
        assertTrue(engine.released)
        assertEquals("https://a.com", m.selected.pendingUrl) // سيُحمَّل في جلسة جديدة
        assertNull(m.selected.engineOrNull)
    }

    @Test fun repeatedCrashesStopReloading() {
        val m = manager()
        m.loadInSelected("https://a.com")
        (m.activate(m.selected) as FakeEngine).listener!!.onUrlChanged("https://a.com")
        repeat(3) { (m.activate(m.selected) as FakeEngine).listener!!.onCrashed() }
        assertNull(m.selected.pendingUrl) // بعد 3 انهيارات لا نعيد المحاولة تلقائياً
    }

    @Test fun translationStateIsKeptPerPageAndResetOnNavigation() {
        val m = manager()
        val engine = m.activate(m.selected) as FakeEngine
        engine.listener!!.onUrlChanged("https://a.com")
        engine.listener!!.onTranslationState(PageTranslationState("fr", true, false, null))
        assertEquals("fr", m.selected.translation?.detectedLanguage)
        engine.listener!!.onUrlChanged("https://b.com") // صفحة جديدة
        assertNull(m.selected.translation)
    }

    @Test fun desktopModeIsPerTabAndAppliedWhenEngineIsCreated() {
        val m = manager()
        m.setDesktopMode(m.selected, true) // لا محرك بعد: يُحفظ العلم فقط
        assertTrue(m.selected.desktopMode)
        val engine = m.activate(m.selected) as FakeEngine
        assertEquals(true, engine.desktop) // طُبِّق عند إنشاء المحرك
        val other = m.newTab("https://b.com")
        assertFalse(other.desktopMode) // لا يتسرب إلى تبويب آخر
    }

    @Test fun backFromLinkTabReturnsToOpener() {
        val m = manager()
        val opener = m.selected
        m.loadInSelected("https://a.com")
        val engine = m.activate(opener) as FakeEngine
        engine.listener!!.onOpenInNewTab("https://b.com") // رابط target=_blank
        val child = m.selected
        assertEquals(opener.id, child.openerId)
        assertTrue(m.closeToOpener(child))
        assertTrue(m.selected === opener)
        assertEquals(1, m.tabs.size)
    }

    @Test fun closeToOpenerDoesNothingWithoutOpener() {
        val m = manager()
        assertFalse(m.closeToOpener(m.selected))
        assertEquals(1, m.tabs.size)
    }

    @Test fun resetToHomeClearsPageAndReleasesEngine() {
        val m = manager()
        m.loadInSelected("https://a.com")
        val engine = engines[0]
        m.resetToHome(m.selected)
        assertNull(m.selected.url)
        assertNull(m.selected.pendingUrl)
        assertTrue(engine.released)
        assertNull(m.selected.engineOrNull)
    }

    @Test fun sessionIsPersistedOnChanges() {
        val store = FakeStore()
        val m = manager(store)
        m.newTab("https://a.com")
        assertEquals(2, store.saved!!.tabs.size)
        assertEquals(1, store.saved!!.selectedIndex)
    }
}

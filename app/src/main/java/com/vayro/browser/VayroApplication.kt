package com.vayro.browser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.vayro.browser.core.AppPrefs
import com.vayro.browser.data.BlockLogRepository
import com.vayro.browser.diagnostics.CrashLog
import com.vayro.browser.data.BookmarkRepository
import com.vayro.browser.data.BrowserDb
import com.vayro.browser.data.ShortcutRepository
import com.vayro.browser.data.SiteGroupRepository
import com.vayro.browser.data.WorkspaceRepository
import com.vayro.browser.home.HomeSettingsStore
import com.vayro.browser.workspace.WorkspaceManager
import com.vayro.browser.data.DownloadRepository
import com.vayro.browser.datasaver.DataSaverManager
import com.vayro.browser.datasaver.DataSaverMode
import com.vayro.browser.datasaver.DataUsageTracker
import com.vayro.browser.downloads.DownloadCenter
import com.vayro.browser.downloads.MediaStoreSaver
import com.vayro.browser.data.HistoryRepository
import com.vayro.browser.engine.BrowserEngine
import com.vayro.browser.engine.GeckoBrowserEngine
import com.vayro.browser.engine.GeckoDataSaverApplier
import com.vayro.browser.engine.GeckoFetcher
import com.vayro.browser.engine.GeckoProtectionApplier
import com.vayro.browser.protection.ProtectionManager
import com.vayro.browser.tabs.PrefsSessionStore
import com.vayro.browser.tabs.TabManager
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.StorageController

class VayroApplication : Application() {

    // جديد: عند إنشاء الـ Runtime نربط الحماية به ونطبق المستوى المحفوظ فوراً
    private val geckoRuntime: GeckoRuntime by lazy {
        GeckoRuntime.create(this).also { runtime ->
            val applier = GeckoProtectionApplier(runtime)
            protection.applier = applier
            applier.apply(protection.level(), protection.cookiePolicy())
            // جديد: تطبيق وضع توفير البيانات المحفوظ
            val saverApplier = GeckoDataSaverApplier(runtime, dataSaver)
            dataSaver.applier = saverApplier
            saverApplier.apply(dataSaver.mode())
        }
    }

    // جلب الملفات عبر شبكة Gecko (يشارك ملفات الارتباط)
    private val geckoFetcher: GeckoFetcher by lazy { GeckoFetcher(geckoRuntime) }

    private val db: BrowserDb by lazy { BrowserDb(this) }
    val history: HistoryRepository by lazy { HistoryRepository(db) }
    // المرحلتان 7 و8: مساحات العمل، الاختصارات، المجموعات (كلها تُصفّى بالمساحة الحالية)
    val workspaces: WorkspaceManager by lazy {
        WorkspaceManager(this, WorkspaceRepository(db)) { id -> TabManager(::createEngine, PrefsSessionStore(this, id), history) }
    }
    val bookmarks: BookmarkRepository by lazy { BookmarkRepository(db) { workspaces.currentId } }
    val shortcuts: ShortcutRepository by lazy { ShortcutRepository(db) { workspaces.currentId } }
    val siteGroups: SiteGroupRepository by lazy { SiteGroupRepository(db) { workspaces.currentId } }
    val homeSettings: HomeSettingsStore by lazy { HomeSettingsStore(this) }

    /** تُستدعى بعد أي تغيير في الصفحة الرئيسية (اختصارات/مجموعات/إعدادات) كي تُعاد رسمها. */
    var homeListener: (() -> Unit)? = null
    fun notifyHomeChanged() { homeListener?.invoke() }
    val blockLog: BlockLogRepository by lazy { BlockLogRepository(db) }
    val protection: ProtectionManager by lazy { ProtectionManager(this, blockLog) }

    // المرحلة 5: التنزيلات وتوفير البيانات
    val downloads: DownloadCenter by lazy {
        DownloadCenter(this, DownloadRepository(db), { geckoFetcher }, MediaStoreSaver(this))
    }
    val dataSaver: DataSaverManager by lazy { DataSaverManager(this) }
    val dataUsage: DataUsageTracker by lazy { DataUsageTracker(this) }

    // تعديل: مدير التبويبات صار تابعاً لمساحة العمل الحالية
    val tabManager: TabManager get() = workspaces.tabManager

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this) // أول شيء: لنلتقط أي انهيار لاحق
        AppCompatDelegate.setDefaultNightMode(AppPrefs.themeMode(this))
    }

    fun createEngine(isPrivate: Boolean): BrowserEngine = GeckoBrowserEngine(geckoRuntime, protection, downloads, isPrivate) { dataSaver.mode() != DataSaverMode.OFF }

    /** جديد: مسح بيانات المواقع (كوكيز، تخزين، ذاكرة مؤقتة، أذونات). */
    fun clearSiteData() {
        geckoRuntime.storageController.clearData(StorageController.ClearFlags.ALL)
    }
}

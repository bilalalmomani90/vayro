package com.vayro.browser.ui

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.app.PictureInPictureParams
import android.os.Build
import android.text.format.Formatter
import android.provider.Settings
import android.util.Rational
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.AppPrefs
import com.vayro.browser.core.HostUtil
import com.vayro.browser.core.IntentUrls
import com.vayro.browser.diagnostics.CrashLog
import com.vayro.browser.diagnostics.DiagnosticsInfo
import com.vayro.browser.diagnostics.DiagnosticsReport
import com.vayro.browser.core.UrlResolver
import com.vayro.browser.databinding.ActivityMainBinding
import com.vayro.browser.downloads.DownloadCenter
import com.vayro.browser.downloads.DownloadItem
import com.vayro.browser.downloads.FileSafety
import com.vayro.browser.engine.PermissionHandler
import com.vayro.browser.home.HomeController
import com.vayro.browser.workspace.WorkspaceManager
import com.vayro.browser.engine.SitePermission
import com.vayro.browser.protection.ProtectionManager
import com.vayro.browser.tabs.Tab
import com.vayro.browser.tabs.TabManager

class MainActivity : AppCompatActivity(), TabManager.Listener, PermissionHandler, ProtectionManager.Listener,
    DownloadCenter.UiListener, WorkspaceManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var home: HomeController
    private lateinit var suggestions: SuggestionController
    private var fullScreenApplied = false // هل واجهة ملء الشاشة مطبَّقة حالياً
    private var secureWindow = false // هل علم FLAG_SECURE مفعّل حالياً (التبويب الخاص)
    private val app get() = application as VayroApplication
    private val tabManager get() = app.tabManager

    // جديد (المرحلة 4): نتيجة طلب أذونات Android المعلّق
    private var pendingAndroidResult: ((Boolean) -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            pendingAndroidResult?.invoke(grants.values.all { it })
            pendingAndroidResult = null
        }

    // جديد (المرحلة 5): طلب إذن الإشعارات (Android 13+) مرة واحدة ليظهر إشعار التنزيل
    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* القرار للمستخدم */ }

    // آخر رابط فحصنا حالة المفضلة له (لتجنب استعلامات مكررة)
    private var starUrl: String? = null

    // تعديل: الرجوع يخص التبويب المحدد حالياً
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val tab = tabManager.selected
            // أولاً الخروج من ملء الشاشة، ثم الرجوع في السجل
            if (tab.isFullScreen) tab.engineOrNull?.exitFullScreen() else tab.engineOrNull?.goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, backCallback)
        setupAddressBar()
        setupButtons()

        // جديد (المرحلتان 7 و8): الصفحة الرئيسية القابلة للتخصيص ومساحات العمل
        home = HomeController(
            this, app, binding,
            onOpenUrl = { tabManager.loadInSelected(it) },
            onShowWorkspaces = { WorkspacesBottomSheet().show(supportFragmentManager, "workspaces") },
            onShowCustomize = { HomeCustomizeBottomSheet().show(supportFragmentManager, "customize") }
        )
        app.homeListener = { home.refresh(); applyToolbarSettings() }
        app.workspaces.listener = this
        applyToolbarSettings()
        home.refresh()

        // جديد (المرحلة 9): اقتراحات العنوان المحلية + روابط تصل والتطبيق مفتوح
        suggestions = SuggestionController(this, app, binding.addressInput, binding.addressBar) { url ->
            hideKeyboard()
            tabManager.loadInSelected(url)
        }
        addOnNewIntentListener { handleExternalIntent(it) }

        // التبويبات تعيش في Application، فنعيد عرض التبويب المحدد فقط (لا حاجة لحفظ الرابط هنا)
        tabManager.listener = this
        app.protection.listener = this // تنبيهات الحجب (نوافذ/تحويلات)
        app.downloads.uiListener = this // تنبيهات التنزيل (تحذير/بدء/اكتمال)
        mountSelected()
        if (savedInstanceState == null) {
            handleExternalIntent(intent)
            offerCrashReport()
        }
    }

    private fun setupAddressBar() {
        binding.addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submitAddress()
                true
            } else false
        }
    }

    private fun setupButtons() {
        binding.goButton.setOnClickListener { submitAddress() }
        binding.reloadButton.setOnClickListener {
            if (tabManager.selected.url != null) tabManager.selected.engineOrNull?.reload()
        }
        binding.menuButton.setOnClickListener { showMenu(it) }
        // جديد: زر التبويبات وزر المفضلة
        binding.tabsButton.setOnClickListener { TabsBottomSheet().show(supportFragmentManager, "tabs") }
        binding.starButton.setOnClickListener { toggleBookmark() }
    }

    private fun submitAddress() {
        val target = UrlResolver.resolve(binding.addressInput.text.toString()) ?: return
        suggestions.dismiss()
        hideKeyboard()
        tabManager.loadInSelected(target) // تعديل: الفتح يتم عبر مدير التبويبات
    }

    private fun toggleBookmark() {
        val tab = tabManager.selected
        val url = tab.url
        if (url == null || !UrlResolver.isWebUrl(url)) {
            Toast.makeText(this, R.string.bookmark_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        app.bookmarks.toggle(url, tab.title) { nowBookmarked ->
            if (url == tabManager.selected.url) setStar(nowBookmarked)
            Toast.makeText(
                this,
                if (nowBookmarked) R.string.bookmark_added else R.string.bookmark_removed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.main_menu, menu)
            menu.findItem(R.id.menu_forward).isEnabled = tabManager.selected.canGoForward
            // الترجمة: "عرض الأصل" يظهر فقط والصفحة مترجمة، و"ترجمة الصفحة" عكسه
            val translated = tabManager.selected.translation?.isTranslated == true
            menu.findItem(R.id.menu_translate).isVisible = !translated
            menu.findItem(R.id.menu_translate_restore).isVisible = translated
            menu.findItem(R.id.menu_close_private).isVisible = tabManager.tabs.any { it.isPrivate }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_new_tab -> tabManager.newTab()
                    R.id.menu_bookmarks -> LibraryBottomSheet.newInstance(true).show(supportFragmentManager, "library")
                    R.id.menu_history -> LibraryBottomSheet.newInstance(false).show(supportFragmentManager, "library")
                    R.id.menu_new_private_tab -> tabManager.newTab(null, isPrivate = true)
                    R.id.menu_close_private -> tabManager.closePrivateTabs()
                    R.id.menu_forward -> tabManager.selected.engineOrNull?.goForward()
                    R.id.menu_share -> sharePage()
                    R.id.menu_translate -> translatePage()
                    R.id.menu_translate_restore -> tabManager.selected.engineOrNull?.restoreOriginalPage()
                    R.id.menu_about -> showDiagnostics(includeCrash = false)
                    R.id.menu_add_to_home -> addToHomeScreen()
                    R.id.menu_default_browser -> openDefaultBrowserSettings()
                    R.id.menu_workspaces -> WorkspacesBottomSheet().show(supportFragmentManager, "workspaces")
                    R.id.menu_add_shortcut -> addPageToShortcuts()
                    R.id.menu_add_to_group -> addPageToGroup()
                    R.id.menu_downloads -> DownloadsBottomSheet().show(supportFragmentManager, "downloads")
                    R.id.menu_data_saver ->
                        DataSaverBottomSheet.newInstance(HostUtil.hostOf(tabManager.selected.url))
                            .show(supportFragmentManager, "datasaver")
                    R.id.menu_site_protection -> openSiteProtection()
                    R.id.menu_report -> ReportBottomSheet().show(supportFragmentManager, "report")
                    R.id.menu_privacy -> PrivacyBottomSheet().show(supportFragmentManager, "privacy")
                    R.id.menu_language_ar -> setLanguage("ar")
                    R.id.menu_language_en -> setLanguage("en")
                    R.id.menu_theme_light -> AppPrefs.setThemeMode(this@MainActivity, AppCompatDelegate.MODE_NIGHT_NO)
                    R.id.menu_theme_dark -> AppPrefs.setThemeMode(this@MainActivity, AppCompatDelegate.MODE_NIGHT_YES)
                    R.id.menu_theme_system -> AppPrefs.setThemeMode(this@MainActivity, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun openSiteProtection() {
        val host = HostUtil.hostOf(tabManager.selected.url)
        if (host == null) {
            Toast.makeText(this, R.string.site_open_first, Toast.LENGTH_SHORT).show()
            return
        }
        SiteProtectionBottomSheet.newInstance(host).show(supportFragmentManager, "site")
    }

    /** رابط يصل من تطبيق آخر (VIEW) أو من المشاركة (SEND): يُفتح في تبويب جديد، وhttp/https فقط. */
    private fun handleExternalIntent(intent: Intent?) {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { UrlResolver.isWebUrl(it) }
            Intent.ACTION_SEND -> IntentUrls.extractWebUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            else -> null
        } ?: return
        tabManager.newTab(url)
    }

    // ---- التشخيص والانهيارات (محلي بالكامل، والمشاركة بقرار المستخدم) ----

    private fun diagnosticsText(includeCrash: Boolean): String {
        val pkg = packageManager.getPackageInfo(packageName, 0)
        val info = DiagnosticsInfo(
            appVersion = pkg.versionName.orEmpty(),
            appBuild = pkg.longVersionCode,
            geckoView = com.vayro.browser.BuildConfig.GECKOVIEW_VERSION,
            androidRelease = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            locale = resources.configuration.locales[0].toLanguageTag(),
            protectionLevel = app.protection.level().key,
            dataSaver = app.dataSaver.mode().key,
            tabs = tabManager.tabs.size,
            privateTabs = tabManager.tabs.count { it.isPrivate }
        )
        return DiagnosticsReport.format(info, CrashLog.events(), if (includeCrash) CrashLog.readLastCrash() else null)
    }

    private fun showDiagnostics(includeCrash: Boolean) {
        val text = diagnosticsText(includeCrash)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.diag_title)
            .setMessage(text)
            .setPositiveButton(R.string.diag_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, "VAYRO diagnostics")
                }
                startActivity(Intent.createChooser(send, getString(R.string.diag_share)))
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    /** إن انهار التطبيق في المرة السابقة نعرض الأمر مرة واحدة: عرض التقرير أو حذفه. */
    private fun offerCrashReport() {
        if (CrashLog.readLastCrash() == null) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_message)
            .setPositiveButton(R.string.crash_view) { _, _ ->
                showDiagnostics(includeCrash = true)
                CrashLog.clearLastCrash() // يُعرض مرة واحدة فقط
            }
            .setNegativeButton(R.string.crash_dismiss) { _, _ -> CrashLog.clearLastCrash() }
            .show()
    }

    /** ترجمة محلية للصفحة إلى لغة التطبيق الحالية (عربي/إنجليزي). النموذج يُنزَّل مرة واحدة بعد موافقتك. */
    private fun translatePage() {
        val tab = tabManager.selected
        val state = tab.translation
        val from = state?.detectedLanguage
        val engine = tab.engineOrNull
        if (engine == null || state == null || !state.canTranslate || from == null) {
            Toast.makeText(this, R.string.tr_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val to = resources.configuration.locales[0].language
        if (from.substringBefore('-').equals(to, ignoreCase = true)) {
            Toast.makeText(this, R.string.tr_same_language, Toast.LENGTH_SHORT).show()
            return
        }
        engine.checkTranslationSize(from, to) { size ->
            when {
                size == null -> Toast.makeText(this, R.string.tr_unavailable, Toast.LENGTH_LONG).show()
                size > 0 -> MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.tr_download_confirm, Formatter.formatShortFileSize(this, size)))
                    .setPositiveButton(R.string.tr_download_go) { _, _ -> startTranslation(engine, from, to, true) }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
                else -> startTranslation(engine, from, to, false) // النموذج منزَّل مسبقاً
            }
        }
    }

    private fun startTranslation(engine: com.vayro.browser.engine.BrowserEngine, from: String, to: String, download: Boolean) {
        Toast.makeText(this, R.string.tr_started, Toast.LENGTH_SHORT).show()
        engine.translatePage(from, to, download) { error ->
            if (error != null) Toast.makeText(this, getString(R.string.tr_failed, error), Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePage() {
        val (url, title) = currentWebPage() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        startActivity(Intent.createChooser(send, getString(R.string.menu_share)))
    }

    private fun openDefaultBrowserSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** ملء الشاشة: إخفاء الشريط العلوي وأشرطة النظام، ومنع انطفاء الشاشة أثناء الفيديو. */
    private fun applyFullScreen(full: Boolean) {
        if (full) binding.progressBar.visibility = View.GONE
        if (full == fullScreenApplied) return
        fullScreenApplied = full
        binding.topBar.visibility = if (full) View.GONE else View.VISIBLE
        val controller = WindowCompat.getInsetsController(window, binding.root)
        if (full) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** إضافة الموقع الحالي إلى الشاشة الرئيسية للهاتف كاختصار يشبه التطبيق. */
    private fun addToHomeScreen() {
        val (url, title) = currentWebPage() ?: return
        val ok = PinnedShortcuts.pin(this, url, title ?: HostUtil.hostOf(url).orEmpty())
        Toast.makeText(this, if (ok) R.string.home_screen_requested else R.string.home_screen_unsupported, Toast.LENGTH_LONG).show()
    }

    /** مظهر التبويب الخاص: شريط ملوّن، تلميح مختلف، تنبيه في الرئيسية، ومنع لقطات الشاشة. */
    private fun applyPrivateMode(isPrivate: Boolean) {
        binding.topBar.setBackgroundColor(
            if (isPrivate) ColorUtils.setAlphaComponent(ContextCompat.getColor(this, R.color.vayro_primary), 0x40)
            else android.graphics.Color.TRANSPARENT
        )
        binding.addressInput.setHint(if (isPrivate) R.string.address_hint_private else R.string.address_hint)
        binding.privateNotice.visibility = if (isPrivate) View.VISIBLE else View.GONE
        if (isPrivate != secureWindow) {
            secureWindow = isPrivate
            if (isPrivate) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** إظهار/إخفاء أزرار الشريط حسب تخصيص المستخدم. */
    private fun applyToolbarSettings() {
        val settings = app.homeSettings.load()
        binding.reloadButton.visibility = if (settings.showReload) View.VISIBLE else View.GONE
        binding.starButton.visibility = if (settings.showStar) View.VISIBLE else View.GONE
    }

    private fun currentWebPage(): Pair<String, String?>? {
        val tab = tabManager.selected
        val url = tab.url
        if (url == null || !UrlResolver.isWebUrl(url)) {
            Toast.makeText(this, R.string.site_open_first, Toast.LENGTH_SHORT).show()
            return null
        }
        return url to tab.title?.takeIf { it.isNotBlank() }
    }

    private fun addPageToShortcuts() {
        val (url, title) = currentWebPage() ?: return
        app.shortcuts.add(title ?: HostUtil.hostOf(url).orEmpty(), url) {
            Toast.makeText(this, R.string.shortcut_added, Toast.LENGTH_SHORT).show()
            app.notifyHomeChanged()
        }
    }

    private fun addPageToGroup() {
        val (url, title) = currentWebPage() ?: return
        app.siteGroups.all { groups ->
            if (groups.isEmpty()) {
                Toast.makeText(this, R.string.group_none, Toast.LENGTH_SHORT).show()
                return@all
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.menu_add_to_group)
                .setItems(groups.map { it.name }.toTypedArray()) { _, which ->
                    app.siteGroups.addItem(groups[which].id, url, title) {
                        Toast.makeText(this, R.string.group_added, Toast.LENGTH_SHORT).show()
                        app.notifyHomeChanged()
                    }
                }
                .show()
        }
    }

    // تبديل مساحة العمل: مدير تبويبات جديد، فنعيد ربطه وعرضه ونحدّث الصفحة الرئيسية
    override fun onWorkspaceChanged() {
        tabManager.listener = this
        mountSelected()
        home.refresh()
    }

    private fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** يعرض عرض التبويب المحدد داخل الحاوية ويحدّث الشريط العلوي. */
    private fun mountSelected() {
        binding.engineContainer.removeAllViews()
        val tab = tabManager.selected
        val engine = tabManager.activate(tab)
        engine.permissionHandler = this // حوارات الأذونات تظهر من هذه الواجهة
        engine.attachTo(binding.engineContainer)
        binding.addressInput.clearFocus()
        starUrl = null
        refreshChrome(forceAddress = true)
        updateTabCount()
    }

    /** يزامن الشريط العلوي والصفحة الرئيسية مع حالة التبويب المحدد. */
    private fun refreshChrome(forceAddress: Boolean) {
        val tab = tabManager.selected
        val url = tab.url
        val showHome = url == null
        // تُحدَّث الصفحة الرئيسية عند ظهورها (قد تكون البيانات تغيرت أثناء التصفح)
        if (showHome && binding.homeContainer.visibility != View.VISIBLE) home.refresh()
        binding.homeContainer.visibility = if (showHome) View.VISIBLE else View.GONE
        setReloadEnabled(url != null)
        backCallback.isEnabled = tab.canGoBack || tab.isFullScreen
        applyPrivateMode(tab.isPrivate)
        binding.progressBar.visibility = if (tab.isLoading) View.VISIBLE else View.INVISIBLE
        binding.progressBar.setProgressCompat(tab.progress, true)
        applyFullScreen(tab.isFullScreen)
        // لا نكتب فوق نص يحرره المستخدم الآن
        if (forceAddress || !binding.addressInput.hasFocus()) binding.addressInput.setText(url.orEmpty())
        refreshStar(url)
    }

    private fun refreshStar(url: String?) {
        if (url == null || !UrlResolver.isWebUrl(url)) {
            starUrl = null
            setStar(false)
            return
        }
        if (url == starUrl) return
        starUrl = url
        app.bookmarks.isBookmarked(url) { marked ->
            if (url == tabManager.selected.url) setStar(marked)
        }
    }

    private fun setStar(bookmarked: Boolean) {
        binding.starButton.setImageResource(if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_outline)
    }

    private fun updateTabCount() {
        binding.tabsButton.text = tabManager.tabs.size.toString()
    }

    private fun setReloadEnabled(enabled: Boolean) {
        binding.reloadButton.isEnabled = enabled
        binding.reloadButton.alpha = if (enabled) 1f else 0.4f
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.addressInput.windowToken, 0)
        binding.addressInput.clearFocus()
    }

    // ---- TabManager.Listener ----

    override fun onTabsChanged() = updateTabCount()

    override fun onSelectedTabChanged(old: Tab?, new: Tab) = mountSelected()

    override fun onTabUpdated(tab: Tab) {
        if (tab === tabManager.selected) refreshChrome(forceAddress = false)
    }

    // ---- ProtectionManager.Listener: تنبيهات الحجب مع إمكانية السماح المؤقت ----

    override fun onPopupBlocked(url: String) {
        val bar = Snackbar.make(binding.root, R.string.snack_popup_blocked, Snackbar.LENGTH_LONG)
        if (UrlResolver.isWebUrl(url)) bar.setAction(R.string.snack_open) { tabManager.newTab(url) }
        bar.show()
    }

    override fun onRedirectBlocked(url: String) {
        val host = HostUtil.hostOf(url) ?: url
        Snackbar.make(binding.root, getString(R.string.snack_redirect_blocked, host), Snackbar.LENGTH_LONG)
            .setAction(R.string.snack_allow_once) {
                app.protection.tempAllow(url) // سماح لمدة دقيقة لهذا الرابط فقط
                tabManager.loadInSelected(url)
            }.show()
    }

    // ---- DownloadCenter.UiListener: التنزيلات ----

    override fun onRiskyDownload(fileName: String, risk: FileSafety.Risk, proceed: () -> Unit) {
        val message = if (risk == FileSafety.Risk.DISGUISED) R.string.dl_risky_disguised else R.string.dl_risky_executable
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dl_risky_title)
            .setMessage(getString(message, fileName))
            .setPositiveButton(R.string.dl_download_anyway) { _, _ -> proceed() }
            .setNegativeButton(R.string.action_cancel, null) // الافتراضي: لا تنزيل
            .show()
    }

    override fun onDownloadStarted(fileName: String) {
        askNotificationPermissionOnce()
        Snackbar.make(binding.root, getString(R.string.dl_started, fileName), Snackbar.LENGTH_LONG)
            .setAction(R.string.dl_view) { DownloadsBottomSheet().show(supportFragmentManager, "downloads") }
            .show()
    }

    override fun onDownloadFinished(item: DownloadItem) {
        Snackbar.make(binding.root, getString(R.string.dl_finished, item.fileName), Snackbar.LENGTH_LONG)
            .setAction(R.string.dl_view) { DownloadsBottomSheet().show(supportFragmentManager, "downloads") }
            .show()
    }

    override fun onUnsupported() {
        Toast.makeText(this, R.string.dl_unsupported, Toast.LENGTH_SHORT).show()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("vayro_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- PermissionHandler: أذونات الكاميرا والميكروفون والموقع ----

    override fun requestSitePermission(host: String, kinds: Set<SitePermission>, result: (Boolean) -> Unit) {
        val names = kinds.joinToString(getString(R.string.perm_and)) {
            getString(
                when (it) {
                    SitePermission.CAMERA -> R.string.perm_camera
                    SitePermission.MICROPHONE -> R.string.perm_microphone
                    SitePermission.LOCATION -> R.string.perm_location
                }
            )
        }
        var answered = false // نضمن الرد مرة واحدة فقط
        fun answer(value: Boolean) {
            if (!answered) {
                answered = true
                result(value)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.perm_title, host, names))
            .setPositiveButton(R.string.perm_allow) { _, _ -> answer(true) }
            .setNegativeButton(R.string.perm_block) { _, _ -> answer(false) }
            .setOnCancelListener { answer(false) }
            .show()
    }

    override fun requestAndroidPermissions(permissions: List<String>, result: (Boolean) -> Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            result(true)
            return
        }
        pendingAndroidResult = result
        permissionLauncher.launch(missing.toTypedArray())
    }

    // فيديو بملء الشاشة + زر الرئيسية = نافذة عائمة (صورة داخل صورة) بدل إيقاف الفيديو
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (tabManager.selected.isFullScreen) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            } catch (_: Exception) {
                // الجهاز لا يدعم الصورة داخل الصورة: يبقى السلوك الافتراضي
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.dataUsage.sample() // خط أساس/تسجيل استهلاك البيانات
    }

    override fun onStop() {
        app.dataUsage.sample() // تسجيل الاستهلاك عند مغادرة التطبيق
        tabManager.persist() // حفظ الجلسة عند مغادرة التطبيق
        super.onStop()
    }

    override fun onDestroy() {
        tabManager.listener = null
        app.protection.listener = null
        app.downloads.uiListener = null
        app.homeListener = null
        app.workspaces.listener = null
        // لا نترك مراجع لهذه الواجهة داخل المحركات بعد تدميرها
        tabManager.tabs.forEach { it.engineOrNull?.permissionHandler = null }
        // نفصل العرض فقط؛ الجلسات تبقى حية في TabManager
        tabManager.selected.engineOrNull?.detach()
        super.onDestroy()
    }
}

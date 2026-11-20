package com.vayro.browser.engine

import android.annotation.SuppressLint
import android.os.SystemClock
import android.webkit.URLUtil
import android.view.MotionEvent
import android.view.ViewGroup
import com.vayro.browser.core.BlockReason
import com.vayro.browser.core.HostUtil
import com.vayro.browser.diagnostics.CrashLog
import com.vayro.browser.downloads.DownloadCenter
import com.vayro.browser.downloads.DownloadRequest
import com.vayro.browser.protection.ProtectionManager
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.TranslationsController
import org.mozilla.geckoview.WebResponse

/** تنفيذ BrowserEngine باستخدام GeckoView؛ كل كائن = جلسة = تبويب واحد. */
class GeckoBrowserEngine(
    private val runtime: GeckoRuntime,
    private val protection: ProtectionManager,
    private val downloads: DownloadCenter,
    private val isPrivate: Boolean, // جديد (المرحلة 10): جلسة خاصة بكوكيز معزولة
    private val blockAutoplay: () -> Boolean // وضع توفير البيانات يمنع التشغيل التلقائي
) : BrowserEngine {

    override var listener: BrowserEngine.Listener? = null
    override var permissionHandler: PermissionHandler? = null

    private val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(isPrivate).build())
    private var geckoView: GeckoView? = null

    // رابط الصفحة الحالي (لقرارات الحماية) وآخر لمسة للمستخدم (لتمييز النوافذ/التحويلات التلقائية)
    private var currentUrl: String? = null

    // أذونات الصفحة الحالية: منها إذن حماية التتبع الذي يُستخدم كاستثناء للموقع
    private var pagePermissions: List<GeckoSession.PermissionDelegate.ContentPermission> = emptyList()
    private var lastTouchAt = 0L
    // لا نسجل الحجب في سجل التقارير أثناء التصفح الخاص
    private fun reportBlock(reason: BlockReason, blockedUrl: String, pageUrl: String?) {
        if (!isPrivate) protection.report(reason, blockedUrl, pageUrl)
    }

    private fun recentTouch() = SystemClock.uptimeMillis() - lastTouchAt <= TOUCH_WINDOW_MS

    init {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                listener?.onLoadingChanged(true)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                listener?.onLoadingChanged(false)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                listener?.onProgressChanged(progress)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                pagePermissions = perms
                url?.let {
                    currentUrl = it
                    listener?.onUrlChanged(it)
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                listener?.onCanGoBackChanged(canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                listener?.onCanGoForwardChanged(canGoForward)
            }

            // منع التحويلات التلقائية المريبة (أقصى حماية فقط)
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                if (request.target != GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT) return null
                val url = request.uri
                if (protection.isTempAllowed(url)) return null // سماح مؤقت من المستخدم
                val blocked = protection.shouldBlockRedirect(
                    currentUrl, url, request.isDirectNavigation, request.isRedirect, recentTouch()
                )
                if (!blocked) return null
                reportBlock(BlockReason.REDIRECT, url, currentUrl)
                protection.listener?.onRedirectBlocked(url)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            // نافذة جديدة (window.open / target=_blank): نفتحها كتبويب أو نحجبها
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (protection.popupAllowed(currentUrl, recentTouch())) {
                    listener?.onOpenInNewTab(uri)
                } else {
                    reportBlock(BlockReason.POPUP, uri, currentUrl)
                    protection.listener?.onPopupBlocked(uri)
                }
                return null // نمنع نافذة Gecko؛ التبويب يُفتح عبر TabManager (بدون window.opener)
            }
        }

        // نوافذ منبثقة حجبها Gecko لأنها لم تتبع لمسة: نحجبها ونسجلها
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onPopupPrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.PopupPrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                if (protection.popupExplicitlyAllowed(currentUrl)) {
                    return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
                }
                val target = prompt.targetUri ?: ""
                reportBlock(BlockReason.POPUP, target, currentUrl)
                protection.listener?.onPopupBlocked(target)
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
            }
        }

        // حجب الإعلانات والمتتبعات عبر حماية التتبع في Gecko، وتسجيل كل حجب فعلي
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                if (!event.isBlocking) return
                val category = event.antiTrackingCategory
                val reason = when {
                    category and ContentBlocking.AntiTracking.AD != 0 -> BlockReason.AD
                    category != 0 -> BlockReason.TRACKER
                    else -> return
                }
                reportBlock(reason, event.uri, currentUrl)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                listener?.onTitleChanged(title.orEmpty())
            }

            // جديد (المرحلة 13): عملية المحتوى انهارت أو قُتلت لقلة الذاكرة؛ الجلسة لم تعد صالحة
            override fun onCrash(session: GeckoSession) {
                CrashLog.note("Content process crashed")
                listener?.onCrashed()
            }

            override fun onKill(session: GeckoSession) {
                CrashLog.note("Content process killed by the system")
                listener?.onCrashed()
            }

            // جديد (المرحلة 11): الصفحة تطلب ملء الشاشة (زر ملء الشاشة في مشغل الفيديو)
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                listener?.onFullScreenChanged(fullScreen)
            }

            // جديد (المرحلة 5): ملف لا يستطيع المحرك عرضه = تنزيل؛ نمرره لمركز التنزيلات
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val headers = response.headers
                fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
                val mime = header("content-type")?.substringBefore(';')?.trim().orEmpty()
                val name = URLUtil.guessFileName(response.uri, header("content-disposition"), mime.ifBlank { null })
                try { response.body?.close() } catch (_: Exception) {} // لن نستهلك هذا التدفق
                downloads.request(
                    DownloadRequest(response.uri, name, mime, header("content-length")?.toLongOrNull() ?: -1L, currentUrl)
                )
            }
        }

        // الأذونات: الكاميرا والميكروفون والموقع تمر عبر حوار صريح؛ الإشعارات تُرفض (غير مدعومة بعد)
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val handler = permissionHandler
                if (handler == null || permissions == null) {
                    callback.reject()
                    return
                }
                handler.requestAndroidPermissions(permissions.toList()) { ok ->
                    if (ok) callback.grant() else callback.reject()
                }
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                val result = GeckoResult<Int>()
                when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> {
                        val handler = permissionHandler
                        if (handler == null) {
                            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                        } else {
                            val host = HostUtil.hostOf(perm.uri) ?: perm.uri
                            handler.requestSitePermission(host, setOf(SitePermission.LOCATION)) { allowed ->
                                result.complete(
                                    if (allowed) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                                    else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                                )
                            }
                        }
                    }
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION ->
                        result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    // التشغيل التلقائي: يُمنع (مسموع وصامت) عند تفعيل توفير البيانات
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                        result.complete(
                            if (blockAutoplay()) GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            else GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        )
                    else -> return null // بقية الأنواع على سلوك Gecko الافتراضي
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val handler = permissionHandler
                val kinds = mutableSetOf<SitePermission>()
                if (!video.isNullOrEmpty()) kinds.add(SitePermission.CAMERA)
                if (!audio.isNullOrEmpty()) kinds.add(SitePermission.MICROPHONE)
                if (handler == null || kinds.isEmpty()) {
                    callback.reject()
                    return
                }
                handler.requestSitePermission(HostUtil.hostOf(uri) ?: uri, kinds) { allowed ->
                    if (allowed) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
                }
            }
        }

        // جديد (المرحلة 14): الترجمة المحلية. نص الصفحة يُترجَم داخل الجهاز ولا يُرسل لأي خادم
        session.setTranslationsSessionDelegate(object : TranslationsController.SessionTranslation.Delegate {
            override fun onTranslationStateChange(
                session: GeckoSession,
                translationState: TranslationsController.SessionTranslation.TranslationState?
            ) {
                val detected = translationState?.detectedLanguages
                val translated = translationState != null &&
                    translationState.requestedTranslationPair != null &&
                    translationState.error == null &&
                    translationState.hasVisibleChange
                listener?.onTranslationState(
                    PageTranslationState(
                        detectedLanguage = detected?.docLangTag,
                        canTranslate = detected?.isDocLangTagSupported == true,
                        isTranslated = translated,
                        error = translationState?.error
                    )
                )
            }
        })

        session.open(runtime)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun attachTo(container: ViewGroup) {
        detach()
        val view = GeckoView(container.context)
        // نسجل وقت آخر لمسة دون استهلاك الحدث (نعيد false)
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) lastTouchAt = SystemClock.uptimeMillis()
            false
        }
        view.setSession(session)
        container.addView(
            view,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        geckoView = view
        session.setActive(true)
    }

    override fun detach() {
        val view = geckoView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        view.releaseSession()
        geckoView = null
        session.setActive(false)
    }

    override fun loadUrl(url: String) = session.loadUri(url)

    override fun reload() = session.reload()

    override fun goBack() = session.goBack()

    override fun goForward() = session.goForward()

    override fun exitFullScreen() = session.exitFullScreen()

    override fun checkTranslationSize(from: String, to: String, done: (Long?) -> Unit) {
        TranslationsController.RuntimeTranslation.checkPairDownloadSize(from, to)
            .accept({ size -> done(size) }, { done(null) })
    }

    override fun translatePage(from: String, to: String, downloadModel: Boolean, onDone: (String?) -> Unit) {
        val translation = session.sessionTranslation
        if (translation == null) {
            onDone("translation engine unavailable")
            return
        }
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
            .downloadModel(downloadModel)
            .build()
        translation.translate(from, to, options)
            .accept({ onDone(null) }, { error -> onDone(error?.message ?: "translation failed") })
    }

    override fun restoreOriginalPage() {
        session.sessionTranslation?.restoreOriginalPage()
    }

    // الاستثناء يُحفظ الآن كإذن PERMISSION_TRACKING (ALLOW = السماح للمتتبعات = حماية مطفأة لهذا الموقع)
    // لأن ContentBlockingController.addException/removeException أُزيلت من GeckoView
    override fun setProtectionException(enabled: Boolean) {
        val permission = pagePermissions.firstOrNull {
            it.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING
        } ?: return // لا صفحة محمّلة بعد
        runtime.storageController.setPermission(
            permission,
            if (enabled) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        )
    }

    override fun release() {
        geckoView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.releaseSession()
        }
        geckoView = null
        session.close()
    }

    private companion object {
        const val TOUCH_WINDOW_MS = 1500L
    }
}

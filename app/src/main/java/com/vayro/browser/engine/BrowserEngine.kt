package com.vayro.browser.engine

import android.view.ViewGroup

/** حالة ترجمة الصفحة كما يبلّغها محرك الترجمة المحلي (داخل الجهاز). */
data class PageTranslationState(
    val detectedLanguage: String?, // لغة الصفحة المكتشفة (BCP 47)
    val canTranslate: Boolean,     // هل لغة الصفحة مدعومة للترجمة؟
    val isTranslated: Boolean,     // هل الصفحة مترجمة الآن؟
    val error: String?
)

/** أنواع أذونات المواقع التي تعالجها الواجهة. */
enum class SitePermission { CAMERA, MICROPHONE, LOCATION }

/** تنفذه الواجهة لعرض طلبات الأذونات للمستخدم (المحرك لا يعرف الحوارات). */
interface PermissionHandler {
    fun requestSitePermission(host: String, kinds: Set<SitePermission>, result: (Boolean) -> Unit)
    fun requestAndroidPermissions(permissions: List<String>, result: (Boolean) -> Unit)
}

/**
 * واجهة مجردة لمحرك تبويب واحد (جلسة واحدة). TabManager يملك محركاً لكل تبويب،
 * والواجهة لا تعرف GeckoView.
 */
interface BrowserEngine {

    interface Listener {
        fun onUrlChanged(url: String)
        fun onTitleChanged(title: String)
        fun onLoadingChanged(isLoading: Boolean)
        fun onProgressChanged(progress: Int)
        fun onCanGoBackChanged(canGoBack: Boolean)
        fun onCanGoForwardChanged(canGoForward: Boolean) // جديد (المرحلة 9)
        fun onTranslationState(state: PageTranslationState) // جديد (المرحلة 14): حالة الترجمة
        fun onCrashed() // جديد (المرحلة 13): انهارت عملية المحتوى أو قتلها النظام
        fun onFullScreenChanged(fullScreen: Boolean) // جديد (المرحلة 11): فيديو/محتوى بملء الشاشة
        fun onOpenInNewTab(url: String) // جديد (المرحلة 3): نافذة مسموحة تُفتح كتبويب
    }

    var listener: Listener?
    var permissionHandler: PermissionHandler? // جديد (المرحلة 4)

    fun attachTo(container: ViewGroup)
    fun detach()
    fun loadUrl(url: String)
    fun reload()
    fun goBack()
    fun goForward()
    fun exitFullScreen()

    /** حجم نموذج اللغة المطلوب بالبايت (0 = منزَّل بالفعل، null = الزوج غير مدعوم أو تعذر الفحص). */
    fun checkTranslationSize(from: String, to: String, done: (Long?) -> Unit)

    /** يترجم الصفحة الحالية محلياً؛ onDone(null) عند البدء بنجاح أو رسالة الخطأ. */
    fun translatePage(from: String, to: String, downloadModel: Boolean, onDone: (String?) -> Unit)

    fun restoreOriginalPage()

    /** جديد: تعطيل/تفعيل حماية التتبع لموقع هذه الجلسة الحالي (استثناء الموقع). */
    fun setProtectionException(enabled: Boolean)

    fun release()
}

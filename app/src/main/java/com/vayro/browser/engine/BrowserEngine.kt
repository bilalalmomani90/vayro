package com.vayro.browser.engine

import android.view.ViewGroup

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

    /** جديد: تعطيل/تفعيل حماية التتبع لموقع هذه الجلسة الحالي (استثناء الموقع). */
    fun setProtectionException(enabled: Boolean)

    fun release()
}

package com.vayro.browser.engine

import android.util.Log
import com.vayro.browser.datasaver.DataSaverManager
import com.vayro.browser.datasaver.DataSaverMode
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * يطبّق توفير البيانات: منع التشغيل التلقائي للوسائط (إعداد المحرك)،
 * وفي الوضع الصارم تفعيل إضافة مدمجة تلغي طلبات الصور وتسأل التطبيق عن الإعداد والاستثناءات.
 */
class GeckoDataSaverApplier(
    private val runtime: GeckoRuntime,
    private val saver: DataSaverManager
) : DataSaverManager.Applier {

    private var extension: WebExtension? = null
    private var wantExtension = false

    private val messageDelegate = object : WebExtension.MessageDelegate {
        // رد فوري بالإعداد الحالي على رسائل الإضافة
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? = GeckoResult.fromValue(saver.configJson() as Any)
    }

    init {
        runtime.webExtensionController.ensureBuiltIn(SAVER_URI, SAVER_ID).accept(
            { ext ->
                ext?.setMessageDelegate(messageDelegate, NATIVE_APP)
                extension = ext
                syncExtension()
            },
            { error -> Log.w("VayroDataSaver", "saver extension install failed", error) }
        )
    }

    override fun apply(mode: DataSaverMode) {
        // منع التشغيل التلقائي صار يُطبَّق في GeckoBrowserEngine عبر PermissionDelegate
        // (GeckoView أزال GeckoRuntimeSettings.setAutoplayDefault)
        wantExtension = mode == DataSaverMode.STRICT
        syncExtension()
    }

    private fun syncExtension() {
        val ext = extension ?: return
        val controller = runtime.webExtensionController
        if (wantExtension) controller.enable(ext, WebExtensionController.EnableSource.APP)
        else controller.disable(ext, WebExtensionController.EnableSource.APP)
    }

    private companion object {
        const val SAVER_URI = "resource://android/assets/saver/"
        const val SAVER_ID = "saver@vayro.browser"
        const val NATIVE_APP = "browser"
    }
}

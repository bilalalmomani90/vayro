package com.vayro.browser.engine

import android.util.Log
import com.vayro.browser.core.CookiePolicy
import com.vayro.browser.core.ProtectionLevel
import com.vayro.browser.protection.ProtectionManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/** يطبّق مستوى الحماية وسياسة الكوكيز على GeckoRuntime، ويفعّل/يعطّل إضافة التنظيف المدمجة. */
class GeckoProtectionApplier(private val runtime: GeckoRuntime) : ProtectionManager.Applier {

    private var cleaner: WebExtension? = null
    private var wantCleaner = false

    init {
        // تثبيت إضافة التنظيف المدمجة (تُفعَّل في المتقدم والأقصى فقط)
        runtime.webExtensionController.ensureBuiltIn(CLEANER_URI, CLEANER_ID).accept(
            { ext ->
                cleaner = ext
                syncCleaner()
            },
            { error -> Log.w("VayroProtection", "cleaner install failed", error) }
        )
    }

    override fun apply(level: ProtectionLevel, cookies: CookiePolicy) {
        val cb = runtime.settings.contentBlocking
        when (level) {
            ProtectionLevel.NORMAL -> {
                cb.setAntiTracking(ContentBlocking.AntiTracking.DEFAULT)
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                cb.setStrictSocialTrackingProtection(false)
            }
            ProtectionLevel.ADVANCED -> {
                cb.setAntiTracking(
                    ContentBlocking.AntiTracking.AD or ContentBlocking.AntiTracking.ANALYTIC or
                        ContentBlocking.AntiTracking.SOCIAL or ContentBlocking.AntiTracking.CRYPTOMINING or
                        ContentBlocking.AntiTracking.FINGERPRINTING
                )
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
                cb.setStrictSocialTrackingProtection(false)
            }
            ProtectionLevel.MAX -> {
                cb.setAntiTracking(ContentBlocking.AntiTracking.STRICT)
                cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                cb.setStrictSocialTrackingProtection(true)
            }
        }
        cb.setCookieBehavior(
            when (cookies) {
                CookiePolicy.ALLOW_ALL -> ContentBlocking.CookieBehavior.ACCEPT_ALL
                CookiePolicy.BLOCK_TRACKERS -> ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                CookiePolicy.BLOCK_THIRD_PARTY -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
            }
        )
        wantCleaner = level != ProtectionLevel.NORMAL
        syncCleaner()
    }

    private fun syncCleaner() {
        val ext = cleaner ?: return
        val controller = runtime.webExtensionController
        if (wantCleaner) controller.enable(ext, WebExtensionController.EnableSource.APP)
        else controller.disable(ext, WebExtensionController.EnableSource.APP)
    }

    private companion object {
        const val CLEANER_URI = "resource://android/assets/cosmetic/"
        const val CLEANER_ID = "cleaner@vayro.browser"
    }
}

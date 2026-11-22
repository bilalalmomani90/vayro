package com.vayro.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionLogicTest {

    @Test fun hostOfStripsWwwAndLowercases() {
        assertEquals("example.com", HostUtil.hostOf("https://WWW.Example.com/a?b=1"))
        assertNull(HostUtil.hostOf("not a url"))
        assertNull(HostUtil.hostOf(null))
    }

    @Test fun sameSiteHandlesSubdomains() {
        assertTrue(HostUtil.sameSite("example.com", "login.example.com"))
        assertTrue(HostUtil.sameSite("a.example.com", "b.example.com"))
        assertFalse(HostUtil.sameSite("example.com", "evil.org"))
        assertFalse(HostUtil.sameSite(null, "example.com"))
    }

    @Test fun popupsFollowLevelRules() {
        // عادي: مسموح (المحرك سمح بها بعد لمسة)
        assertTrue(ProtectionLogic.popupAllowed(ProtectionLevel.NORMAL, true, false, false))
        // متقدم وأقصى: لا بد من لمسة حديثة
        assertFalse(ProtectionLogic.popupAllowed(ProtectionLevel.ADVANCED, true, false, false))
        assertTrue(ProtectionLogic.popupAllowed(ProtectionLevel.ADVANCED, true, false, true))
        assertFalse(ProtectionLogic.popupAllowed(ProtectionLevel.MAX, true, false, false))
    }

    @Test fun siteExceptionsOverridePopupRules() {
        assertTrue(ProtectionLogic.popupAllowed(ProtectionLevel.MAX, siteProtected = false, sitePopupsAllowed = false, recentUserTouch = false))
        assertTrue(ProtectionLogic.popupAllowed(ProtectionLevel.MAX, siteProtected = true, sitePopupsAllowed = true, recentUserTouch = false))
    }

    @Test fun redirectsAreBlockedOnlyInMaxAndOnlyWhenAutomatic() {
        // التلقائي عبر الموقع بلا لمسة: يُحجب في أقصى حماية فقط
        assertTrue(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, true, false, false, true, false))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.ADVANCED, true, false, false, true, false))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.NORMAL, true, false, false, true, false))
        // لا يُحجب: إدخال مباشر، تحويل خادم، نفس الموقع، لمسة حديثة، أو حماية مطفأة للموقع
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, true, true, false, true, false))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, true, false, true, true, false))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, true, false, false, false, false))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, true, false, false, true, true))
        assertFalse(ProtectionLogic.redirectBlocked(ProtectionLevel.MAX, false, false, false, true, false))
    }

    @Test fun trustCardUsesLocalSignalsOnly() {
        // أخضر: لا إشارات
        assertEquals(TrustLevel.GREEN, SiteTrust.assess("https://a.com", false, true, 0, 0).level)
        // أحمر: في قائمتك المحظورة (يغلب كل شيء)
        val red = SiteTrust.assess("https://a.com", true, true, 0, 0)
        assertEquals(TrustLevel.RED, red.level)
        assertEquals(listOf(TrustReason.USER_BLOCKED), red.reasons)
        // أصفر: HTTP وحده، أو تحويلات/نوافذ كثيرة، أو حماية مطفأة
        assertEquals(TrustLevel.YELLOW, SiteTrust.assess("http://a.com", false, true, 0, 0).level)
        assertEquals(TrustLevel.YELLOW, SiteTrust.assess("https://a.com", false, true, SiteTrust.REDIRECTS_WARN, 0).level)
        assertEquals(TrustLevel.YELLOW, SiteTrust.assess("https://a.com", false, true, 0, SiteTrust.POPUPS_WARN).level)
        assertEquals(TrustLevel.YELLOW, SiteTrust.assess("https://a.com", false, false, 0, 0).level)
        // HTTPS وحده لا يجعل الموقع أخضر إن وُجدت إشارات أخرى
        assertEquals(TrustLevel.YELLOW, SiteTrust.assess("https://a.com", false, true, 9, 0).level)
        // أقل من العتبة: يبقى أخضر
        assertEquals(TrustLevel.GREEN, SiteTrust.assess("https://a.com", false, true, SiteTrust.REDIRECTS_WARN - 1, SiteTrust.POPUPS_WARN - 1).level)
    }

    @Test fun strictSiteBehavesLikeMaxAndBlocksUnrequestedDownloads() {
        assertEquals(ProtectionLevel.MAX, ProtectionLogic.effectiveLevel(ProtectionLevel.NORMAL, true))
        assertEquals(ProtectionLevel.NORMAL, ProtectionLogic.effectiveLevel(ProtectionLevel.NORMAL, false))
        // العادي: التنزيل مسموح؛ المتقدم والأقصى: يُحجب إن لم يسبقه لمس
        assertFalse(ProtectionLogic.autoDownloadBlocked(ProtectionLevel.NORMAL, true, false))
        assertTrue(ProtectionLogic.autoDownloadBlocked(ProtectionLevel.ADVANCED, true, false))
        assertTrue(ProtectionLogic.autoDownloadBlocked(ProtectionLevel.MAX, true, false))
        // بعد لمس، أو إن كانت الحماية مطفأة للموقع: مسموح
        assertFalse(ProtectionLogic.autoDownloadBlocked(ProtectionLevel.MAX, true, true))
        assertFalse(ProtectionLogic.autoDownloadBlocked(ProtectionLevel.MAX, false, false))
    }

    @Test fun normalizeInputCleansUserText() {
        assertEquals("example.com", HostUtil.normalizeInput("https://www.Example.com/a?b=1"))
        assertEquals("example.com", HostUtil.normalizeInput("  example.com "))
        assertNull(HostUtil.normalizeInput("localhost"))
        assertNull(HostUtil.normalizeInput("bad host.com"))
        assertNull(HostUtil.normalizeInput(""))
    }

    @Test fun blocklistMatchesSubdomainsButNotLookalikes() {
        val blocked = setOf("example.com")
        assertTrue(HostUtil.matchesBlocklist("example.com", blocked))
        assertTrue(HostUtil.matchesBlocklist("sub.example.com", blocked))
        assertFalse(HostUtil.matchesBlocklist("notexample.com", blocked))
        assertFalse(HostUtil.matchesBlocklist(null, blocked))
        assertFalse(HostUtil.matchesBlocklist("example.org", blocked))
    }

    @Test fun enumKeysRoundTripWithSafeDefaults() {
        assertEquals(ProtectionLevel.MAX, ProtectionLevel.fromKey("max"))
        assertEquals(ProtectionLevel.NORMAL, ProtectionLevel.fromKey("garbage"))
        assertEquals(CookiePolicy.BLOCK_TRACKERS, CookiePolicy.fromKey(null))
        assertEquals(BlockReason.POPUP, BlockReason.fromKey("popup"))
        assertEquals(BlockReason.BLOCKED, BlockReason.fromKey("blocked"))
        assertEquals(BlockReason.DOWNLOAD, BlockReason.fromKey("download"))
        assertNull(BlockReason.fromKey("x"))
    }
}

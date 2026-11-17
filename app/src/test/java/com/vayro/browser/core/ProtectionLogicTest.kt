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

    @Test fun enumKeysRoundTripWithSafeDefaults() {
        assertEquals(ProtectionLevel.MAX, ProtectionLevel.fromKey("max"))
        assertEquals(ProtectionLevel.NORMAL, ProtectionLevel.fromKey("garbage"))
        assertEquals(CookiePolicy.BLOCK_TRACKERS, CookiePolicy.fromKey(null))
        assertEquals(BlockReason.POPUP, BlockReason.fromKey("popup"))
        assertNull(BlockReason.fromKey("x"))
    }
}

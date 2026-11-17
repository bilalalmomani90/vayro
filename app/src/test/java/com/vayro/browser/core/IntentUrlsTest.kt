package com.vayro.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentUrlsTest {

    @Test fun extractsFirstUrlFromSharedText() {
        assertEquals("https://example.com/a?b=1", IntentUrls.extractWebUrl("Look at this https://example.com/a?b=1 now"))
    }

    @Test fun stripsTrailingPunctuation() {
        assertEquals("https://example.com/x", IntentUrls.extractWebUrl("see (https://example.com/x)."))
    }

    @Test fun ignoresNonWebAndEmpty() {
        assertNull(IntentUrls.extractWebUrl("no link here"))
        assertNull(IntentUrls.extractWebUrl("javascript:alert(1)"))
        assertNull(IntentUrls.extractWebUrl(null))
        assertNull(IntentUrls.extractWebUrl("https://"))
    }

    @Test fun sqlLikeEscapesSpecials() {
        assertEquals("100\\%\\_done\\\\", SqlLike.escape("100%_done\\"))
    }
}

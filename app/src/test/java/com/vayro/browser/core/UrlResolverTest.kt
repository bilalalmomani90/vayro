package com.vayro.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {

    @Test fun emptyInputReturnsNull() {
        assertNull(UrlResolver.resolve("   "))
    }

    @Test fun fullUrlIsKept() {
        assertEquals("https://example.com/a?b=1", UrlResolver.resolve("https://example.com/a?b=1"))
        assertEquals("http://example.com", UrlResolver.resolve("http://example.com"))
    }

    @Test fun bareDomainGetsHttps() {
        assertEquals("https://example.com", UrlResolver.resolve("example.com"))
        assertEquals("https://sub.example.co.uk/path", UrlResolver.resolve("sub.example.co.uk/path"))
    }

    @Test fun localAddressesUseHttp() {
        assertEquals("http://localhost:8080", UrlResolver.resolve("localhost:8080"))
        assertEquals("http://192.168.1.1", UrlResolver.resolve("192.168.1.1"))
    }

    @Test fun phraseBecomesSearch() {
        assertEquals("https://duckduckgo.com/?q=hello+world", UrlResolver.resolve("hello world"))
    }

    @Test fun arabicPhraseIsEncodedSearch() {
        val result = UrlResolver.resolve("مرحبا بالعالم")!!
        assertTrue(result.startsWith("https://duckduckgo.com/?q=%"))
    }

    @Test fun dangerousSchemesBecomeSearch() {
        val js = UrlResolver.resolve("javascript:alert(1)")!!
        assertTrue(js.startsWith("https://duckduckgo.com/?q="))
        val data = UrlResolver.resolve("data:text/html,hi")!!
        assertTrue(data.startsWith("https://duckduckgo.com/?q="))
    }

    @Test fun isWebUrlOnlyAcceptsHttpAndHttps() {
        assertTrue(UrlResolver.isWebUrl("https://a.com"))
        assertTrue(UrlResolver.isWebUrl("HTTP://a.com"))
        assertTrue(!UrlResolver.isWebUrl("about:blank"))
        assertTrue(!UrlResolver.isWebUrl("javascript:alert(1)"))
    }
}

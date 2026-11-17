package com.vayro.browser.home

import com.vayro.browser.core.UrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLayoutTest {

    @Test fun parseOrderRepairsBadData() {
        val all = HomeSection.values().toList()
        assertEquals(all, HomeLayout.parseOrder(null))
        // مجهول + مكرر + ناقص: يُصلح دون أن يضيع أي قسم
        val parsed = HomeLayout.parseOrder("recent,bogus,recent,shortcuts")
        assertEquals(listOf(HomeSection.RECENT, HomeSection.SHORTCUTS), parsed.take(2))
        assertEquals(all.size, parsed.size)
        assertEquals(all.toSet(), parsed.toSet())
    }

    @Test fun orderRoundTrips() {
        val order = listOf(HomeSection.BOOKMARKS, HomeSection.SHORTCUTS, HomeSection.RECENT, HomeSection.GROUPS)
        assertEquals(order, HomeLayout.parseOrder(HomeLayout.serializeOrder(order)))
    }

    @Test fun moveClampsAtEdges() {
        val order = HomeSection.values().toList()
        assertEquals(order, HomeLayout.move(order, order.first(), -1))
        assertEquals(order, HomeLayout.move(order, order.last(), +1))
        val moved = HomeLayout.move(order, HomeSection.GROUPS, -1)
        assertEquals(HomeSection.GROUPS, moved[0])
        assertEquals(order.size, moved.size)
    }

    @Test fun hiddenSectionsAreNotVisible() {
        val settings = HomeSettings(hidden = setOf(HomeSection.RECENT, HomeSection.GROUPS))
        assertEquals(listOf(HomeSection.SHORTCUTS, HomeSection.BOOKMARKS), HomeLayout.visible(settings))
    }

    @Test fun groupPlanKeepsOnlyUniqueWebUrlsAndCaps() {
        val urls = listOf("https://a.com", "https://a.com", "about:blank", "javascript:x", "http://b.org")
        assertEquals(listOf("https://a.com", "http://b.org"), GroupPlan.urlsToOpen(urls))
        val many = (1..50).map { "https://site$it.com" }
        assertEquals(GroupPlan.MAX_TABS, GroupPlan.urlsToOpen(many).size)
    }

    @Test fun siteUrlRejectsSearchPhrasesAndAcceptsDomains() {
        assertEquals("https://example.com", UrlResolver.asSiteUrl("example.com"))
        assertEquals("http://example.com/x", UrlResolver.asSiteUrl("http://example.com/x"))
        assertNull(UrlResolver.asSiteUrl("hello world"))
        assertNull(UrlResolver.asSiteUrl("justoneword"))
        assertNull(UrlResolver.asSiteUrl("  "))
    }

    @Test fun cardSizesMapToColumns() {
        assertEquals(4, CardSize.COMPACT.columns)
        assertEquals(2, CardSize.LARGE.columns)
        assertEquals(CardSize.NORMAL, CardSize.fromKey("garbage"))
    }
}

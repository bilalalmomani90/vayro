package com.vayro.browser.engine

import com.vayro.browser.downloads.FetchResponse
import com.vayro.browser.downloads.Fetcher
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import java.io.IOException

/** يجلب الملفات عبر شبكة Gecko نفسها كي تُرسَل ملفات الارتباط وجلسة الموقع مع الطلب. */
class GeckoFetcher(runtime: GeckoRuntime) : Fetcher {

    private val executor = GeckoWebExecutor(runtime)

    override fun open(url: String, rangeStart: Long, referrer: String?): FetchResponse {
        val builder = WebRequest.Builder(url).method("GET")
        if (rangeStart > 0) builder.header("Range", "bytes=$rangeStart-") // استكمال من نقطة محددة
        if (referrer != null) builder.referrer(referrer)
        // poll يحجب الخيط الخلفي الحالي (لا يُستدعى أبداً على خيط الواجهة)
        val response = executor.fetch(builder.build(), GeckoWebExecutor.FETCH_FLAGS_NONE)
            .poll(TIMEOUT_MS) ?: throw IOException("no response")
        return FetchResponse(response.statusCode, response.headers, response.body)
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}

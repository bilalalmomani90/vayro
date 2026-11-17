package com.vayro.browser.downloads

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** خادم وهمي يدعم (أو لا يدعم) Range. */
private class FakeFetcher(private val data: ByteArray, private val supportsRange: Boolean = true, private val status: Int = 200) : Fetcher {
    override fun open(url: String, rangeStart: Long, referrer: String?): FetchResponse {
        if (status != 200) return FetchResponse(status, emptyMap(), null)
        return if (rangeStart > 0 && supportsRange) {
            val rest = data.copyOfRange(rangeStart.toInt(), data.size)
            FetchResponse(
                206,
                mapOf("Content-Range" to "bytes $rangeStart-${data.size - 1}/${data.size}",
                    "Content-Length" to rest.size.toString(), "Accept-Ranges" to "bytes"),
                ByteArrayInputStream(rest)
            )
        } else {
            FetchResponse(
                200, mapOf("Content-Length" to data.size.toString(), "Accept-Ranges" to if (supportsRange) "bytes" else "none"),
                ByteArrayInputStream(data)
            )
        }
    }
}

class DownloadsTest {

    private val data = ByteArray(100_000) { (it % 251).toByte() }

    // ساعة تتقدم ثانية عند كل قراءة كي يُبلَّغ التقدم بعد كل جزء
    private fun worker(fetcher: Fetcher): DownloadWorker {
        var t = 0L
        return DownloadWorker(fetcher) { t += 1000; t }
    }

    private fun tempPart(): File = File.createTempFile("vayro", ".part").also { it.delete(); it.deleteOnExit() }

    @Test fun fullDownloadCompletes() {
        val part = tempPart()
        val result = worker(FakeFetcher(data)).run("https://a.com/f", null, part, TransferControl(), {}, { _, _, _ -> })
        assertEquals(TransferResult.Completed, result)
        assertArrayEquals(data, part.readBytes())
    }

    @Test fun pauseThenResumeProducesIdenticalFile() {
        val part = tempPart()
        val control = TransferControl()
        val first = worker(FakeFetcher(data)).run("u", null, part, control, {}, { _, _, _ -> control.paused = true })
        assertEquals(TransferResult.Paused, first)
        assertTrue(part.length() in 1 until data.size.toLong())

        val second = worker(FakeFetcher(data)).run("u", null, part, TransferControl(), {}, { _, _, _ -> })
        assertEquals(TransferResult.Completed, second)
        assertArrayEquals(data, part.readBytes())
    }

    @Test fun serverIgnoringRangeRestartsFromZero() {
        val part = tempPart()
        val control = TransferControl()
        worker(FakeFetcher(data, supportsRange = false)).run("u", null, part, control, {}, { _, _, _ -> control.paused = true })
        val result = worker(FakeFetcher(data, supportsRange = false)).run("u", null, part, TransferControl(), {}, { _, _, _ -> })
        assertEquals(TransferResult.Completed, result)
        assertArrayEquals(data, part.readBytes())
    }

    @Test fun httpErrorFails() {
        val result = worker(FakeFetcher(data, status = 404)).run("u", null, tempPart(), TransferControl(), {}, { _, _, _ -> })
        assertEquals(TransferResult.Failed("HTTP 404"), result)
    }

    @Test fun resumableFlagComesFromAcceptRanges() {
        var info: TransferInfo? = null
        worker(FakeFetcher(data)).run("u", null, tempPart(), TransferControl(), { info = it }, { _, _, _ -> })
        assertTrue(info!!.resumable)
        assertEquals(data.size.toLong(), info!!.total)
    }

    @Test fun riskAssessment() {
        assertEquals(FileSafety.Risk.EXECUTABLE, FileSafety.assess("installer.apk"))
        assertEquals(FileSafety.Risk.DISGUISED, FileSafety.assess("invoice.pdf.exe"))
        assertEquals(FileSafety.Risk.DISGUISED, FileSafety.assess("photo\u202Egpj.exe"))
        assertEquals(FileSafety.Risk.NONE, FileSafety.assess("report.pdf"))
        assertEquals(FileSafety.Risk.NONE, FileSafety.assess("noextension"))
    }

    @Test fun sanitizeCleansNames() {
        assertEquals("a_b.txt", FileSafety.sanitize("a/b.txt"))
        assertEquals("download", FileSafety.sanitize("..."))
        assertEquals("gnp.exe", FileSafety.sanitize("\u202Egnp.exe")) // حرف عكس الاتجاه يُحذف
        val long = FileSafety.sanitize("x".repeat(300) + ".pdf")
        assertTrue(long.length <= 120 && long.endsWith(".pdf"))
    }

    @Test fun categoriesByExtensionThenMime() {
        assertEquals(DownloadCategory.IMAGES, DownloadCategory.of("a.PNG", null))
        assertEquals(DownloadCategory.DOCUMENTS, DownloadCategory.of("a.pdf", null))
        assertEquals(DownloadCategory.APPS, DownloadCategory.of("a.apk", null))
        assertEquals(DownloadCategory.VIDEO, DownloadCategory.of("noext", "video/mp4"))
        assertEquals(DownloadCategory.OTHER, DownloadCategory.of("a.xyz", "application/x-foo"))
    }

    @Test fun filterBySearchAndCategory() {
        fun item(id: Long, name: String) = DownloadItem(
            id, "u", null, name, "x", DownloadCategory.of(name, null), 1, 1, DownloadStatus.COMPLETED, true, 0
        )
        val list = listOf(item(1, "Report.pdf"), item(2, "cat.png"), item(3, "report2.zip"))
        assertEquals(listOf(1L, 3L), DownloadFilter.apply(list, "report", null).map { it.id })
        assertEquals(listOf(2L), DownloadFilter.apply(list, "", DownloadCategory.IMAGES).map { it.id })
        assertEquals(3, DownloadFilter.apply(list, " ", null).size)
    }
}

package com.vayro.browser.downloads

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** استجابة شبكة مجردة (يوفرها GeckoFetcher فعلياً وبديل اختبار في الاختبارات). */
class FetchResponse(val statusCode: Int, val headers: Map<String, String>, val body: InputStream?) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

interface Fetcher {
    /** يفتح الرابط من البايت rangeStart (0 = من البداية). */
    fun open(url: String, rangeStart: Long, referrer: String?): FetchResponse
}

/** أعلام التحكم تُضبط من خيط آخر لإيقاف النقل مؤقتاً أو إلغائه. */
class TransferControl {
    @Volatile var paused = false
    @Volatile var canceled = false
}

sealed class TransferResult {
    object Completed : TransferResult()
    object Paused : TransferResult()
    object Canceled : TransferResult()
    data class Failed(val message: String) : TransferResult()
}

data class TransferInfo(val total: Long, val resumable: Boolean)

/**
 * ينقل ملفاً واحداً إلى ملف .part مع دعم الاستكمال (Range).
 * إن تجاهل الخادم Range (استجاب 200) نبدأ من الصفر بدل إفساد الملف.
 */
class DownloadWorker(
    private val fetcher: Fetcher,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun run(
        url: String,
        referrer: String?,
        partFile: File,
        control: TransferControl,
        onInfo: (TransferInfo) -> Unit,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit
    ): TransferResult {
        if (control.canceled) return TransferResult.Canceled
        if (control.paused) return TransferResult.Paused

        var restarted = false
        while (true) {
            var existing = if (partFile.exists()) partFile.length() else 0L
            val response = try {
                fetcher.open(url, existing, referrer)
            } catch (e: Exception) {
                return TransferResult.Failed(e.message ?: "network error")
            }
            val body = response.body
            try {
                when {
                    // نطاق غير صالح: الملف الجزئي لا يطابق الخادم، نعيد من الصفر مرة واحدة
                    response.statusCode == 416 && existing > 0 && !restarted -> {
                        partFile.delete()
                        restarted = true
                        continue
                    }
                    response.statusCode == 206 && existing > 0 -> Unit // استكمال فعلي
                    response.statusCode in 200..299 -> {
                        if (existing > 0) { // الخادم تجاهل Range
                            partFile.delete()
                            existing = 0
                        }
                    }
                    else -> return TransferResult.Failed("HTTP ${response.statusCode}")
                }
                if (body == null) return TransferResult.Failed("empty response")

                val resumable = response.statusCode == 206 ||
                    response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                val total = parseTotal(response, existing)
                onInfo(TransferInfo(total, resumable))

                var downloaded = existing
                FileOutputStream(partFile, existing > 0).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastTick = clock()
                    var bytesAtTick = downloaded
                    while (true) {
                        if (control.canceled) return TransferResult.Canceled
                        if (control.paused) return TransferResult.Paused
                        val n = body.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        downloaded += n
                        val now = clock()
                        if (now - lastTick >= PROGRESS_INTERVAL_MS) {
                            val speed = (downloaded - bytesAtTick) * 1000 / (now - lastTick)
                            onProgress(downloaded, total, speed)
                            lastTick = now
                            bytesAtTick = downloaded
                            // إيقاف مؤقت/إلغاء طُلب أثناء إشعار التقدم يُلتقط في الدورة التالية
                        }
                    }
                }
                onProgress(downloaded, total, 0)
                if (total >= 0 && downloaded < total) return TransferResult.Failed("connection closed early")
                return TransferResult.Completed
            } catch (e: IOException) {
                return TransferResult.Failed(e.message ?: "I/O error")
            } finally {
                try { body?.close() } catch (_: Exception) {}
            }
        }
    }

    /** الحجم الكلي: من Content-Range في 206، وإلا existing + Content-Length، وإلا مجهول (-1). */
    private fun parseTotal(response: FetchResponse, existing: Long): Long {
        if (response.statusCode == 206) {
            response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { return it }
        }
        val length = response.header("Content-Length")?.toLongOrNull() ?: return -1
        return existing + length
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val PROGRESS_INTERVAL_MS = 400L
    }
}

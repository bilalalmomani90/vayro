package com.vayro.browser.downloads

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.vayro.browser.core.UrlResolver
import com.vayro.browser.data.DownloadRepository
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * مركز التنزيلات: القائمة، التنفيذ (3 تنزيلات بالتوازي)، الإيقاف المؤقت والاستكمال، وحفظ الحالة.
 * كل تعديل على القائمة يتم على الخيط الرئيسي فقط؛ النقل نفسه على خيوط خلفية.
 */
class DownloadCenter(
    private val context: Context,
    private val repo: DownloadRepository,
    private val fetcherProvider: () -> Fetcher,
    private val saver: FileSaver
) {
    /** تنبيهات للواجهة (تُستدعى على الخيط الرئيسي). */
    interface UiListener {
        fun onRiskyDownload(fileName: String, risk: FileSafety.Risk, proceed: () -> Unit)
        fun onDownloadStarted(fileName: String)
        fun onDownloadFinished(item: DownloadItem)
        fun onUnsupported()
    }

    var uiListener: UiListener? = null

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(MAX_PARALLEL)
    private val observers = CopyOnWriteArrayList<(List<DownloadItem>) -> Unit>()
    private val controls = HashMap<Long, TransferControl>()
    private val lastPersist = HashMap<Long, Long>()
    private val partsDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private var items: List<DownloadItem> = emptyList()
    private var serviceRunning = false

    init {
        // استعادة القائمة المحفوظة؛ ما كان جارياً قبل إغلاق التطبيق يصبح "متوقفاً مؤقتاً"
        repo.all { loaded ->
            items = loaded.map {
                if (it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED) {
                    it.copy(status = DownloadStatus.PAUSED).also { fixed -> repo.update(fixed) }
                } else it
            } + items
            notifyObservers()
        }
    }

    // ---- مراقبة القائمة ----
    fun addObserver(observer: (List<DownloadItem>) -> Unit) {
        observers.add(observer)
        observer(items)
    }

    fun removeObserver(observer: (List<DownloadItem>) -> Unit) {
        observers.remove(observer)
    }

    fun activeCount(): Int = items.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }

    private fun notifyObservers() {
        val snapshot = items
        observers.forEach { it(snapshot) }
    }

    // ---- بدء التنزيل ----
    /** يُستدعى من المحرك عند وصول ملف للتنزيل. */
    fun request(request: DownloadRequest) {
        if (!UrlResolver.isWebUrl(request.url)) {
            uiListener?.onUnsupported() // روابط blob: و data: غير مدعومة بعد
            return
        }
        val risk = FileSafety.assess(request.fileName)
        val safe = request.copy(fileName = FileSafety.sanitize(request.fileName))
        if (risk == FileSafety.Risk.NONE) {
            enqueue(safe)
        } else {
            // ملف مشبوه: لا يبدأ إلا بموافقة صريحة، وبدون واجهة نافذة لا يبدأ إطلاقاً
            val listener = uiListener ?: return
            listener.onRiskyDownload(safe.fileName, risk) { enqueue(safe) }
        }
    }

    private fun enqueue(request: DownloadRequest) {
        val mime = request.mime.ifBlank { "application/octet-stream" }
        val draft = DownloadItem(
            id = 0, url = request.url, referrer = request.referrer, fileName = request.fileName, mime = mime,
            category = DownloadCategory.of(request.fileName, mime), totalBytes = request.contentLength,
            downloadedBytes = 0, status = DownloadStatus.QUEUED, resumable = false,
            createdAt = System.currentTimeMillis()
        )
        repo.insert(draft) { id ->
            items = listOf(draft.copy(id = id)) + items
            notifyObservers()
            uiListener?.onDownloadStarted(draft.fileName)
            startTransfer(id)
        }
    }

    private fun find(id: Long) = items.firstOrNull { it.id == id }

    private fun startTransfer(id: Long) {
        val item = find(id) ?: return
        if (controls.containsKey(id)) return // يعمل بالفعل
        val control = TransferControl()
        controls[id] = control
        update(id, persist = true) { it.copy(status = DownloadStatus.QUEUED, error = null) }
        ensureService()

        // لقطة من العنصر تُمرَّر للخيط الخلفي: القائمة نفسها لا تُقرأ إلا من الخيط الرئيسي
        pool.execute { runTransfer(item, control) }
    }

    private fun runTransfer(item: DownloadItem, control: TransferControl) {
        val id = item.id
        val part = File(partsDir, "$id.part")
        main.post { if (!control.canceled) update(id) { it.copy(status = DownloadStatus.RUNNING) } }

        val result = DownloadWorker(fetcherProvider()).run(
            item.url, item.referrer, part, control,
            onInfo = { info ->
                main.post {
                    update(id, persist = true) {
                        it.copy(totalBytes = if (info.total >= 0) info.total else it.totalBytes, resumable = info.resumable)
                    }
                }
            },
            onProgress = { downloaded, total, speed ->
                main.post {
                    update(id) {
                        it.copy(
                            downloadedBytes = downloaded, speedBps = speed,
                            totalBytes = if (total >= 0) total else it.totalBytes
                        )
                    }
                }
            }
        )

        when (result) {
            TransferResult.Completed -> publish(item, part)
            TransferResult.Paused -> main.post {
                controls.remove(id)
                update(id, persist = true) { it.copy(status = DownloadStatus.PAUSED, speedBps = 0) }
                stopServiceIfIdle()
            }
            TransferResult.Canceled -> {
                part.delete()
                main.post { controls.remove(id); stopServiceIfIdle() }
            }
            is TransferResult.Failed -> main.post {
                controls.remove(id)
                update(id, persist = true) {
                    it.copy(status = DownloadStatus.FAILED, error = result.message, speedBps = 0)
                }
                stopServiceIfIdle()
            }
        }
    }

    /** نسخ الملف المكتمل إلى Download/VAYRO (على الخيط الخلفي). */
    private fun publish(item: DownloadItem, part: File) {
        val id = item.id
        try {
            val uri = saver.publish(part, item.fileName, item.mime, item.category)
            part.delete()
            main.post {
                controls.remove(id)
                update(id, persist = true) {
                    it.copy(
                        status = DownloadStatus.COMPLETED, uri = uri, speedBps = 0,
                        downloadedBytes = if (it.totalBytes >= 0) it.totalBytes else it.downloadedBytes
                    )
                }
                find(id)?.let { done -> uiListener?.onDownloadFinished(done) }
                stopServiceIfIdle()
            }
        } catch (e: Exception) {
            main.post {
                controls.remove(id)
                update(id, persist = true) {
                    it.copy(status = DownloadStatus.FAILED, error = e.message ?: "save failed", speedBps = 0)
                }
                stopServiceIfIdle()
            }
        }
    }

    // ---- أوامر المستخدم ----
    fun pause(id: Long) {
        controls[id]?.paused = true
    }

    /** استكمال أو إعادة المحاولة: إن لم يدعم الخادم الاستكمال يبدأ الملف من الصفر تلقائياً. */
    fun resume(id: Long) = startTransfer(id)

    /** إلغاء تنزيل غير مكتمل: يحذف الجزء المحمَّل والعنصر. */
    fun cancel(id: Long) {
        controls[id]?.canceled = true
        File(partsDir, "$id.part").delete()
        removeFromList(id)
    }

    /** إزالة عنصر مكتمل أو فاشل، مع خيار حذف الملف نفسه. */
    fun remove(id: Long, deleteFile: Boolean) {
        val item = find(id) ?: return
        if (deleteFile) item.uri?.let { saver.delete(it) }
        File(partsDir, "$id.part").delete()
        removeFromList(id)
    }

    private fun removeFromList(id: Long) {
        items = items.filter { it.id != id }
        repo.delete(id)
        notifyObservers()
    }

    fun viewIntent(item: DownloadItem): Intent? = item.uri?.let { saver.viewIntent(it, item.mime) }

    // ---- تحديث الحالة ----
    private fun update(id: Long, persist: Boolean = false, transform: (DownloadItem) -> DownloadItem) {
        var changed: DownloadItem? = null
        items = items.map { if (it.id == id) transform(it).also { n -> changed = n } else it }
        val item = changed ?: return
        val now = System.currentTimeMillis()
        // التقدم يُحفظ كل ثانيتين فقط؛ تغيرات الحالة تُحفظ فوراً
        if (persist || now - (lastPersist[id] ?: 0L) >= PERSIST_INTERVAL_MS) {
            lastPersist[id] = now
            repo.update(item)
        }
        notifyObservers()
    }

    // ---- الخدمة الأمامية (تبقي التنزيل حياً عند مغادرة التطبيق) ----
    private fun ensureService() {
        if (serviceRunning) return
        serviceRunning = true
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: Exception) {
            serviceRunning = false // التنزيل يستمر ما دامت العملية حية
        }
    }

    private fun stopServiceIfIdle() {
        // الخدمة نفسها تراقب القائمة وتتوقف عند خلوها؛ هنا نعيد ضبط العلم فقط عندما لا نشاط
        if (activeCount() == 0) serviceRunning = false
    }

    fun onServiceStopped() {
        serviceRunning = false
    }

    private companion object {
        const val MAX_PARALLEL = 3
        const val PERSIST_INTERVAL_MS = 2000L
    }
}

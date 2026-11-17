package com.vayro.browser.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** ينشر الملف المكتمل في مجلد التنزيلات العام. */
interface FileSaver {
    /** ينسخ ملف .part إلى مكانه النهائي ويعيد Uri كنص؛ يرمي IOException عند الفشل. */
    fun publish(part: File, fileName: String, mime: String, category: DownloadCategory): String
    fun delete(uri: String)
    fun viewIntent(uri: String, mime: String): Intent
}

/** التنفيذ عبر MediaStore: Download/VAYRO/<التصنيف> بلا حاجة لأذونات تخزين (Android 10+). */
class MediaStoreSaver(private val context: Context) : FileSaver {

    override fun publish(part: File, fileName: String, mime: String, category: DownloadCategory): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VAYRO/${category.folder}")
            put(MediaStore.Downloads.IS_PENDING, 1) // مخفي حتى اكتمال النسخ
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("could not create file")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("could not open file")
            out.use { stream -> part.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // لا نترك ملفاً ناقصاً
            throw if (e is IOException) e else IOException(e.message)
        }
    }

    override fun delete(uri: String) {
        try { context.contentResolver.delete(Uri.parse(uri), null, null) } catch (_: Exception) {}
    }

    override fun viewIntent(uri: String, mime: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

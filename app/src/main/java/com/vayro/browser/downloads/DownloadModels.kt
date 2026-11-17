package com.vayro.browser.downloads

enum class DownloadStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED;

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.name == key } ?: FAILED
    }
}

/** تصنيف الملفات (يحدد المجلد داخل Download/VAYRO وفلاتر القائمة). */
enum class DownloadCategory(val folder: String) {
    DOCUMENTS("Documents"), IMAGES("Images"), VIDEO("Video"), AUDIO("Audio"),
    ARCHIVES("Archives"), APPS("Apps"), OTHER("Other");

    companion object {
        fun fromKey(key: String?) = values().firstOrNull { it.name == key } ?: OTHER

        private val IMAGES_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "avif")
        private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")
        private val APP_EXT = setOf("apk", "xapk", "apks", "aab", "exe", "msi", "dmg", "pkg", "deb")
        private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "csv", "epub", "md")

        /** الامتداد أولاً، ثم نوع MIME كاحتياط. */
        fun of(fileName: String, mime: String?): DownloadCategory {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                ext in IMAGES_EXT -> IMAGES
                ext in VIDEO_EXT -> VIDEO
                ext in AUDIO_EXT -> AUDIO
                ext in ARCHIVE_EXT -> ARCHIVES
                ext in APP_EXT -> APPS
                ext in DOC_EXT -> DOCUMENTS
                mime?.startsWith("image/") == true -> IMAGES
                mime?.startsWith("video/") == true -> VIDEO
                mime?.startsWith("audio/") == true -> AUDIO
                mime == "application/pdf" -> DOCUMENTS
                else -> OTHER
            }
        }
    }
}

/** عنصر تنزيل غير قابل للتعديل؛ التحديث يتم بإنشاء نسخة (copy) كي تعمل قوائم الواجهة بسلاسة. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val referrer: String?,
    val fileName: String,
    val mime: String,
    val category: DownloadCategory,
    val totalBytes: Long,        // -1 إذا كان الحجم غير معروف
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val resumable: Boolean,      // هل أكد الخادم دعم الاستكمال؟
    val createdAt: Long,
    val uri: String? = null,     // مكان الملف النهائي بعد الاكتمال
    val error: String? = null,
    val speedBps: Long = 0       // مؤقتة: لا تُحفظ في قاعدة البيانات
)

/** طلب تنزيل صادر من المحرك. */
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mime: String,
    val contentLength: Long,
    val referrer: String?
)

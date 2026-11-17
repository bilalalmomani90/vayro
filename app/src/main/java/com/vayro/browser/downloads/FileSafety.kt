package com.vayro.browser.downloads

/** أسماء الملفات وتقييم الخطورة (منطق خالص). */
object FileSafety {

    enum class Risk { NONE, EXECUTABLE, DISGUISED }

    private val RISKY_EXT = setOf(
        "apk", "xapk", "apks", "exe", "msi", "bat", "cmd", "com", "scr", "pif", "jar",
        "js", "vbs", "wsf", "ps1", "sh", "dmg", "pkg", "lnk", "hta", "reg", "dll"
    )
    private val HARMLESS_LOOKING_EXT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "jpg", "jpeg", "png",
        "gif", "mp3", "mp4", "zip", "rar"
    )
    private val BIDI_CONTROLS = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")

    /** يقيّم الاسم الخام كما وصل من الخادم (قبل التنظيف). */
    fun assess(rawName: String): Risk {
        if (BIDI_CONTROLS.containsMatchIn(rawName)) return Risk.DISGUISED // خدعة عكس اتجاه النص
        val parts = rawName.lowercase().split('.')
        if (parts.size < 2) return Risk.NONE
        val last = parts.last()
        if (last !in RISKY_EXT) return Risk.NONE
        val previous = parts[parts.size - 2]
        return if (parts.size >= 3 && previous in HARMLESS_LOOKING_EXT) Risk.DISGUISED else Risk.EXECUTABLE
    }

    /** تنظيف الاسم: لا فواصل مسارات ولا رموز تحكم ولا حروف عكس الاتجاه، وطول معقول. */
    fun sanitize(rawName: String): String {
        var name = BIDI_CONTROLS.replace(rawName, "")
        name = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.', ' ')
        if (name.isEmpty()) return "download"
        if (name.length > MAX_NAME) {
            val ext = name.substringAfterLast('.', "")
            name = if (ext.isNotEmpty() && ext.length <= 10) {
                name.substringBeforeLast('.').take(MAX_NAME - ext.length - 1) + "." + ext
            } else name.take(MAX_NAME)
        }
        return name
    }

    private const val MAX_NAME = 120
}

/** فلترة قائمة التنزيلات بالبحث والتصنيف. */
object DownloadFilter {
    fun apply(items: List<DownloadItem>, query: String, category: DownloadCategory?): List<DownloadItem> {
        val q = query.trim()
        return items.filter { item ->
            (category == null || item.category == category) &&
                (q.isEmpty() || item.fileName.contains(q, ignoreCase = true))
        }
    }
}

package com.vayro.browser.data

import android.content.ContentValues
import com.vayro.browser.downloads.DownloadCategory
import com.vayro.browser.downloads.DownloadItem
import com.vayro.browser.downloads.DownloadStatus

class DownloadRepository(private val helper: BrowserDb) {

    private fun values(item: DownloadItem) = ContentValues().apply {
        put("url", item.url)
        put("referrer", item.referrer)
        put("file_name", item.fileName)
        put("mime", item.mime)
        put("category", item.category.name)
        put("total_bytes", item.totalBytes)
        put("downloaded_bytes", item.downloadedBytes)
        put("status", item.status.name)
        put("resumable", if (item.resumable) 1 else 0)
        put("created_at", item.createdAt)
        put("uri", item.uri)
        put("error", item.error)
    }

    fun all(done: (List<DownloadItem>) -> Unit) = AsyncDb.query({
        val list = mutableListOf<DownloadItem>()
        helper.readableDatabase.query("downloads", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    DownloadItem(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        url = c.getString(c.getColumnIndexOrThrow("url")),
                        referrer = c.getString(c.getColumnIndexOrThrow("referrer")),
                        fileName = c.getString(c.getColumnIndexOrThrow("file_name")),
                        mime = c.getString(c.getColumnIndexOrThrow("mime")),
                        category = DownloadCategory.fromKey(c.getString(c.getColumnIndexOrThrow("category"))),
                        totalBytes = c.getLong(c.getColumnIndexOrThrow("total_bytes")),
                        downloadedBytes = c.getLong(c.getColumnIndexOrThrow("downloaded_bytes")),
                        status = DownloadStatus.fromKey(c.getString(c.getColumnIndexOrThrow("status"))),
                        resumable = c.getInt(c.getColumnIndexOrThrow("resumable")) == 1,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        uri = c.getString(c.getColumnIndexOrThrow("uri")),
                        error = c.getString(c.getColumnIndexOrThrow("error"))
                    )
                )
            }
        }
        list
    }, done)

    fun insert(item: DownloadItem, done: (Long) -> Unit) =
        AsyncDb.query({ helper.writableDatabase.insert("downloads", null, values(item)) }, done)

    fun update(item: DownloadItem) = AsyncDb.run {
        helper.writableDatabase.update("downloads", values(item), "id=?", arrayOf(item.id.toString()))
    }

    fun delete(id: Long) = AsyncDb.run {
        helper.writableDatabase.delete("downloads", "id=?", arrayOf(id.toString()))
    }
}

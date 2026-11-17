package com.vayro.browser.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.vayro.browser.core.SqlLike

/** ما يحتاجه TabManager من السجل (واجهة صغيرة تسمح بالاختبار دون Android). */
interface HistorySink {
    fun record(url: String, title: String?)
    fun updateTitle(url: String, title: String)
}

class HistoryRepository(private val helper: BrowserDb) : HistorySink {

    override fun record(url: String, title: String?) = AsyncDb.run {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title.orEmpty())
            put("visited_at", System.currentTimeMillis())
        }
        // REPLACE: إن وُجد الرابط يُستبدل بسجل أحدث
        helper.writableDatabase.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun updateTitle(url: String, title: String) = AsyncDb.run {
        val values = ContentValues().apply { put("title", title) }
        helper.writableDatabase.update("history", values, "url=?", arrayOf(url))
    }

    /** الأحدث أولاً. */
    fun recent(limit: Int = 300, done: (List<Entry>) -> Unit) = AsyncDb.query({
        val list = mutableListOf<Entry>()
        helper.readableDatabase.query(
            "history", arrayOf("id", "url", "title"), null, null, null, null,
            "visited_at DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) list.add(Entry(c.getLong(0), c.getString(1), c.getString(2)))
        }
        list
    }, done)

    /** اقتراحات محلية من السجل أثناء الكتابة (لا يُرسل شيء خارج الجهاز). */
    fun search(query: String, limit: Int, done: (List<Entry>) -> Unit) = AsyncDb.query({
        val pattern = "%" + SqlLike.escape(query) + "%"
        val list = mutableListOf<Entry>()
        helper.readableDatabase.rawQuery(
            "SELECT id, url, title FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' " +
                "ORDER BY visited_at DESC LIMIT ?", arrayOf(pattern, pattern, limit.toString())
        ).use { c -> while (c.moveToNext()) list.add(Entry(c.getLong(0), c.getString(1), c.getString(2))) }
        list
    }, done)

    fun delete(id: Long) = AsyncDb.run {
        helper.writableDatabase.delete("history", "id=?", arrayOf(id.toString()))
    }

    fun clear() = AsyncDb.run { helper.writableDatabase.delete("history", null, null) }
}

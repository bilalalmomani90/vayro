package com.vayro.browser.data

import android.content.ContentValues
import android.database.DatabaseUtils
import com.vayro.browser.core.SqlLike

/** المفضلة لكل مساحة عمل؛ [workspaceId] يُقرأ عند كل عملية لأن المساحة الحالية قد تتغير. */
class BookmarkRepository(private val helper: BrowserDb, private val workspaceId: () -> Long) {

    fun isBookmarked(url: String, done: (Boolean) -> Unit) {
        val ws = workspaceId()
        AsyncDb.query({
            DatabaseUtils.queryNumEntries(
                helper.readableDatabase, "bookmarks", "workspace_id=? AND url=?", arrayOf(ws.toString(), url)
            ) > 0
        }, done)
    }

    /** يضيف الرابط إن لم يكن مفضلاً ويحذفه إن كان مفضلاً. النتيجة: هل أصبح مفضلاً الآن؟ */
    fun toggle(url: String, title: String?, done: (Boolean) -> Unit) {
        val ws = workspaceId()
        AsyncDb.query({
            val db = helper.writableDatabase
            if (db.delete("bookmarks", "workspace_id=? AND url=?", arrayOf(ws.toString(), url)) > 0) {
                false
            } else {
                val values = ContentValues().apply {
                    put("workspace_id", ws)
                    put("url", url)
                    put("title", title.orEmpty())
                    put("created_at", System.currentTimeMillis())
                }
                db.insert("bookmarks", null, values)
                true
            }
        }, done)
    }

    /** الأحدث إضافةً أولاً. */
    fun all(done: (List<Entry>) -> Unit) {
        val ws = workspaceId()
        AsyncDb.query({
            val list = mutableListOf<Entry>()
            helper.readableDatabase.query(
                "bookmarks", arrayOf("id", "url", "title"), "workspace_id=?", arrayOf(ws.toString()),
                null, null, "created_at DESC"
            ).use { c ->
                while (c.moveToNext()) list.add(Entry(c.getLong(0), c.getString(1), c.getString(2)))
            }
            list
        }, done)
    }

    /** اقتراحات محلية من مفضلة المساحة الحالية. */
    fun search(query: String, limit: Int, done: (List<Entry>) -> Unit) {
        val ws = workspaceId()
        val pattern = "%" + SqlLike.escape(query) + "%"
        AsyncDb.query({
            val list = mutableListOf<Entry>()
            helper.readableDatabase.rawQuery(
                "SELECT id, url, title FROM bookmarks WHERE workspace_id = ? AND " +
                    "(url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\') ORDER BY created_at DESC LIMIT ?",
                arrayOf(ws.toString(), pattern, pattern, limit.toString())
            ).use { c -> while (c.moveToNext()) list.add(Entry(c.getLong(0), c.getString(1), c.getString(2))) }
            list
        }, done)
    }

    fun delete(id: Long) = AsyncDb.run {
        helper.writableDatabase.delete("bookmarks", "id=?", arrayOf(id.toString()))
    }
}

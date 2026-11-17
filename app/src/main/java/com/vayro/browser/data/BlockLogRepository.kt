package com.vayro.browser.data

import android.content.ContentValues
import com.vayro.browser.core.BlockReason

data class BlockEntry(val timestamp: Long, val reason: BlockReason, val blockedHost: String, val pageHost: String)

/** سجل الحجب المحلي: يغذي الإحصائيات والتقارير وأسباب الحجب. */
class BlockLogRepository(private val helper: BrowserDb) {

    fun record(reason: BlockReason, blockedHost: String, pageHost: String) = AsyncDb.run {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("reason", reason.key)
            put("blocked_host", blockedHost)
            put("page_host", pageHost)
        }
        db.insert("block_log", null, values)
        // نحتفظ بآخر 2000 سجل فقط كي لا ينمو الجدول بلا حد
        db.execSQL("DELETE FROM block_log WHERE id <= (SELECT MAX(id) FROM block_log) - 2000")
    }

    /** عدد الحجوبات لكل سبب منذ وقت معين، مع إمكانية الحصر بموقع واحد. */
    fun counts(since: Long, pageHost: String? = null, done: (Map<BlockReason, Int>) -> Unit) = AsyncDb.query({
        val result = mutableMapOf<BlockReason, Int>()
        val sql = "SELECT reason, COUNT(*) FROM block_log WHERE ts >= ?" +
            (if (pageHost != null) " AND page_host = ?" else "") + " GROUP BY reason"
        val args = if (pageHost != null) arrayOf(since.toString(), pageHost) else arrayOf(since.toString())
        helper.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) BlockReason.fromKey(c.getString(0))?.let { result[it] = c.getInt(1) }
        }
        result
    }, done)

    fun recent(limit: Int = 100, done: (List<BlockEntry>) -> Unit) = AsyncDb.query({
        val list = mutableListOf<BlockEntry>()
        helper.readableDatabase.query(
            "block_log", arrayOf("ts", "reason", "blocked_host", "page_host"),
            null, null, null, null, "id DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                val reason = BlockReason.fromKey(c.getString(1)) ?: continue
                list.add(BlockEntry(c.getLong(0), reason, c.getString(2), c.getString(3)))
            }
        }
        list
    }, done)

    fun clear() = AsyncDb.run { helper.writableDatabase.delete("block_log", null, null) }
}

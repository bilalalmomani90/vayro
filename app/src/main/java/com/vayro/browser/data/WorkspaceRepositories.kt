package com.vayro.browser.data

import android.content.ContentValues

data class Workspace(val id: Long, val name: String, val emoji: String)
data class Shortcut(val id: Long, val title: String, val url: String)
data class GroupItem(val id: Long, val url: String, val title: String?)
data class SiteGroup(val id: Long, val name: String, val items: List<GroupItem>)

class WorkspaceRepository(private val helper: BrowserDb) {

    fun all(done: (List<Workspace>) -> Unit) = AsyncDb.query({
        val list = mutableListOf<Workspace>()
        helper.readableDatabase.query("workspaces", arrayOf("id", "name", "emoji"), null, null, null, null, "id ASC").use { c ->
            while (c.moveToNext()) list.add(Workspace(c.getLong(0), c.getString(1), c.getString(2)))
        }
        list
    }, done)

    fun create(name: String, emoji: String, done: (Long) -> Unit) = AsyncDb.query({
        helper.writableDatabase.insert("workspaces", null, ContentValues().apply {
            put("name", name)
            put("emoji", emoji)
        })
    }, done)

    /** يحذف المساحة وكل ما فيها (مفضلة، اختصارات، مجموعات). */
    fun delete(id: Long) = AsyncDb.run {
        val db = helper.writableDatabase
        val args = arrayOf(id.toString())
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM site_group_items WHERE group_id IN (SELECT id FROM site_groups WHERE workspace_id=?)", args)
            db.delete("site_groups", "workspace_id=?", args)
            db.delete("shortcuts", "workspace_id=?", args)
            db.delete("bookmarks", "workspace_id=?", args)
            db.delete("workspaces", "id=?", args)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

class ShortcutRepository(private val helper: BrowserDb, private val workspaceId: () -> Long) {

    fun all(done: (List<Shortcut>) -> Unit) {
        val ws = workspaceId()
        AsyncDb.query({
            val list = mutableListOf<Shortcut>()
            helper.readableDatabase.query(
                "shortcuts", arrayOf("id", "title", "url"), "workspace_id=?", arrayOf(ws.toString()), null, null, "id ASC"
            ).use { c -> while (c.moveToNext()) list.add(Shortcut(c.getLong(0), c.getString(1), c.getString(2))) }
            list
        }, done)
    }

    fun add(title: String, url: String, done: () -> Unit = {}) {
        val ws = workspaceId()
        AsyncDb.query({
            helper.writableDatabase.insert("shortcuts", null, ContentValues().apply {
                put("workspace_id", ws)
                put("title", title)
                put("url", url)
            })
        }) { done() }
    }

    fun delete(id: Long) = AsyncDb.run { helper.writableDatabase.delete("shortcuts", "id=?", arrayOf(id.toString())) }
}

class SiteGroupRepository(private val helper: BrowserDb, private val workspaceId: () -> Long) {

    fun all(done: (List<SiteGroup>) -> Unit) {
        val ws = workspaceId()
        AsyncDb.query({
            val db = helper.readableDatabase
            val names = linkedMapOf<Long, String>()
            db.query("site_groups", arrayOf("id", "name"), "workspace_id=?", arrayOf(ws.toString()), null, null, "id ASC").use { c ->
                while (c.moveToNext()) names[c.getLong(0)] = c.getString(1)
            }
            val items = HashMap<Long, MutableList<GroupItem>>()
            db.rawQuery(
                "SELECT id, group_id, url, title FROM site_group_items WHERE group_id IN " +
                    "(SELECT id FROM site_groups WHERE workspace_id=?) ORDER BY id ASC", arrayOf(ws.toString())
            ).use { c ->
                while (c.moveToNext()) items.getOrPut(c.getLong(1)) { mutableListOf() }
                    .add(GroupItem(c.getLong(0), c.getString(2), c.getString(3)))
            }
            names.map { (id, name) -> SiteGroup(id, name, items[id].orEmpty()) }
        }, done)
    }

    fun create(name: String, done: (Long) -> Unit = {}) {
        val ws = workspaceId()
        AsyncDb.query({
            helper.writableDatabase.insert("site_groups", null, ContentValues().apply {
                put("workspace_id", ws)
                put("name", name)
            })
        }, done)
    }

    fun rename(id: Long, name: String) = AsyncDb.run {
        helper.writableDatabase.update("site_groups", ContentValues().apply { put("name", name) }, "id=?", arrayOf(id.toString()))
    }

    fun delete(id: Long) = AsyncDb.run {
        val db = helper.writableDatabase
        db.delete("site_group_items", "group_id=?", arrayOf(id.toString()))
        db.delete("site_groups", "id=?", arrayOf(id.toString()))
    }

    /** يضيف موقعاً للمجموعة إن لم يكن موجوداً فيها. */
    fun addItem(groupId: Long, url: String, title: String?, done: () -> Unit = {}) = AsyncDb.query({
        val db = helper.writableDatabase
        val exists = android.database.DatabaseUtils.queryNumEntries(
            db, "site_group_items", "group_id=? AND url=?", arrayOf(groupId.toString(), url)
        ) > 0
        if (!exists) {
            db.insert("site_group_items", null, ContentValues().apply {
                put("group_id", groupId)
                put("url", url)
                put("title", title.orEmpty())
            })
        }
    }) { done() }

    fun removeItem(itemId: Long) = AsyncDb.run {
        helper.writableDatabase.delete("site_group_items", "id=?", arrayOf(itemId.toString()))
    }
}

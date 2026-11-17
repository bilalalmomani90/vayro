package com.vayro.browser.tabs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedTab(val url: String?, val title: String?)
data class SavedSession(val tabs: List<SavedTab>, val selectedIndex: Int)

/** تخزين جلسة التبويبات لاستعادتها بعد إغلاق التطبيق. */
interface SessionStore {
    fun load(): SavedSession?
    fun save(session: SavedSession)
}

class PrefsSessionStore(context: Context, workspaceId: Long = 1L) : SessionStore {
    private val prefs = context.getSharedPreferences("vayro_session", Context.MODE_PRIVATE)
    private val key = keyFor(workspaceId)

    override fun load(): SavedSession? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("tabs")
            val tabs = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedTab(
                    if (o.isNull("url")) null else o.getString("url"),
                    if (o.isNull("title")) null else o.getString("title")
                )
            }
            SavedSession(tabs, root.optInt("selected", 0))
        } catch (e: Exception) {
            null // بيانات تالفة: نبدأ جلسة جديدة بدل الانهيار
        }
    }

    override fun save(session: SavedSession) {
        val arr = JSONArray()
        session.tabs.forEach { t ->
            arr.put(JSONObject().put("url", t.url ?: JSONObject.NULL).put("title", t.title ?: JSONObject.NULL))
        }
        val root = JSONObject().put("tabs", arr).put("selected", session.selectedIndex)
        prefs.edit().putString(key, root.toString()).apply()
    }

    companion object {
        // المساحة 1 تبقى على المفتاح القديم "session" كي لا تضيع تبويبات المستخدم عند الترقية
        private fun keyFor(id: Long) = if (id == 1L) "session" else "session_$id"

        fun clear(context: Context, workspaceId: Long) {
            context.getSharedPreferences("vayro_session", Context.MODE_PRIVATE).edit().remove(keyFor(workspaceId)).apply()
        }
    }
}

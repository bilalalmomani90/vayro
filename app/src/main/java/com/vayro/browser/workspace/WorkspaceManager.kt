package com.vayro.browser.workspace

import android.content.Context
import com.vayro.browser.data.WorkspaceRepository
import com.vayro.browser.tabs.PrefsSessionStore
import com.vayro.browser.tabs.TabManager

/**
 * مساحات العمل: لكل مساحة تبويباتها (TabManager خاص) ومفضلتها واختصاراتها ومجموعاتها.
 * عند التبديل تُحفظ المساحة القديمة وتُحرَّر محركاتها، فلا يبقى في الذاكرة إلا المساحة النشطة.
 */
class WorkspaceManager(
    private val context: Context,
    private val repo: WorkspaceRepository,
    private val tabManagerFactory: (workspaceId: Long) -> TabManager
) {
    interface Listener {
        fun onWorkspaceChanged()
    }

    var listener: Listener? = null

    private val prefs = context.getSharedPreferences("vayro_workspaces", Context.MODE_PRIVATE)
    var currentId: Long = prefs.getLong(KEY_CURRENT, DEFAULT_ID)
        private set

    private var manager: TabManager? = null

    /** مدير تبويبات المساحة الحالية (يُنشأ عند أول طلب). */
    val tabManager: TabManager
        get() = manager ?: tabManagerFactory(currentId).also { manager = it }

    fun all(done: (List<com.vayro.browser.data.Workspace>) -> Unit) = repo.all(done)

    fun create(name: String, emoji: String, done: (Long) -> Unit) = repo.create(name, emoji, done)

    fun switchTo(id: Long) {
        if (id == currentId) return
        manager?.shutdown()
        manager = null
        currentId = id
        prefs.edit().putLong(KEY_CURRENT, id).apply()
        listener?.onWorkspaceChanged()
    }

    /** حذف مساحة (الافتراضية لا تُحذف). إن كانت حالية ننتقل إلى الافتراضية أولاً. */
    fun delete(id: Long) {
        if (id == DEFAULT_ID) return
        if (id == currentId) switchTo(DEFAULT_ID)
        repo.delete(id)
        PrefsSessionStore.clear(context, id)
    }

    companion object {
        const val DEFAULT_ID = 1L
        private const val KEY_CURRENT = "current_id"
    }
}

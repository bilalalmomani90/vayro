package com.vayro.browser.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** قاعدة بيانات محلية للسجل والمفضلة. لا تُرسل أي بيانات خارج الجهاز. */
class BrowserDb(context: Context) : SQLiteOpenHelper(context, "vayro.db", null, 4) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        // url فريد: زيارة نفس الرابط تحدّث السجل ولا تكرره
        db.execSQL(
            "CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "url TEXT NOT NULL UNIQUE, title TEXT, visited_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_history_visited ON history(visited_at)")
        createBookmarks(db)
        createBlockLog(db)
        createDownloads(db)
        createWorkspaceTables(db)
    }

    // جديد (v2): سجل الحجب. نخزن اسم المضيف فقط (بدون مسارات أو معاملات) حفاظاً على الخصوصية
    private fun createBlockLog(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE block_log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, " +
                "reason TEXT NOT NULL, blocked_host TEXT NOT NULL, page_host TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_block_log_ts ON block_log(ts)")
    }

    // v4: المفضلة صارت لكل مساحة عمل (الفريد هو الزوج workspace_id + url)
    private fun createBookmarks(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, workspace_id INTEGER NOT NULL DEFAULT 1, " +
                "url TEXT NOT NULL, title TEXT, created_at INTEGER NOT NULL, UNIQUE(workspace_id, url))"
        )
    }

    // v4: مساحات العمل، الاختصارات، مجموعات المواقع
    private fun createWorkspaceTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workspaces (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT NOT NULL)")
        // المساحة الافتراضية رقمها 1 دائماً ولا تُحذف
        db.insert("workspaces", null, ContentValues().apply {
            put("id", 1)
            put("name", appContext.getString(com.vayro.browser.R.string.workspace_default))
            put("emoji", "🏠")
        })
        db.execSQL("CREATE TABLE shortcuts (id INTEGER PRIMARY KEY AUTOINCREMENT, workspace_id INTEGER NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL)")
        db.execSQL("CREATE TABLE site_groups (id INTEGER PRIMARY KEY AUTOINCREMENT, workspace_id INTEGER NOT NULL, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE site_group_items (id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, url TEXT NOT NULL, title TEXT)")
    }

    // جديد (v3): جدول التنزيلات (الملفات نفسها تُحفظ في Download/VAYRO وليس في القاعدة)
    private fun createDownloads(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, referrer TEXT, " +
                "file_name TEXT NOT NULL, mime TEXT NOT NULL, category TEXT NOT NULL, total_bytes INTEGER NOT NULL, " +
                "downloaded_bytes INTEGER NOT NULL, status TEXT NOT NULL, resumable INTEGER NOT NULL, " +
                "created_at INTEGER NOT NULL, uri TEXT, error TEXT)"
        )
    }

    // الترقية من v1 تضيف جدول سجل الحجب فقط دون المساس ببيانات المستخدم
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createBlockLog(db)
        if (oldVersion < 3) createDownloads(db)
        if (oldVersion < 4) {
            createWorkspaceTables(db)
            // إعادة بناء جدول المفضلة مع نقل بيانات المستخدم كلها إلى المساحة الافتراضية
            db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_old")
            createBookmarks(db)
            db.execSQL(
                "INSERT INTO bookmarks (id, workspace_id, url, title, created_at) " +
                    "SELECT id, 1, url, title, created_at FROM bookmarks_old"
            )
            db.execSQL("DROP TABLE bookmarks_old")
        }
    }
}

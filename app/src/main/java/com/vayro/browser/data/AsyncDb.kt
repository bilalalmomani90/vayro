package com.vayro.browser.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/** ينفذ عمليات قاعدة البيانات على خيط خلفي واحد (يحفظ الترتيب) ويعيد النتائج على خيط الواجهة. */
object AsyncDb {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** كتابة بلا نتيجة. */
    fun run(work: () -> Unit) {
        executor.execute {
            try { work() } catch (e: Exception) { Log.w("AsyncDb", "write failed", e) }
        }
    }

    /** قراءة/كتابة بنتيجة تُسلَّم على خيط الواجهة. */
    fun <T> query(work: () -> T, done: (T) -> Unit) {
        executor.execute {
            val result = try { work() } catch (e: Exception) {
                Log.w("AsyncDb", "query failed", e)
                return@execute
            }
            main.post { done(result) }
        }
    }
}

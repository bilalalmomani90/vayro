package com.vayro.browser.ui

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vayro.browser.R
import com.vayro.browser.VayroApplication

/**
 * اقتراحات العنوان أثناء الكتابة من المفضلة والسجل المحليين فقط.
 * لا يُرسل ما تكتبه إلى أي خادم (اقتراحات البحث عبر الإنترنت غير مفعّلة عمداً حفاظاً على الخصوصية).
 */
class SuggestionController(
    private val activity: AppCompatActivity,
    private val app: VayroApplication,
    private val input: EditText,
    private val anchor: View,
    private val onPick: (String) -> Unit
) {
    data class Suggestion(val title: String, val url: String)

    private val handler = Handler(Looper.getMainLooper())
    private var popup: ListPopupWindow? = null
    private var query = ""

    private val adapter = object : ArrayAdapter<Suggestion>(activity, 0) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_suggestion, parent, false)
            val item = getItem(position)!!
            view.findViewById<TextView>(R.id.suggestionTitle).text = item.title.ifBlank { item.url }
            view.findViewById<TextView>(R.id.suggestionUrl).text = item.url
            return view
        }
    }

    private val search = Runnable { lookup(query) }

    init {
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // التحديثات البرمجية للعنوان (بلا تركيز) لا تُظهر اقتراحات
                if (!input.hasFocus()) return
                query = s?.toString()?.trim().orEmpty()
                handler.removeCallbacks(search)
                if (query.length < MIN_CHARS) dismiss() else handler.postDelayed(search, DEBOUNCE_MS)
            }
        })
        input.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) dismiss() }
    }

    private fun lookup(q: String) {
        app.bookmarks.search(q, MAX_EACH) { bookmarks ->
            app.history.search(q, MAX_EACH) historyResult@{ history ->
                // تجاهل نتيجة متأخرة إن تغيّر النص أو فقد الحقل التركيز
                if (!input.hasFocus() || q != query) return@historyResult
                val merged = (bookmarks + history).distinctBy { it.url }.take(MAX_TOTAL)
                    .map { Suggestion(it.title.orEmpty(), it.url) }
                show(merged)
            }
        }
    }

    private fun show(list: List<Suggestion>) {
        if (list.isEmpty()) {
            dismiss()
            return
        }
        adapter.clear()
        adapter.addAll(list)
        val p = popup ?: createPopup().also { popup = it }
        p.width = anchor.width
        if (!p.isShowing) p.show()
    }

    private fun createPopup() = ListPopupWindow(activity).apply {
        anchorView = anchor
        setAdapter(adapter)
        inputMethodMode = ListPopupWindow.INPUT_METHOD_NEEDED // لا نُخفي لوحة المفاتيح
        setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.bg_home_card))
        setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let {
                dismiss()
                onPick(it.url)
            }
        }
    }

    fun dismiss() {
        handler.removeCallbacks(search)
        popup?.dismiss()
    }

    private companion object {
        const val MIN_CHARS = 2
        const val DEBOUNCE_MS = 150L
        const val MAX_EACH = 4
        const val MAX_TOTAL = 6
    }
}

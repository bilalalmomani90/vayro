package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.data.Entry
import com.vayro.browser.databinding.SheetLibraryBinding

/** نافذة السجل والمفضلة (تبويبان داخلها). */
class LibraryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetLibraryBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication
    private lateinit var adapter: RowAdapter
    private var showingBookmarks = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showingBookmarks = arguments?.getBoolean(ARG_BOOKMARKS) ?: false

        adapter = RowAdapter(
            actionIcon = R.drawable.ic_delete,
            actionDescription = R.string.delete_entry,
            // الرابط محفوظ في subtitle
            onClick = { row ->
                app.tabManager.loadInSelected(row.subtitle)
                dismiss()
            },
            onAction = { row ->
                if (showingBookmarks) app.bookmarks.delete(row.id) else app.history.delete(row.id)
                load() // العمليات تُنفَّذ بالترتيب على خيط واحد فيعيد الاستعلام النتيجة المحدّثة
            }
        )
        binding.rowsList.layoutManager = LinearLayoutManager(requireContext())
        binding.rowsList.adapter = adapter

        binding.libraryTabs.getTabAt(if (showingBookmarks) 1 else 0)?.select()
        binding.libraryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingBookmarks = tab.position == 1
                load()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.clearHistoryButton.setOnClickListener {
            app.history.clear()
            load()
        }
        load()
    }

    private fun load() {
        binding.clearHistoryButton.visibility = if (showingBookmarks) View.GONE else View.VISIBLE
        if (showingBookmarks) app.bookmarks.all { render(it) } else app.history.recent { render(it) }
    }

    private fun render(entries: List<Entry>) {
        if (!isAdded || _binding == null) return // النافذة أُغلقت قبل وصول النتيجة
        adapter.submit(entries.map { Row(it.id, it.title?.takeIf { t -> t.isNotBlank() } ?: it.url, it.url) })
        binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.setText(if (showingBookmarks) R.string.library_bookmarks_empty else R.string.library_history_empty)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_BOOKMARKS = "bookmarks"
        fun newInstance(showBookmarks: Boolean) =
            LibraryBottomSheet().apply { arguments = bundleOf(ARG_BOOKMARKS to showBookmarks) }
    }
}

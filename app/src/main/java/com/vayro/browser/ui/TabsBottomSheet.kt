package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.databinding.SheetTabsBinding

/** نافذة إدارة التبويبات: عرض، تبديل، إغلاق، وفتح تبويب جديد. */
class TabsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTabsBinding? = null
    private val binding get() = _binding!!
    private val tabManager get() = (requireActivity().application as VayroApplication).tabManager
    private lateinit var adapter: RowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RowAdapter(
            actionIcon = R.drawable.ic_close,
            actionDescription = R.string.close_tab,
            onClick = { row ->
                tabManager.tabs.find { it.id == row.id }?.let { tabManager.select(it) }
                dismiss()
            },
            onAction = { row ->
                tabManager.tabs.find { it.id == row.id }?.let { tabManager.close(it) }
                refresh() // تحديث القائمة بعد الإغلاق
            }
        )
        binding.rowsList.layoutManager = LinearLayoutManager(requireContext())
        binding.rowsList.adapter = adapter
        binding.newTabButton.setOnClickListener {
            tabManager.newTab()
            dismiss()
        }
        refresh()
    }

    private fun refresh() {
        val tabs = tabManager.tabs
        binding.sheetTitle.text = getString(R.string.tabs_title_count, tabs.size)
        adapter.submit(tabs.map { tab ->
            Row(
                id = tab.id,
                title = (if (tab.isPrivate) "🕶 " else "") +
                    (tab.title?.takeIf { it.isNotBlank() } ?: tab.url ?: getString(R.string.new_tab)),
                subtitle = tab.url.orEmpty(),
                highlighted = tab === tabManager.selected
            )
        })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

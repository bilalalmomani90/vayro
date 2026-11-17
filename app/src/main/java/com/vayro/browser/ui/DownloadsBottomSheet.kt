package com.vayro.browser.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.databinding.SheetDownloadsBinding
import com.vayro.browser.downloads.DownloadCategory
import com.vayro.browser.downloads.DownloadFilter
import com.vayro.browser.downloads.DownloadItem
import com.vayro.browser.downloads.DownloadStatus
import com.vayro.browser.downloads.FileSafety

/** مدير التنزيلات: قائمة حية مع بحث وتصنيف وأوامر لكل عنصر. */
class DownloadsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDownloadsBinding? = null
    private val binding get() = _binding!!
    private val center get() = (requireActivity().application as VayroApplication).downloads

    private var all: List<DownloadItem> = emptyList()
    private var query = ""
    private var category: DownloadCategory? = null

    private val observer: (List<DownloadItem>) -> Unit = { list ->
        all = list
        if (_binding != null) render()
    }

    private lateinit var adapter: DownloadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DownloadAdapter(::onPrimary, ::onRemove, ::open)
        binding.dlList.layoutManager = LinearLayoutManager(requireContext())
        binding.dlList.itemAnimator = null // تحديثات التقدم المتكررة بلا وميض
        binding.dlList.adapter = adapter

        binding.dlSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                render()
            }
        })

        binding.dlChips.setOnCheckedStateChangeListener { _, ids ->
            category = when (ids.firstOrNull()) {
                R.id.chipDocs -> DownloadCategory.DOCUMENTS
                R.id.chipImages -> DownloadCategory.IMAGES
                R.id.chipVideo -> DownloadCategory.VIDEO
                R.id.chipAudio -> DownloadCategory.AUDIO
                R.id.chipArchives -> DownloadCategory.ARCHIVES
                R.id.chipApps -> DownloadCategory.APPS
                R.id.chipOther -> DownloadCategory.OTHER
                else -> null // الكل
            }
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        center.addObserver(observer) // يستدعي المراقب فوراً بالقائمة الحالية
    }

    override fun onStop() {
        center.removeObserver(observer)
        super.onStop()
    }

    private fun render() {
        val filtered = DownloadFilter.apply(all, query, category)
        adapter.submitList(filtered)
        binding.dlEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onPrimary(item: DownloadItem) {
        when (item.status) {
            DownloadStatus.RUNNING -> center.pause(item.id)
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> center.resume(item.id)
            DownloadStatus.COMPLETED -> open(item)
            DownloadStatus.QUEUED -> Unit
        }
    }

    private fun onRemove(item: DownloadItem) {
        if (item.status == DownloadStatus.COMPLETED) {
            // خياران: إزالة من القائمة فقط، أو حذف الملف نفسه أيضاً
            MaterialAlertDialogBuilder(requireContext())
                .setItems(arrayOf(getString(R.string.dl_remove_from_list), getString(R.string.dl_delete_file))) { _, which ->
                    center.remove(item.id, deleteFile = which == 1)
                }
                .show()
        } else {
            center.cancel(item.id)
        }
    }

    /** فتح الملف؛ الملفات التنفيذية تتطلب تأكيداً ثانياً. */
    private fun open(item: DownloadItem) {
        val risk = FileSafety.assess(item.fileName)
        if (risk == FileSafety.Risk.NONE) {
            launch(item)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dl_risky_title)
                .setMessage(getString(R.string.dl_risky_executable, item.fileName))
                .setPositiveButton(R.string.dl_open_anyway) { _, _ -> launch(item) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun launch(item: DownloadItem) {
        val intent = center.viewIntent(item) ?: return
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.dl_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

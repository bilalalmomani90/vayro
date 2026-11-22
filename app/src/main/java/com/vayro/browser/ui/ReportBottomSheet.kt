package com.vayro.browser.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.BlockReason
import com.vayro.browser.data.BlockEntry
import com.vayro.browser.databinding.SheetReportBinding
import java.util.Calendar

/** تقرير الحماية: أعداد اليوم وآخر 7 أيام (من السجل الفعلي فقط) وسجل الحجب بأسبابه. */
class ReportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetReportBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication
    private val adapter = RowAdapter() // بلا زر إجراء

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rowsList.layoutManager = LinearLayoutManager(requireContext())
        binding.rowsList.adapter = adapter
        binding.clearLogButton.setOnClickListener {
            app.blockLog.clear()
            load()
        }
        load()
    }

    private fun load() {
        val now = System.currentTimeMillis()
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000

        app.blockLog.counts(midnight) { counts ->
            if (_binding != null) binding.todayText.text = summary(R.string.report_today, counts)
        }
        app.blockLog.counts(weekAgo) { counts ->
            if (_binding != null) binding.weekText.text = summary(R.string.report_week, counts)
        }
        app.blockLog.recent { entries ->
            if (_binding == null) return@recent
            adapter.submit(entries.map { toRow(it, now) })
            binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun summary(periodRes: Int, counts: Map<BlockReason, Int>) = getString(
        R.string.report_summary, getString(periodRes),
        counts[BlockReason.AD] ?: 0, counts[BlockReason.TRACKER] ?: 0,
        counts[BlockReason.POPUP] ?: 0, counts[BlockReason.REDIRECT] ?: 0
    )

    /** صف يشرح سبب الحجب ومكانه ووقته. */
    private fun toRow(e: BlockEntry, now: Long): Row {
        val reasonRes = when (e.reason) {
            BlockReason.AD -> R.string.block_row_ad
            BlockReason.TRACKER -> R.string.block_row_tracker
            BlockReason.POPUP -> R.string.block_row_popup
            BlockReason.REDIRECT -> R.string.block_row_redirect
            BlockReason.BLOCKED -> R.string.block_row_blocked
            BlockReason.DOWNLOAD -> R.string.block_row_download
        }
        val whenText = DateUtils.getRelativeTimeSpanString(e.timestamp, now, DateUtils.MINUTE_IN_MILLIS)
        return Row(
            id = e.timestamp,
            title = e.blockedHost.ifBlank { "—" },
            subtitle = getString(reasonRes, e.pageHost.ifBlank { "—" }) + " · " + whenText
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

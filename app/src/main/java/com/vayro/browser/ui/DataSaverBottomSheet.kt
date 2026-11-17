package com.vayro.browser.ui

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.databinding.SheetDatasaverBinding
import com.vayro.browser.datasaver.DataSaverMode

/** توفير البيانات: الوضع، استثناء الموقع الحالي، والاستهلاك الفعلي المقاس. */
class DataSaverBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDatasaverBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDatasaverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val saver = app.dataSaver
        val host = arguments?.getString(ARG_HOST)

        binding.modeGroup.check(
            when (saver.mode()) {
                DataSaverMode.OFF -> R.id.modeOff
                DataSaverMode.STANDARD -> R.id.modeStandard
                DataSaverMode.STRICT -> R.id.modeStrict
            }
        )
        binding.modeDescription.setText(descriptionOf(saver.mode()))
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.modeStandard -> DataSaverMode.STANDARD
                R.id.modeStrict -> DataSaverMode.STRICT
                else -> DataSaverMode.OFF
            }
            saver.setMode(mode)
            binding.modeDescription.setText(descriptionOf(mode))
        }

        // استثناء الموقع الحالي (يخص حجب الصور فقط)
        if (host == null) {
            binding.siteSwitch.visibility = View.GONE
        } else {
            binding.siteSwitch.isChecked = saver.isFullQuality(host)
            binding.siteSwitch.setOnCheckedChangeListener { _, enabled ->
                saver.setFullQuality(host, enabled)
                app.tabManager.selected.engineOrNull?.reload()
            }
        }

        // الاستهلاك المقاس فعلياً بعدّاد النظام
        val supported = app.dataUsage.sample()
        if (!supported) {
            binding.usageToday.setText(R.string.ds_usage_unavailable)
            binding.usageWeek.visibility = View.GONE
        } else {
            val ctx = requireContext()
            binding.usageToday.text = getString(
                R.string.ds_usage_today, Formatter.formatShortFileSize(ctx, app.dataUsage.bytesForLastDays(1))
            )
            binding.usageWeek.text = getString(
                R.string.ds_usage_week, Formatter.formatShortFileSize(ctx, app.dataUsage.bytesForLastDays(7))
            )
        }
    }

    @StringRes
    private fun descriptionOf(mode: DataSaverMode) = when (mode) {
        DataSaverMode.OFF -> R.string.ds_desc_off
        DataSaverMode.STANDARD -> R.string.ds_desc_standard
        DataSaverMode.STRICT -> R.string.ds_desc_strict
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_HOST = "host"
        fun newInstance(host: String?) = DataSaverBottomSheet().apply { arguments = bundleOf(ARG_HOST to host) }
    }
}

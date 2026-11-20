package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.BlockReason
import com.vayro.browser.databinding.SheetSiteProtectionBinding

/** تخصيص الحماية لموقع واحد: تفعيل/تعطيل الحماية، والسماح بالنوافذ المنبثقة، وملخص الحجب. */
class SiteProtectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSiteProtectionBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSiteProtectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = arguments?.getString(ARG_HOST) ?: run { dismiss(); return }
        val protection = app.protection
        binding.siteHost.text = host

        binding.switchProtection.isChecked = protection.isProtected(host)
        binding.switchPopups.isChecked = protection.popupsAllowed(host)

        binding.switchProtection.setOnCheckedChangeListener { _, enabled ->
            protection.setProtected(host, enabled)
            // استثناء حماية التتبع في المحرك ثم إعادة تحميل الصفحة لتطبيقه
            val engine = app.tabManager.selected.engineOrNull
            engine?.setProtectionException(!enabled)
            engine?.reload()
        }
        binding.switchPopups.setOnCheckedChangeListener { _, allowed ->
            protection.setPopupsAllowed(host, allowed)
        }

        // ملخص آخر 7 أيام لهذا الموقع
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        app.blockLog.counts(weekAgo, host) { counts ->
            if (_binding == null) return@counts
            binding.siteSummary.text = getString(
                R.string.site_summary,
                counts[BlockReason.AD] ?: 0, counts[BlockReason.TRACKER] ?: 0,
                counts[BlockReason.POPUP] ?: 0, counts[BlockReason.REDIRECT] ?: 0
            )
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_HOST = "host"
        fun newInstance(host: String) = SiteProtectionBottomSheet().apply { arguments = bundleOf(ARG_HOST to host) }
    }
}

package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.CookiePolicy
import com.vayro.browser.core.ProtectionLevel
import com.vayro.browser.databinding.SheetPrivacyBinding

/** الإعدادات العامة للخصوصية: مستوى الحماية، الكوكيز، ومسح البيانات. */
class PrivacyBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetPrivacyBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val protection = app.protection

        // الحالة الحالية أولاً، ثم نضيف المستمع كي لا يُستدعى أثناء التهيئة
        binding.levelGroup.check(
            when (protection.level()) {
                ProtectionLevel.NORMAL -> R.id.levelNormal
                ProtectionLevel.ADVANCED -> R.id.levelAdvanced
                ProtectionLevel.MAX -> R.id.levelMax
            }
        )
        binding.levelDescription.setText(descriptionOf(protection.level()))
        binding.levelGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val level = when (checkedId) {
                R.id.levelAdvanced -> ProtectionLevel.ADVANCED
                R.id.levelMax -> ProtectionLevel.MAX
                else -> ProtectionLevel.NORMAL
            }
            protection.setLevel(level)
            binding.levelDescription.setText(descriptionOf(level))
        }

        binding.cookieGroup.check(
            when (protection.cookiePolicy()) {
                CookiePolicy.ALLOW_ALL -> R.id.cookieAll
                CookiePolicy.BLOCK_TRACKERS -> R.id.cookieTrackers
                CookiePolicy.BLOCK_THIRD_PARTY -> R.id.cookieThirdParty
            }
        )
        binding.cookieGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            protection.setCookiePolicy(
                when (checkedId) {
                    R.id.cookieAll -> CookiePolicy.ALLOW_ALL
                    R.id.cookieThirdParty -> CookiePolicy.BLOCK_THIRD_PARTY
                    else -> CookiePolicy.BLOCK_TRACKERS
                }
            )
        }

        binding.clearHistoryNow.setOnClickListener {
            confirm(R.string.confirm_clear_history) { app.history.clear() }
        }
        binding.clearSiteDataButton.setOnClickListener {
            confirm(R.string.confirm_clear_site_data) { app.clearSiteData() }
        }
    }

    @StringRes
    private fun descriptionOf(level: ProtectionLevel) = when (level) {
        ProtectionLevel.NORMAL -> R.string.level_desc_normal
        ProtectionLevel.ADVANCED -> R.string.level_desc_advanced
        ProtectionLevel.MAX -> R.string.level_desc_max
    }

    /** حوار تأكيد قبل أي مسح لا رجعة فيه. */
    private fun confirm(@StringRes message: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                action()
                Toast.makeText(requireContext(), R.string.done_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

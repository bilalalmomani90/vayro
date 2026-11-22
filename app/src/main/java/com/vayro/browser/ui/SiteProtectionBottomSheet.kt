package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.BlockReason
import com.vayro.browser.core.SiteTrust
import com.vayro.browser.core.TrustLevel
import com.vayro.browser.core.TrustReason
import com.vayro.browser.databinding.SheetSiteProtectionBinding

/** حماية الموقع: بطاقة الثقة، زر «أنقذني»، تفعيل/تعطيل الحماية، النوافذ، الحظر، وملخص الحجب. */
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
        val url = arguments?.getString(ARG_URL)
        binding.siteHost.text = host

        bindSwitches(host, url)

        // «أنقذني من هذا الموقع»: أقوى حماية ممكنة لهذا الموقع ثم إعادة التحميل
        binding.rescueButton.setOnClickListener {
            val protection = app.protection
            protection.setProtected(host, true)
            protection.setPopupsAllowed(host, false)
            protection.setStrict(host, true) // نوافذ وتحويلات وتنزيلات بقواعد "أقصى حماية"
            val engine = app.tabManager.selected.engineOrNull
            engine?.setProtectionException(false)
            engine?.reload()
            bindSwitches(host, url)
            refreshTrust(host, url)
            Toast.makeText(requireContext(), R.string.rescue_done, Toast.LENGTH_LONG).show()
        }

        refreshTrust(host, url)
    }

    /**
     * يضبط حالة المفاتيح من الإعدادات المحفوظة. نفصل المستمعات أولاً كي لا يُعدّ التغيير البرمجي
     * ضغطة مستخدم، ثم نعيدها بعد ضبط الحالة.
     */
    private fun bindSwitches(host: String, url: String?) {
        val protection = app.protection
        binding.switchProtection.setOnCheckedChangeListener(null)
        binding.switchPopups.setOnCheckedChangeListener(null)
        binding.switchBlock.setOnCheckedChangeListener(null)

        binding.switchProtection.isChecked = protection.isProtected(host)
        binding.switchPopups.isChecked = protection.popupsAllowed(host)
        binding.switchBlock.isChecked = host in protection.blockedHosts()

        binding.switchProtection.setOnCheckedChangeListener { _, enabled ->
            protection.setProtected(host, enabled)
            // استثناء حماية التتبع في المحرك ثم إعادة تحميل الصفحة لتطبيقه
            val engine = app.tabManager.selected.engineOrNull
            engine?.setProtectionException(!enabled)
            engine?.reload()
            refreshTrust(host, url)
        }
        // حظر الموقع كله: أي تنقل إليه لاحقاً يُمنع مع إمكانية السماح لمرة واحدة
        binding.switchBlock.setOnCheckedChangeListener { _, blocked ->
            protection.setBlocked(host, blocked)
            refreshTrust(host, url)
        }
        binding.switchPopups.setOnCheckedChangeListener { _, allowed -> protection.setPopupsAllowed(host, allowed) }
    }

    /** يحسب بطاقة الثقة من الإشارات المحلية ويعرضها (الإحصاء لآخر 7 أيام لهذا الموقع). */
    private fun refreshTrust(host: String, url: String?) {
        val protection = app.protection
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        app.blockLog.counts(weekAgo, host) { counts ->
            if (_binding == null) return@counts
            val result = SiteTrust.assess(
                url = url,
                userBlocked = host in protection.blockedHosts(),
                protectionOn = protection.isProtected(host),
                redirectsBlockedThisWeek = counts[BlockReason.REDIRECT] ?: 0,
                popupsBlockedThisWeek = counts[BlockReason.POPUP] ?: 0
            )
            val (labelRes, colorRes, textColorRes) = when (result.level) {
                TrustLevel.GREEN -> Triple(R.string.trust_green, R.color.vayro_success, R.color.vayro_dark_bg)
                TrustLevel.YELLOW -> Triple(R.string.trust_yellow, R.color.vayro_warning, R.color.vayro_dark_bg)
                TrustLevel.RED -> Triple(R.string.trust_red, R.color.vayro_danger, R.color.white)
            }
            binding.trustBadge.setText(labelRes)
            binding.trustBadge.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
            binding.trustBadge.background.mutate().setTint(ContextCompat.getColor(requireContext(), colorRes))
            binding.trustReasons.text = result.reasons.joinToString("\n") { "• " + getString(reasonText(it)) }

            binding.siteSummary.text = getString(
                R.string.site_summary,
                counts[BlockReason.AD] ?: 0, counts[BlockReason.TRACKER] ?: 0,
                counts[BlockReason.POPUP] ?: 0, counts[BlockReason.REDIRECT] ?: 0
            )
        }
    }

    private fun reasonText(reason: TrustReason) = when (reason) {
        TrustReason.USER_BLOCKED -> R.string.trust_r_user_blocked
        TrustReason.NOT_ENCRYPTED -> R.string.trust_r_not_encrypted
        TrustReason.MANY_REDIRECTS -> R.string.trust_r_redirects
        TrustReason.MANY_POPUPS -> R.string.trust_r_popups
        TrustReason.PROTECTION_OFF -> R.string.trust_r_protection_off
        TrustReason.NONE -> R.string.trust_r_none
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_HOST = "host"
        private const val ARG_URL = "url"
        fun newInstance(host: String, url: String?) =
            SiteProtectionBottomSheet().apply { arguments = bundleOf(ARG_HOST to host, ARG_URL to url) }
    }
}

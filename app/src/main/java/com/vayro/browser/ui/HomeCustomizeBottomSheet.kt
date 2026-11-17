package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.databinding.ItemSectionSettingBinding
import com.vayro.browser.databinding.SheetHomeCustomizeBinding
import com.vayro.browser.home.CardSize
import com.vayro.browser.home.HomeBackground
import com.vayro.browser.home.HomeLayout
import com.vayro.browser.home.HomeSection

/** تخصيص الصفحة الرئيسية: ترتيب الأقسام وإظهارها، حجم البطاقات، الخلفية، وأزرار الشريط. */
class HomeCustomizeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetHomeCustomizeBinding? = null
    private val binding get() = _binding!!
    private val app get() = requireActivity().application as VayroApplication

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHomeCustomizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val s = app.homeSettings.load()

        // الحالة الحالية أولاً ثم المستمعات كي لا تُستدعى أثناء التهيئة
        binding.sizeGroup.check(
            when (s.cardSize) { CardSize.COMPACT -> R.id.sizeCompact; CardSize.NORMAL -> R.id.sizeNormal; CardSize.LARGE -> R.id.sizeLarge }
        )
        binding.sizeGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            change { it.copy(cardSize = when (id) { R.id.sizeCompact -> CardSize.COMPACT; R.id.sizeLarge -> CardSize.LARGE; else -> CardSize.NORMAL }) }
        }

        binding.bgGroup.check(
            when (s.background) {
                HomeBackground.DEFAULT -> R.id.bgDefault; HomeBackground.VIOLET -> R.id.bgViolet
                HomeBackground.CYAN -> R.id.bgCyan; HomeBackground.DEEP -> R.id.bgDeep
            }
        )
        binding.bgGroup.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            change {
                it.copy(
                    background = when (id) {
                        R.id.bgViolet -> HomeBackground.VIOLET; R.id.bgCyan -> HomeBackground.CYAN
                        R.id.bgDeep -> HomeBackground.DEEP; else -> HomeBackground.DEFAULT
                    }
                )
            }
        }

        binding.toolbarReload.isChecked = s.showReload
        binding.toolbarStar.isChecked = s.showStar
        binding.toolbarReload.setOnCheckedChangeListener { _, on -> change { it.copy(showReload = on) } }
        binding.toolbarStar.setOnCheckedChangeListener { _, on -> change { it.copy(showStar = on) } }

        renderSections()
    }

    private fun change(transform: (com.vayro.browser.home.HomeSettings) -> com.vayro.browser.home.HomeSettings) {
        app.homeSettings.update(transform)
        app.notifyHomeChanged()
    }

    /** صف لكل قسم: اسم، أعلى/أسفل، وإظهار/إخفاء. */
    private fun renderSections() {
        val settings = app.homeSettings.load()
        binding.sectionsContainer.removeAllViews()
        settings.order.forEach { section ->
            val row = ItemSectionSettingBinding.inflate(layoutInflater, binding.sectionsContainer, false)
            row.sectionName.setText(nameOf(section))
            row.sectionSwitch.isChecked = section !in settings.hidden
            row.sectionSwitch.setOnCheckedChangeListener { _, on ->
                change { it.copy(hidden = if (on) it.hidden - section else it.hidden + section) }
            }
            row.sectionUp.setOnClickListener { move(section, -1) }
            row.sectionDown.setOnClickListener { move(section, +1) }
            binding.sectionsContainer.addView(row.root)
        }
    }

    private fun move(section: HomeSection, delta: Int) {
        change { it.copy(order = HomeLayout.move(it.order, section, delta)) }
        renderSections()
    }

    private fun nameOf(section: HomeSection) = when (section) {
        HomeSection.SHORTCUTS -> R.string.section_shortcuts
        HomeSection.GROUPS -> R.string.section_groups
        HomeSection.BOOKMARKS -> R.string.section_bookmarks
        HomeSection.RECENT -> R.string.section_recent
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

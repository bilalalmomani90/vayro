package com.vayro.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.databinding.SheetWorkspacesBinding
import com.vayro.browser.workspace.WorkspaceManager

/** مساحات العمل: عرض، تبديل، إنشاء (اسم + أيقونة)، وحذف. */
class WorkspacesBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetWorkspacesBinding? = null
    private val binding get() = _binding!!
    private val manager get() = (requireActivity().application as VayroApplication).workspaces
    private lateinit var adapter: RowAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetWorkspacesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RowAdapter(
            actionIcon = R.drawable.ic_delete,
            actionDescription = R.string.dl_action_delete,
            onClick = { row ->
                manager.switchTo(row.id)
                dismiss()
            },
            onAction = { row -> confirmDelete(row.id, row.title) }
        )
        binding.rowsList.layoutManager = LinearLayoutManager(requireContext())
        binding.rowsList.adapter = adapter
        binding.newWorkspaceButton.setOnClickListener { newWorkspaceDialog() }
        load()
    }

    private fun load() {
        manager.all { list ->
            if (_binding == null) return@all
            adapter.submit(list.map { ws ->
                Row(ws.id, "${ws.emoji} ${ws.name}", "", highlighted = ws.id == manager.currentId)
            })
        }
    }

    private fun confirmDelete(id: Long, title: String) {
        if (id == WorkspaceManager.DEFAULT_ID) {
            Toast.makeText(requireContext(), R.string.workspace_default_protected, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.workspace_delete_confirm, title))
            .setPositiveButton(R.string.dl_action_delete) { _, _ ->
                manager.delete(id)
                load()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun newWorkspaceDialog() {
        val ctx = requireContext()
        val name = EditText(ctx).apply { setHint(R.string.field_workspace_name); setSingleLine() }
        // اختيار أيقونة (إيموجي) واحدة من قائمة جاهزة
        val chips = ChipGroup(ctx).apply { isSingleSelection = true; isSelectionRequired = true }
        EMOJIS.forEachIndexed { index, emoji ->
            chips.addView(Chip(ctx).apply {
                id = View.generateViewId()
                text = emoji
                isCheckable = true
                isChecked = index == 0
            })
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 3, pad, 0)
            addView(name)
            addView(chips)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.workspace_new)
            .setView(content)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val value = name.text.toString().trim()
                if (value.isEmpty()) return@setPositiveButton
                val emoji = (chips.findViewById<Chip>(chips.checkedChipId))?.text?.toString() ?: EMOJIS.first()
                manager.create(value, emoji) { id ->
                    manager.switchTo(id) // ننتقل إلى المساحة الجديدة فوراً
                    if (isAdded) dismiss()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val EMOJIS = listOf("💼", "📚", "🎬", "🎨", "🛒", "✈️", "🏠")
    }
}

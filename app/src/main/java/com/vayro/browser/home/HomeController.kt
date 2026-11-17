package com.vayro.browser.home

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.core.HostUtil
import com.vayro.browser.core.UrlResolver
import com.vayro.browser.data.Entry
import com.vayro.browser.data.Shortcut
import com.vayro.browser.data.SiteGroup
import com.vayro.browser.data.Workspace
import com.vayro.browser.databinding.ActivityMainBinding
import com.vayro.browser.databinding.ItemGroupBinding

/** يبني أقسام الصفحة الرئيسية حسب إعدادات المستخدم وبيانات المساحة الحالية. */
class HomeController(
    private val activity: AppCompatActivity,
    private val app: VayroApplication,
    private val binding: ActivityMainBinding,
    private val onOpenUrl: (String) -> Unit,
    private val onShowWorkspaces: () -> Unit,
    private val onShowCustomize: () -> Unit
) {
    private val inflater = LayoutInflater.from(activity)

    init {
        binding.workspaceLabel.setOnClickListener { onShowWorkspaces() }
        binding.customizeButton.setOnClickListener { onShowCustomize() }
    }

    /** نتائج الاستعلامات الخمسة تصل بشكل غير متزامن؛ نرسم عند وصولها كلها. */
    private class Data {
        var workspaces: List<Workspace> = emptyList()
        var shortcuts: List<Shortcut> = emptyList()
        var groups: List<SiteGroup> = emptyList()
        var bookmarks: List<Entry> = emptyList()
        var recent: List<Entry> = emptyList()
    }

    fun refresh() {
        val data = Data()
        var pending = 5
        fun arrived() {
            if (--pending == 0) render(data)
        }
        app.workspaces.all { data.workspaces = it; arrived() }
        app.shortcuts.all { data.shortcuts = it; arrived() }
        app.siteGroups.all { data.groups = it; arrived() }
        app.bookmarks.all { data.bookmarks = it; arrived() }
        app.history.recent(MAX_TILES) { data.recent = it; arrived() }
    }

    private fun render(data: Data) {
        val settings = app.homeSettings.load()

        binding.homeContainer.setBackgroundResource(
            when (settings.background) {
                HomeBackground.DEFAULT -> 0
                HomeBackground.VIOLET -> R.drawable.bg_home_violet
                HomeBackground.CYAN -> R.drawable.bg_home_cyan
                HomeBackground.DEEP -> R.drawable.bg_home_deep
            }.let { if (it == 0) R.color.vayro_background else it }
        )

        val current = data.workspaces.firstOrNull { it.id == app.workspaces.currentId }
        binding.workspaceLabel.text = activity.getString(
            R.string.home_workspace_label, current?.let { "${it.emoji} ${it.name}" } ?: ""
        )

        val content = binding.homeContent
        content.removeAllViews()
        val sections = HomeLayout.visible(settings)
        if (sections.isEmpty()) {
            content.addView(hint(R.string.home_hint))
            return
        }
        sections.forEach { section ->
            when (section) {
                HomeSection.SHORTCUTS -> {
                    content.addView(header(R.string.section_shortcuts, R.string.action_add_short) { addShortcutDialog() })
                    if (data.shortcuts.isEmpty()) content.addView(hint(R.string.home_shortcuts_empty))
                    else content.addView(
                        grid(
                            data.shortcuts.map { Tile(it.id, it.title, it.url) }, settings.cardSize,
                            onLong = { tile -> confirmDeleteShortcut(tile) }
                        )
                    )
                }
                HomeSection.GROUPS -> {
                    content.addView(header(R.string.section_groups, R.string.action_new_short) { newGroupDialog() })
                    if (data.groups.isEmpty()) content.addView(hint(R.string.home_groups_empty))
                    else data.groups.forEach { content.addView(groupCard(it)) }
                }
                HomeSection.BOOKMARKS -> {
                    content.addView(header(R.string.section_bookmarks, null) {})
                    if (data.bookmarks.isEmpty()) content.addView(hint(R.string.library_bookmarks_empty))
                    else content.addView(grid(data.bookmarks.take(MAX_TILES).map { Tile(it.id, it.title.orEmpty(), it.url) }, settings.cardSize))
                }
                HomeSection.RECENT -> {
                    content.addView(header(R.string.section_recent, null) {})
                    if (data.recent.isEmpty()) content.addView(hint(R.string.library_history_empty))
                    else content.addView(grid(data.recent.map { Tile(it.id, it.title.orEmpty(), it.url) }, settings.cardSize))
                }
            }
        }
    }

    // ---- عناصر الواجهة ----

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    private fun header(titleRes: Int, actionRes: Int?, onAction: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, dp(4))
        }
        row.addView(TextView(activity).apply {
            setText(titleRes)
            setTextColor(activity.getColor(R.color.vayro_text))
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (actionRes != null) {
            row.addView(MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                setText(actionRes)
                setOnClickListener { onAction() }
            })
        }
        return row
    }

    private fun hint(textRes: Int) = TextView(activity).apply {
        setText(textRes)
        setTextColor(activity.getColor(R.color.vayro_text_secondary))
        textSize = 13f
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun grid(tiles: List<Tile>, size: CardSize, onLong: ((Tile) -> Unit)? = null): RecyclerView =
        RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, size.columns)
            isNestedScrollingEnabled = false // التمرير للصفحة كلها لا للشبكة
            adapter = TileAdapter(tiles, size, { onOpenUrl(it.url) }, onLong)
        }

    private fun groupCard(group: SiteGroup): View {
        val b = ItemGroupBinding.inflate(inflater, binding.homeContent, false)
        b.groupName.text = group.name
        b.groupCount.text = activity.getString(R.string.group_sites_count, group.items.size)
        b.groupOpen.setOnClickListener { openGroup(group) }
        b.groupMore.setOnClickListener { anchor ->
            PopupMenu(activity, anchor).apply {
                menuInflater.inflate(R.menu.group_menu, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.group_open_all -> openGroup(group)
                        R.id.group_rename -> renameGroupDialog(group)
                        R.id.group_edit_sites -> editSitesDialog(group)
                        R.id.group_delete -> confirmDeleteGroup(group)
                    }
                    true
                }
                show()
            }
        }
        return b.root
    }

    // ---- أفعال ----

    /** فتح المجموعة كلها بضغطة واحدة (مع تأكيد إذا كثرت المواقع). */
    private fun openGroup(group: SiteGroup) {
        val urls = GroupPlan.urlsToOpen(group.items.map { it.url })
        if (urls.isEmpty()) {
            Toast.makeText(activity, R.string.group_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (urls.size > GroupPlan.CONFIRM_ABOVE) {
            MaterialAlertDialogBuilder(activity)
                .setMessage(activity.getString(R.string.group_open_many, urls.size))
                .setPositiveButton(R.string.group_open_all) { _, _ -> app.tabManager.openAllInNewTabs(urls) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        } else {
            app.tabManager.openAllInNewTabs(urls)
        }
    }

    private fun input(hintRes: Int, initial: String = ""): EditText = EditText(activity).apply {
        setHint(hintRes)
        setText(initial)
        setSingleLine()
    }

    private fun dialogContent(vararg fields: EditText): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            fields.forEach { addView(it) }
        }

    private fun addShortcutDialog() {
        val title = input(R.string.field_title)
        val url = input(R.string.field_url)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_add_shortcut)
            .setView(dialogContent(title, url))
            .setPositiveButton(R.string.action_save) { _, _ ->
                val site = UrlResolver.asSiteUrl(url.text.toString())
                if (site == null) {
                    Toast.makeText(activity, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                } else {
                    val name = title.text.toString().trim().ifBlank { HostUtil.hostOf(site).orEmpty() }
                    app.shortcuts.add(name, site) { app.notifyHomeChanged() }
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteShortcut(tile: Tile) {
        MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.confirm_delete_named, tile.title))
            .setPositiveButton(R.string.dl_action_delete) { _, _ ->
                app.shortcuts.delete(tile.id)
                app.notifyHomeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun newGroupDialog() {
        val name = input(R.string.field_group_name)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_new_group)
            .setView(dialogContent(name))
            .setPositiveButton(R.string.action_save) { _, _ ->
                val value = name.text.toString().trim()
                if (value.isNotEmpty()) app.siteGroups.create(value) { app.notifyHomeChanged() }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun renameGroupDialog(group: SiteGroup) {
        val name = input(R.string.field_group_name, group.name)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.group_rename)
            .setView(dialogContent(name))
            .setPositiveButton(R.string.action_save) { _, _ ->
                val value = name.text.toString().trim()
                if (value.isNotEmpty()) {
                    app.siteGroups.rename(group.id, value)
                    app.notifyHomeChanged()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** قائمة مواقع المجموعة بخيارات؛ ما يُزال تحديده يُحذف عند الحفظ. */
    private fun editSitesDialog(group: SiteGroup) {
        if (group.items.isEmpty()) {
            Toast.makeText(activity, R.string.group_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = group.items.map { it.title?.takeIf { t -> t.isNotBlank() } ?: it.url }.toTypedArray()
        val keep = BooleanArray(labels.size) { true }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.group_edit_sites)
            .setMultiChoiceItems(labels, keep) { _, which, checked -> keep[which] = checked }
            .setPositiveButton(R.string.action_save) { _, _ ->
                group.items.forEachIndexed { i, item -> if (!keep[i]) app.siteGroups.removeItem(item.id) }
                app.notifyHomeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteGroup(group: SiteGroup) {
        MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.confirm_delete_named, group.name))
            .setPositiveButton(R.string.dl_action_delete) { _, _ ->
                app.siteGroups.delete(group.id)
                app.notifyHomeChanged()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private companion object {
        const val MAX_TILES = 8
    }
}

package com.vayro.browser.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.vayro.browser.R
import com.vayro.browser.databinding.ItemRowBinding

/** صف عام يُستخدم في قوائم التبويبات والسجل والمفضلة. */
data class Row(val id: Long, val title: String, val subtitle: String, val highlighted: Boolean = false)

class RowAdapter(
    @DrawableRes private val actionIcon: Int = 0, // 0 = بلا زر إجراء
    @StringRes private val actionDescription: Int = 0,
    private val onClick: (Row) -> Unit = {},
    private val onAction: (Row) -> Unit = {}
) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var rows: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        rows = list
        notifyDataSetChanged()
    }

    class VH(val binding: ItemRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        with(holder.binding) {
            rowTitle.text = row.title
            rowSubtitle.text = row.subtitle
            rowSubtitle.visibility = if (row.subtitle.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            // التبويب المحدد بإطار بنفسجي
            rowRoot.setBackgroundResource(if (row.highlighted) R.drawable.bg_item_selected else R.drawable.bg_home_card)
            if (actionIcon == 0) {
                rowAction.visibility = android.view.View.GONE
            } else {
                rowAction.visibility = android.view.View.VISIBLE
                rowAction.setImageResource(actionIcon)
                rowAction.contentDescription = root.context.getString(actionDescription)
            }
            rowRoot.setOnClickListener { onClick(row) }
            rowAction.setOnClickListener { onAction(row) }
        }
    }

    override fun getItemCount(): Int = rows.size
}

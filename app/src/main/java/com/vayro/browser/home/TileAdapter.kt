package com.vayro.browser.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vayro.browser.core.HostUtil
import com.vayro.browser.databinding.ItemTileBinding

/** بطاقة موقع في الشبكة (اختصار أو مفضلة أو من السجل). */
data class Tile(val id: Long, val title: String, val url: String)

class TileAdapter(
    private val tiles: List<Tile>,
    private val size: CardSize,
    private val onClick: (Tile) -> Unit,
    private val onLongClick: ((Tile) -> Unit)? = null
) : RecyclerView.Adapter<TileAdapter.VH>() {

    class VH(val binding: ItemTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tile = tiles[position]
        val host = HostUtil.hostOf(tile.url).orEmpty()
        val label = tile.title.ifBlank { host }
        with(holder.binding) {
            tileLetter.text = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
            tileTitle.text = label
            tileHost.text = host
            // حجم البطاقة: مضغوط بلا سطر المضيف، كبير بحرف أكبر
            val density = root.resources.displayMetrics.density
            val letterDp = when (size) { CardSize.COMPACT -> 36; CardSize.NORMAL -> 44; CardSize.LARGE -> 56 }
            tileLetter.layoutParams = tileLetter.layoutParams.apply {
                width = (letterDp * density).toInt()
                height = (letterDp * density).toInt()
            }
            tileHost.visibility = if (size == CardSize.COMPACT) View.GONE else View.VISIBLE
            tileTitle.textSize = if (size == CardSize.LARGE) 14f else 12f
            tileRoot.setOnClickListener { onClick(tile) }
            tileRoot.setOnLongClickListener {
                onLongClick?.invoke(tile)
                onLongClick != null
            }
        }
    }

    override fun getItemCount() = tiles.size
}

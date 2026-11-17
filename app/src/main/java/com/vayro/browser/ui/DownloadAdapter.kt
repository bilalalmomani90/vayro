package com.vayro.browser.ui

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vayro.browser.R
import com.vayro.browser.databinding.ItemDownloadBinding
import com.vayro.browser.downloads.DownloadItem
import com.vayro.browser.downloads.DownloadStatus

/** قائمة التنزيلات: حالة، تقدم، سرعة، وأزرار الإيقاف/الاستكمال/إعادة المحاولة/الفتح/الحذف. */
class DownloadAdapter(
    private val onPrimary: (DownloadItem) -> Unit,
    private val onRemove: (DownloadItem) -> Unit,
    private val onOpen: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadAdapter.VH>(Diff) {

    class VH(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        fun size(bytes: Long) = Formatter.formatShortFileSize(ctx, bytes)

        with(holder.binding) {
            dlName.text = item.fileName
            val known = item.totalBytes > 0
            dlStatus.text = when (item.status) {
                DownloadStatus.QUEUED -> ctx.getString(R.string.dl_status_queued)
                DownloadStatus.RUNNING ->
                    if (known) ctx.getString(R.string.dl_status_running, size(item.downloadedBytes), size(item.totalBytes), size(item.speedBps))
                    else ctx.getString(R.string.dl_status_running_unknown, size(item.downloadedBytes), size(item.speedBps))
                DownloadStatus.PAUSED ->
                    if (known) ctx.getString(R.string.dl_status_paused, size(item.downloadedBytes), size(item.totalBytes))
                    else ctx.getString(R.string.dl_status_paused_unknown, size(item.downloadedBytes))
                DownloadStatus.COMPLETED -> ctx.getString(R.string.dl_status_completed, size(maxOf(item.totalBytes, item.downloadedBytes)))
                DownloadStatus.FAILED -> ctx.getString(R.string.dl_status_failed, item.error ?: "?")
            }

            // شريط التقدم للتنزيلات غير المكتملة فقط
            val inProgress = item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.QUEUED ||
                item.status == DownloadStatus.PAUSED
            dlProgress.visibility = if (inProgress) View.VISIBLE else View.GONE
            if (inProgress) {
                val indeterminate = !known && item.status == DownloadStatus.RUNNING
                if (dlProgress.isIndeterminate != indeterminate) dlProgress.isIndeterminate = indeterminate
                if (!indeterminate) {
                    val pct = if (known) (item.downloadedBytes * 100 / item.totalBytes).toInt().coerceIn(0, 100) else 0
                    dlProgress.setProgressCompat(pct, false)
                }
            }

            // الزر الأساسي حسب الحالة (لا يظهر زر إيقاف إن لم يؤكد الخادم دعم الاستكمال)
            val primary: Pair<Int, Int>? = when (item.status) {
                DownloadStatus.RUNNING -> if (item.resumable) R.drawable.ic_pause to R.string.dl_action_pause else null
                DownloadStatus.PAUSED -> R.drawable.ic_play to R.string.dl_action_resume
                DownloadStatus.FAILED -> R.drawable.ic_reload to R.string.dl_action_retry
                DownloadStatus.COMPLETED -> R.drawable.ic_open to R.string.dl_action_open
                DownloadStatus.QUEUED -> null
            }
            if (primary == null) {
                dlPrimary.visibility = View.GONE
            } else {
                dlPrimary.visibility = View.VISIBLE
                dlPrimary.setImageResource(primary.first)
                dlPrimary.contentDescription = ctx.getString(primary.second)
            }
            val done = item.status == DownloadStatus.COMPLETED || item.status == DownloadStatus.FAILED
            dlRemove.setImageResource(if (done) R.drawable.ic_delete else R.drawable.ic_close)
            dlRemove.contentDescription = ctx.getString(if (done) R.string.dl_action_delete else R.string.dl_action_cancel)

            dlPrimary.setOnClickListener { onPrimary(item) }
            dlRemove.setOnClickListener { onRemove(item) }
            root.setOnClickListener { if (item.status == DownloadStatus.COMPLETED) onOpen(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(a: DownloadItem, b: DownloadItem) = a.id == b.id
        override fun areContentsTheSame(a: DownloadItem, b: DownloadItem) = a == b
    }
}

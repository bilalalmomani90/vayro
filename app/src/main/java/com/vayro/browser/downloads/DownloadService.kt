package com.vayro.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vayro.browser.R
import com.vayro.browser.VayroApplication
import com.vayro.browser.ui.MainActivity

/** خدمة أمامية تعرض إشعار التنزيل الجاري وتبقي العملية حية، وتتوقف حين لا يبقى تنزيل نشط. */
class DownloadService : Service() {

    private val center get() = (application as VayroApplication).downloads
    private val observer: (List<DownloadItem>) -> Unit = { items -> refresh(items) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        // يجب استدعاء startForeground فوراً بعد startForegroundService
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(emptyList()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        center.removeObserver(observer)
        center.addObserver(observer)
        return START_NOT_STICKY
    }

    private fun refresh(items: List<DownloadItem>) {
        val active = items.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
        if (active.isEmpty()) {
            center.removeObserver(observer)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            center.onServiceStopped()
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(active: List<DownloadItem>): Notification {
        val known = active.filter { it.totalBytes > 0 }
        val total = known.sumOf { it.totalBytes }
        val done = known.sumOf { it.downloadedBytes }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.dl_notification_title, active.size.coerceAtLeast(1)))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                )
            )
        // شريط التقدم الكلي إن كانت الأحجام معروفة، وإلا غير محدد
        if (total > 0) builder.setProgress(100, (done * 100 / total).toInt().coerceIn(0, 100), false)
        else builder.setProgress(0, 0, true)
        return builder.build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.dl_channel_name), NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        center.removeObserver(observer)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 1001
    }
}

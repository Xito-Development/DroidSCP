package com.droidscp.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifs {
    private const val CHANNEL = "droidscp_transfers"
    const val ID = 1001

    private fun mgr(ctx: Context): NotificationManager {
        val m = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m.getNotificationChannel(CHANNEL) == null) {
            m.createNotificationChannel(
                NotificationChannel(CHANNEL, "Transferencias", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return m
    }

    fun progress(ctx: Context, title: String, text: String, percent: Int) {
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, percent.coerceIn(0, 100), false)
                .build()
            mgr(ctx).notify(ID, n)
        } catch (_: Exception) {}
    }

    fun done(ctx: Context, text: String) {
        try {
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("DroidSCP")
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            mgr(ctx).notify(ID, n)
        } catch (_: Exception) {}
    }

    fun cancel(ctx: Context) {
        try { mgr(ctx).cancel(ID) } catch (_: Exception) {}
    }
}

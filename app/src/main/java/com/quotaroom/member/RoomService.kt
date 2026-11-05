package com.quotaroom.member

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.TrafficStats
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RoomState(
    val connected: Boolean = false,
    val used: Long = 0,
    val quota: Long = 0,
    val blocked: Boolean = false,
    val error: String? = null,
)

/** Keeps reporting usage to the admin in the background and turns the cut-off VPN on or off. */
class RoomService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var vpnOn = false

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Quota Room", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val base = intent?.getStringExtra(EXTRA_BASE) ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return START_NOT_STICKY
        startForeground(NOTIF_ID, notification("Connected"))
        job?.cancel()
        job = scope.launch { loop(base, token) }
        return START_STICKY
    }

    private suspend fun loop(base: String, token: String) {
        var last = totalBytes()
        while (scope.isActive) {
            try {
                val now = totalBytes()
                val delta = (now - last).coerceAtLeast(0)
                last = now
                val r = RoomApi.report(base, token, delta)
                state.value = RoomState(true, r.used, r.quota, r.blocked)
                setVpn(r.blocked)
                val pct = if (r.quota > 0) (r.used * 100 / r.quota) else 100
                updateNotif(if (r.blocked) "Quota used up, internet cut" else "Used $pct%")
            } catch (e: Exception) {
                state.value = state.value.copy(connected = false, error = e.message)
            }
            delay(15_000)
        }
    }

    private fun totalBytes(): Long {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        return (if (rx < 0) 0 else rx) + (if (tx < 0) 0 else tx)
    }

    private fun setVpn(blocked: Boolean) {
        if (blocked == vpnOn) return
        vpnOn = blocked
        val i = Intent(this, QuotaVpnService::class.java)
        if (blocked) startService(i) else startService(i.setAction(QuotaVpnService.ACTION_OFF))
    }

    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Quota Room")
            .setContentText(text)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        job?.cancel()
        if (vpnOn) startService(Intent(this, QuotaVpnService::class.java).setAction(QuotaVpnService.ACTION_OFF))
        vpnOn = false
        state.value = RoomState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.quotaroom.member.STOP"
        const val EXTRA_BASE = "base"
        const val EXTRA_TOKEN = "token"
        private const val CHANNEL = "room"
        private const val NOTIF_ID = 1
        val state = MutableStateFlow(RoomState())
    }
}

package com.quotaroom.member

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * Local VPN used to cut the phone's internet once the quota is used up.
 * Every packet from the device is read and dropped, so nothing goes in or out.
 * Our own app is excluded from the tunnel so it can keep talking to the admin.
 */
class QuotaVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_OFF) {
            stopCutting()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) startCutting()
        return START_STICKY
    }

    private fun startCutting() {
        tun = Builder()
            .setSession("Quota Room")
            .addAddress("10.111.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication(packageName)
            .establish()
        val fd = tun ?: return
        reader = Thread {
            val input = FileInputStream(fd.fileDescriptor)
            val buf = ByteArray(32767)
            try {
                while (input.read(buf) >= 0) {
                    // drop everything on purpose
                }
            } catch (_: Exception) {
                // tunnel closed
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun stopCutting() {
        reader?.interrupt()
        reader = null
        tun?.close()
        tun = null
    }

    override fun onDestroy() {
        stopCutting()
        super.onDestroy()
    }

    companion object {
        const val ACTION_OFF = "com.quotaroom.member.VPN_OFF"
    }
}

package com.aptdesk.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

class MainService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null
    private val rootfsManager by lazy { RootfsManager(this) }
    private val prootManager by lazy { ProotManager(this) }
    private var webServer: WebServer? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBackend()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (runningJob?.isActive == true) {
                    return START_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Starting AptDesk..."))
                runningJob = serviceScope.launch {
                    runBackend()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopBackend()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runBackend() {
        try {
            updateNotification("Starting API server...")
            webServer = WebServer(this, prootManager)
            webServer?.start()

            updateNotification("Preparing rootfs...")
            rootfsManager.ensureRootfs()
            updateNotification("Launching proot...")
            prootManager.start()
            
            val ip = NetworkInfo.getLocalIpAddress()
            updateNotification("http://$ip:8080")
        } catch (error: IOException) {
            Log.e(TAG, "Backend failed", error)
            updateNotification("Failed: ${error.message}")
            stopSelf()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Configuration error", error)
            updateNotification("Failed: ${error.message}")
            stopSelf()
        }
    }

    private fun stopBackend() {
        runningJob?.cancel()
        runningJob = null
        prootManager.stop()
        webServer?.stop()
        webServer = null
    }

    private fun updateNotification(content: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, MainService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AptDesk")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AptDesk Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.aptdesk.app.action.START"
        const val ACTION_STOP = "com.aptdesk.app.action.STOP"
        private const val CHANNEL_ID = "aptdesk_service"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AptDeskService"
    }
}

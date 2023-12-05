package com.dailysdkdemo.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dailysdkdemo.ui.activities.HomeActivity
import com.dailysdkdemo.R
import com.dailysdkdemo.data.utils.ACTION_STOP_FOREGROUND

class NotificationService : Service() {
    private var mNotificationManager: NotificationManager? = null
    private val mNotificationId = 123

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null && intent.action.equals(
                ACTION_STOP_FOREGROUND, ignoreCase = true
            )
        ) {
            mNotificationManager?.cancel(mNotificationId)
            stopSelf()
        } else {
            generateForegroundNotification()
        }
        return START_STICKY
    }

    private fun generateForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intentMainLanding = Intent(this, HomeActivity::class.java)
            intentMainLanding.flags =
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    intentMainLanding,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                )
            if (mNotificationManager == null) {
                mNotificationManager =
                    this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assert(mNotificationManager != null)
                val notificationChannel =
                    NotificationChannel(
                        resources.getString(
                            R.string.channel_id
                        ), resources.getString(
                            R.string.channel_name
                        ),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                notificationChannel.enableLights(false)
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
                mNotificationManager?.createNotificationChannel(notificationChannel)
            }
            val builder = NotificationCompat.Builder(
                this, resources.getString(
                    R.string.channel_id
                )
            )

            val defaultSoundUri: Uri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setContentTitle(resources.getString(R.string.ongoing_call))
                .setContentText(resources.getString(R.string.touch_to_open))
                .setSmallIcon(R.drawable.ic_phone)
                .setOnlyAlertOnce(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
            mNotificationManager?.notify(mNotificationId, builder.build())
        }
    }
}
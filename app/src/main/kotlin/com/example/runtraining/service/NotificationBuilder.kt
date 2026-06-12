package com.example.runtraining.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.runtraining.MainActivity
import com.example.runtraining.R
import com.example.runtraining.util.Format

/** Builds the ongoing notification for `WorkoutForegroundService`. */
object NotificationBuilder {

    const val CHANNEL_ID = "workout"
    const val NOTIFICATION_ID = 1001
    const val ACTION_OPEN_RUN_PAGE = "com.example.runtraining.OPEN_RUN_PAGE"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Workout",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Ongoing workout"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun build(
        context: Context,
        contentTitle: String,
        contentText: String,
        workoutId: Long,
    ): Notification {
        val openRunIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_RUN_PAGE
            putExtra(EXTRA_WORKOUT_ID, workoutId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            openRunIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun contentTextFor(stepIndex: Int, stepsTotal: Int, stepRemainingSec: Int): String =
        "Step $stepIndex/$stepsTotal \u2022 ${Format.formatDuration(stepRemainingSec)} left"

    const val EXTRA_WORKOUT_ID = "com.example.runtraining.WORKOUT_ID"
}

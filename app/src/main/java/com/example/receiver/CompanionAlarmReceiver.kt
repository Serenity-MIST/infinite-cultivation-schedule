package com.example.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.CompanionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.util.Calendar

class CompanionAlarmReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra("EXTRA_REQUEST_CODE", -1)
        if (requestCode == -1) return

        // Launch async work
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                handleAlarmFiring(context, requestCode)
            } finally {
                // Re-schedule this alarm for tomorrow to maintain periodic behavior
                rescheduleNextAlarm(context, requestCode)
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarmFiring(context: Context, requestCode: Int) {
        val currentEpochMs = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        
        when (requestCode) {
            101 -> {
                // Jinzhou Commerce 11:00 AM Alarm (Only when Jinzhou is active)
                val anchorZoned = ZonedDateTime.of(2026, 6, 6, 0, 0, 0, 0, zoneId)
                val anchorEpochMs = anchorZoned.toInstant().toEpochMilli()

                val diffMs = currentEpochMs - anchorEpochMs
                val weekMs = 7L * 24L * 60L * 60L * 1000L

                val rawWeeks = Math.floorDiv(diffMs, weekMs)
                val weekIndex = Math.floorMod(rawWeeks, 2L)

                if (weekIndex == 1L) {
                    showSystemNotification(
                        context = context,
                        id = 101,
                        title = "Jinzhou Commerce Open",
                        content = "Jinzhou Commerce Daily Round is now OPEN! Log in to secure your trade route multipliers."
                    )
                }
            }
            102 -> {
                // Daily Checklist Afternoon Reminder (If core items still incomplete)
                val database = CompanionDatabase.getDatabase(context)
                val tasks = database.dao().getAllTasks()
                val hasIncomplete = tasks.any { it.category == "DAILY" && !it.isCompleted }

                if (hasIncomplete) {
                    showSystemNotification(
                        context = context,
                        id = 102,
                        title = "Daily Cultivation Grinds Left",
                        content = "You still have incomplete tasks on your focus checklist! Complete them before midnight."
                    )
                }
            }
            103 -> {
                // Stat Screenshot cutoff 10:00 PM nightly
                showSystemNotification(
                    context = context,
                    id = 103,
                    title = "Stat Photo Cutoff Warning",
                    content = "It's 10:00 PM! Screenshot your attributes and upload now to save today's cultivation growth."
                )
            }
        }
    }

    private fun showSystemNotification(context: Context, id: Int, title: String, content: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cultivation_companion_native",
                "Cultivation Companion System",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Cultivation notifications for schedules and stat growth reminders"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "cultivation_companion_native")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }

    private fun rescheduleNextAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val hour = when (requestCode) {
            101 -> 11
            102 -> 16
            103 -> 22
            else -> return
        }
        
        val targetIntent = Intent(context, CompanionAlarmReceiver::class.java).apply {
            putExtra("EXTRA_REQUEST_CODE", requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // strictly schedule for tomorrow
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}

object CompanionScheduler {
    fun scheduleAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        fun schedule(requestCode: Int, hour: Int) {
            val intent = Intent(context, CompanionAlarmReceiver::class.java).apply {
                putExtra("EXTRA_REQUEST_CODE", requestCode)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        schedule(101, 11)
        schedule(102, 16)
        schedule(103, 22)
    }
}

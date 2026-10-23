package com.jordanmind.agent.agent

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jordanmind.agent.MainActivity
import com.jordanmind.agent.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A reminder the user asked for. */
data class Reminder(
    val id: Int,
    val task: String,
    val at: Long,
    val done: Boolean = false,
)

/**
 * Reminders backed by AlarmManager, so they fire even when the app has been killed.
 *
 * The pending list is also kept on disk, which is what the floating bot reads to tell the user
 * what they may have forgotten.
 */
class ReminderStore(private val context: Context) {

    private val file = File(context.filesDir, "agent/reminders.json")
    private val items = mutableListOf<Reminder>()

    init {
        load()
    }

    fun all(): List<Reminder> = items.sortedBy { it.at }

    fun pending(): List<Reminder> = items.filter { !it.done }.sortedBy { it.at }

    fun add(task: String, at: Long): Reminder {
        val reminder = Reminder(id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(), task = task, at = at)
        items.add(reminder)
        save()
        schedule(reminder)
        return reminder
    }

    fun complete(id: Int) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = items[index].copy(done = true)
        save()
        cancel(items[index])
    }

    fun remove(id: Int) {
        items.firstOrNull { it.id == id }?.let { cancel(it) }
        items.removeAll { it.id == id }
        save()
    }

    fun dueNow(now: Long = System.currentTimeMillis()): List<Reminder> =
        items.filter { !it.done && it.at <= now }

    /** Re-arms every pending reminder; call after a reboot or app update. */
    fun rescheduleAll() {
        items.filter { !it.done && it.at > System.currentTimeMillis() }.forEach { schedule(it) }
    }

    private fun schedule(reminder: Reminder) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, reminder.id)
            putExtra(EXTRA_TASK, reminder.task)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // setExactAndAllowWhileIdle is throttled by the OS but still fires during Doze.
        runCatching {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
        }.onFailure {
            manager.set(AlarmManager.RTC_WAKEUP, reminder.at, pending)
        }
    }

    private fun cancel(reminder: Reminder) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.cancel(pending)
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                items.add(
                    Reminder(
                        id = obj.getInt("id"),
                        task = obj.getString("task"),
                        at = obj.getLong("at"),
                        done = obj.optBoolean("done", false),
                    ),
                )
            }
        }
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { reminder ->
            array.put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("task", reminder.task)
                    put("at", reminder.at)
                    put("done", reminder.done)
                },
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TASK = "reminder_task"
        const val CHANNEL_REMINDERS = "jordanmind_reminders"
        const val CHANNEL_ARTICLES = "jordanmind_articles"

        fun format(millis: Long): String =
            SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "Things you asked Jordan Mind to remind you about" },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ARTICLES,
                    "Scheduled posts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Nudges when a drafted article is due to publish" },
            )
        }

        fun notify(context: Context, channel: String, id: Int, title: String, body: String) {
            ensureChannels(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_jm)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            runCatching { manager.notify(id, notification) }
        }
    }
}

/** Fires when a reminder's alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = intent.getStringExtra(ReminderStore.EXTRA_TASK) ?: return
        val id = intent.getIntExtra(ReminderStore.EXTRA_ID, 0)
        ReminderStore.notify(context, ReminderStore.CHANNEL_REMINDERS, id, "Reminder", task)
    }
}

/** Re-arms alarms after the device restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderStore(context).rescheduleAll()
        }
    }
}

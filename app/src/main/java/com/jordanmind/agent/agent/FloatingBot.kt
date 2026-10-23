package com.jordanmind.agent.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A draggable bubble that floats over other apps and reminds the user of things they may have
 * forgotten: due reminders, scheduled posts, and anything they asked the agent to remember.
 *
 * Built from plain Android views rather than Compose. A ComposeView inside a Service needs a
 * hand-rolled lifecycle and saved-state owner, and a bubble this small does not justify that risk.
 *
 * The bubble is a foreground service so Android keeps it alive, and it is entirely local — it
 * reads the same JSON files the rest of the app writes.
 */
class FloatingBotService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        if (Settings.canDrawOverlays(this)) {
            showBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && Settings.canDrawOverlays(this)) showBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        panel?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        panel = null
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        ReminderStore.ensureChannels(this)
        val stop = android.app.PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingBotService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.jordanmind.agent.R.drawable.ic_stat_jm)
            .setContentTitle("Jordan Mind is watching your back")
            .setContentText("Tap the bubble for a catch-up")
            .setOngoing(true)
            .addAction(0, "Hide", stop)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val size = (56 * resources.displayMetrics.density).roundToInt()
        val view = TextView(this).apply {
            text = "JM"
            setTextColor(Color.parseColor("#241A00"))
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFC91F"))
                setStroke((2 * resources.displayMetrics.density).roundToInt(), Color.parseColor("#FFE08A"))
            }
        }

        params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - 24
            y = resources.displayMetrics.heightPixels / 3
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).roundToInt()
                    val dy = (event.rawY - touchY).roundToInt()
                    if (abs(dx) > 12 || abs(dy) > 12) dragged = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) togglePanel()
                    true
                }
                else -> false
            }
        }

        bubble = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun togglePanel() {
        panel?.let {
            runCatching { windowManager.removeView(it) }
            panel = null
            return
        }
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * density).roundToInt(),
                (14 * density).roundToInt(),
                (16 * density).roundToInt(),
                (14 * density).roundToInt(),
            )
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.parseColor("#F216130B"))
                setStroke((1 * density).roundToInt(), Color.parseColor("#4A4130"))
            }
        }

        val title = TextView(this).apply {
            text = "Catch-up"
            setTextColor(Color.parseColor("#FFC91F"))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val body = TextView(this).apply {
            text = catchUpText()
            setTextColor(Color.parseColor("#F3EEE2"))
            textSize = 13f
        }
        val close = TextView(this).apply {
            text = "Close"
            setTextColor(Color.parseColor("#FF9E2C"))
            textSize = 13f
            setPadding(0, (10 * density).roundToInt(), 0, 0)
            setOnClickListener { togglePanel() }
        }
        card.addView(title)
        card.addView(body)
        card.addView(close)

        val width = (resources.displayMetrics.widthPixels * 0.82f).roundToInt()
        val panelParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        panel = card
        runCatching { windowManager.addView(card, panelParams) }
    }

    /** Reads local state only; the app has no network permission. */
    private fun catchUpText(): String {
        val reminders = ReminderStore(this)
        val studio = ContentStudio(this)
        val memory = MemoryStore(this)
        val due = reminders.pending().take(5).map { "${it.task} — ${ReminderStore.format(it.at)}" }
        return Prompts.catchUp(studio.scheduled(), due, memory.allFacts())
    }

    companion object {
        const val CHANNEL = "jordanmind_floating"
        const val NOTIFICATION_ID = 4242
        const val ACTION_STOP = "com.jordanmind.agent.STOP_BOT"

        fun start(context: Context) {
            val intent = Intent(context, FloatingBotService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FloatingBotService::class.java)) }
        }
    }
}

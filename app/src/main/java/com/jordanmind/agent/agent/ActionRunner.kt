package com.jordanmind.agent.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runs [AgentAction]s against the device.
 *
 * Two deliberate policies:
 *  - SMS is never sent silently. The intent is opened pre-filled and the user presses send.
 *    Auto-sending would need SEND_SMS, which is a dangerous permission and easy to abuse.
 *  - Nothing here needs INTERNET, so the app keeps its no-network guarantee.
 */
class ActionRunner(private val context: Context) {

    /** Common app names mapped to package names, so "open whatsapp" works without a model. */
    private val knownApps = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "telegram" to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "x" to "com.twitter.android",
        "twitter" to "com.twitter.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera",
        "gallery" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "settings" to "com.android.settings",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "linkedin" to "com.linkedin.android",
        "zoom" to "us.zoom.videomeetings",
        "teams" to "com.microsoft.teams",
        "outlook" to "com.microsoft.office.outlook",
        "word" to "com.microsoft.office.word",
        "excel" to "com.microsoft.office.excel",
        "opera" to "com.opera.browser",
        "play store" to "com.android.vending",
        "phone" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "messages" to "com.google.android.apps.messaging",
        "notes" to "com.google.android.keep",
        "keep" to "com.google.android.keep",
        "files" to "com.google.android.documentsui",
        "bank" to "com.android.bank", // placeholder; real package is device specific
    )

    fun run(action: AgentAction): ActionResult = when (action.type) {
        ActionType.OPEN_APP -> openApp(action.args["app"].orEmpty())
        ActionType.SEND_SMS -> composeSms(action.args["contact"].orEmpty(), action.args["body"].orEmpty())
        ActionType.SET_ALARM -> setAlarm(action.args["time"].orEmpty())
        ActionType.SET_TIMER -> setTimer(action.args["time"].orEmpty())
        ActionType.NOTE -> ActionResult(true, "Noted.")
        else -> ActionResult(true, action.reply)
    }

    private fun openApp(name: String): ActionResult {
        if (name.isBlank()) return ActionResult(false, "Which app?")

        val query = name.lowercase().trim()
        val packageName = knownApps[query] ?: resolveInstalledPackage(query)

        if (packageName == null) {
            return ActionResult(false, "I could not find an app called \"$name\".")
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(false, "\"$name\" is installed but has no launch screen.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ActionResult(true, "Opened $name.")
        } catch (error: Exception) {
            ActionResult(false, "Could not open $name: ${error.message}")
        }
    }

    /** Searches installed launchable apps for a label containing the query. */
    private fun resolveInstalledPackage(query: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val candidates = pm.queryIntentActivities(intent, 0)
        val match = candidates.firstOrNull { info ->
            val label = runCatching { info.loadLabel(pm).toString().lowercase() }.getOrDefault("")
            label == query || label.replace(" ", "").contains(query.replace(" ", ""))
        }
        return match?.activityInfo?.packageName
    }

    /**
     * Opens the messaging composer with recipient and body filled in. The user must still press
     * send — this app will not send messages behind the user's back.
     */
    private fun composeSms(contact: String, body: String): ActionResult {
        if (contact.isBlank()) return ActionResult(false, "Who should I message?")

        val number = resolveContactNumber(contact)
        val uri = if (number != null) Uri.parse("smsto:$number") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            val who = if (number != null) contact else contact
            ActionResult(true, "Opened messages for $who. Press send when you are ready.")
        } catch (error: Exception) {
            ActionResult(false, "No messaging app available: ${error.message}")
        }
    }

    /**
     * Looks up a phone number by display name. READ_CONTACTS is requested only when the user
     * actually asks to message someone, never at startup.
     */
    private fun resolveContactNumber(name: String): String? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val resolver = context.contentResolver
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        return runCatching {
            resolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                arrayOf("%$name%"),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(1) else null
            }
        }.getOrNull()
    }

    private fun setAlarm(time: String): ActionResult {
        val parsed = parseTime(time) ?: return ActionResult(
            false,
            "I could not read \"$time\". Try \"7am\" or \"18:30\".",
        )
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, parsed.first)
            putExtra(AlarmClock.EXTRA_MINUTES, parsed.second)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult(true, "Alarm set for %02d:%02d.".format(parsed.first, parsed.second))
        } catch (error: Exception) {
            ActionResult(false, "No clock app available: ${error.message}")
        }
    }

    private fun setTimer(duration: String): ActionResult {
        val seconds = parseDuration(duration)
            ?: return ActionResult(false, "I could not read \"$duration\". Try \"10 minutes\".")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult(true, "Timer started for $duration.")
        } catch (error: Exception) {
            ActionResult(false, "No clock app available: ${error.message}")
        }
    }

    /** True when the user has disabled our overlay, which silently breaks the floating bot. */
    fun overlayAllowed(): Boolean = Settings.canDrawOverlays(context)

    companion object {
        /** Accepts "7am", "7:30 pm", "18:30", "7". Returns hour, minute. */
        fun parseTime(raw: String): Pair<Int, Int>? {
            // A dot is a common time separator ("7.30pm"), so treat it as one rather than
            // deleting it, which would turn "7.30pm" into the nonsense "730pm".
            val text = raw.lowercase().trim().replace(".", ":")
            val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text) ?: return null
            var hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val meridiem = match.groupValues[3]
            if (hour > 23 || minute > 59) return null
            when (meridiem) {
                "pm" -> if (hour < 12) hour += 12
                "am" -> if (hour == 12) hour = 0
            }
            return hour to minute
        }

        /** Accepts "10 minutes", "1 hour 30 minutes", "45s", "2h". Returns seconds. */
        fun parseDuration(raw: String): Int? {
            val text = raw.lowercase().trim()
            var total = 0
            var found = false
            Regex("(\\d+)\\s*(hours?|hrs?|h)").find(text)?.let {
                total += it.groupValues[1].toInt() * 3600
                found = true
            }
            Regex("(\\d+)\\s*(minutes?|mins?|m)\\b").find(text)?.let {
                total += it.groupValues[1].toInt() * 60
                found = true
            }
            Regex("(\\d+)\\s*(seconds?|secs?|s)\\b").find(text)?.let {
                total += it.groupValues[1].toInt()
                found = true
            }
            if (!found) {
                val bare = text.filter { it.isDigit() }
                if (bare.isNotEmpty()) return bare.toInt() * 60
            }
            return total.takeIf { it > 0 }
        }
    }
}

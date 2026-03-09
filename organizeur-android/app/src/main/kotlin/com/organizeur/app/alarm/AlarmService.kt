package com.organizeur.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.organizeur.app.R
import com.organizeur.app.silentmode.SilentModeEnforcer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibratorManager: VibratorManager? = null
    private var currentAlarmId: String? = null
    private var overlayView: View? = null
    private val timeHandler = Handler(Looper.getMainLooper())
    private var timeRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return START_NOT_STICKY
                currentAlarmId = alarmId
                val repository = AlarmRepository(this)
                val alarm = repository.getAlarmById(alarmId)
                val alarmName = alarm?.name ?: ""
                ringingAlarmName = alarmName.ifEmpty { "Alarme" }

                // Deactivate silent mode if configured for this alarm
                if (alarm?.deactivatesSilentMode != false) {
                    val enforcer = SilentModeEnforcer(this)
                    if (enforcer.isAnyScheduleActive()) {
                        enforcer.restorePreviousState()
                    }
                    // Failsafe: always force DND off when alarm rings
                    enforcer.forceDisableDnd()
                }

                // Start foreground with notification
                val notification = buildNotification(alarmId, alarmName)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

                // Set alarm volume to max
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )

                // Play alarm sound
                startAlarmSound(alarm?.ringtoneUri?.let { Uri.parse(it) })

                // Start vibration
                startVibration()

                // Show overlay (works on MIUI where activity launch is blocked)
                showOverlay(ringingAlarmName ?: alarmName)
            }

            ACTION_DISMISS -> {
                stopAlarm()
                stopSelf()
            }

            ACTION_SNOOZE -> {
                val alarmId = currentAlarmId
                stopAlarm()
                if (alarmId != null) {
                    val repository = AlarmRepository(this)
                    val alarm = repository.getAlarmById(alarmId)
                    if (alarm != null) {
                        val scheduler = AlarmScheduler(this)
                        scheduler.scheduleSnooze(alarm)
                    }
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startAlarmSound(ringtoneUri: Uri? = null) {
        try {
            val alarmUri = ringtoneUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback: try default notification sound
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmService, fallbackUri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {
                // No sound available
            }
        }
    }

    private fun startVibration() {
        vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vibrator = vibratorManager?.defaultVibrator
        val pattern = longArrayOf(0, 500, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // repeat from index 0
    }

    private fun showOverlay(alarmName: String) {
        if (!Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val dp = { value: Int ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
            }

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val timeView = TextView(this).apply {
                text = timeFormat.format(Date())
                setTextColor(Color.WHITE)
                textSize = 64f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            val nameView = TextView(this).apply {
                text = alarmName
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(48))
            }

            var buttonsEnabled = true

            // Confirmation message (hidden initially)
            val confirmView = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                visibility = View.GONE
            }

            val dismissButton = Button(this).apply {
                text = getString(R.string.alarm_dismiss)
                setTextColor(Color.WHITE)
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.parseColor("#D32F2F"))
                minimumHeight = dp(64)
                setOnClickListener {
                    if (!buttonsEnabled) return@setOnClickListener
                    buttonsEnabled = false
                    stopAlarm()
                    stopSelf()
                }
            }

            val snoozeButton = Button(this).apply {
                text = getString(R.string.alarm_snooze)
                setTextColor(Color.WHITE)
                textSize = 18f
                setBackgroundColor(Color.parseColor("#555555"))
                minimumHeight = dp(56)
            }

            // Snooze click handler (needs references to other views)
            snoozeButton.setOnClickListener {
                if (!buttonsEnabled) return@setOnClickListener
                buttonsEnabled = false
                val alarmId = currentAlarmId
                // Stop sound and vibration
                ringingAlarmName = null
                mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
                mediaPlayer = null
                vibratorManager?.defaultVibrator?.cancel()
                vibratorManager = null

                var snoozeMins = 10
                if (alarmId != null) {
                    val repository = AlarmRepository(this@AlarmService)
                    val alarm = repository.getAlarmById(alarmId)
                    if (alarm != null) {
                        snoozeMins = alarm.snoozeDurationMinutes
                        AlarmScheduler(this@AlarmService).scheduleSnooze(alarm)
                    }
                }
                // Hide buttons, show confirmation
                dismissButton.visibility = View.GONE
                snoozeButton.visibility = View.GONE
                nameView.visibility = View.GONE
                confirmView.text = "Rappel dans $snoozeMins min"
                confirmView.visibility = View.VISIBLE
                timeHandler.postDelayed({
                    removeOverlay()
                    stopSelf()
                }, 1500L)
            }

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#E6000000"))
                setPadding(dp(32), dp(64), dp(32), dp(32))
                addView(timeView)
                addView(nameView)
                addView(confirmView)
                addView(dismissButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
                ).apply { bottomMargin = dp(16) })
                addView(snoozeButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
                ))
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                PixelFormat.TRANSLUCENT
            )

            wm.addView(layout, params)
            overlayView = layout

            // Update time every second
            timeRunnable = object : Runnable {
                override fun run() {
                    timeView.text = timeFormat.format(Date())
                    timeHandler.postDelayed(this, 1000L)
                }
            }
            timeHandler.post(timeRunnable!!)

        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        timeRunnable?.let { timeHandler.removeCallbacks(it) }
        timeRunnable = null
        overlayView?.let {
            try {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                wm.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun stopAlarm() {
        ringingAlarmName = null
        removeOverlay()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibratorManager?.defaultVibrator?.cancel()
        vibratorManager = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_notification_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setBypassDnd(true)
            setSound(null, null) // Sound handled by MediaPlayer
            enableVibration(false) // Vibration handled manually
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(alarmId: String, alarmName: String): Notification {
        // Full-screen intent for the ringing activity
        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getService(
            this, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(alarmName)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.alarm_dismiss), dismissPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    null, getString(R.string.alarm_snooze), snoozePendingIntent
                ).build()
            )
            .build()
    }

    companion object {
        const val ACTION_START_ALARM = "com.organizeur.app.alarm.START_ALARM"
        const val ACTION_DISMISS = "com.organizeur.app.alarm.DISMISS"
        const val ACTION_SNOOZE = "com.organizeur.app.alarm.SNOOZE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val NOTIFICATION_ID = 3001
        const val CHANNEL_ID = "alarm_ringing"

        /** Currently ringing alarm name, null if not ringing. Accessible from UI. */
        var ringingAlarmName: String? = null
            private set
    }
}

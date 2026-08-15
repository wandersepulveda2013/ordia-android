package com.ordia.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.app.NotificationCompat
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.preferences.GuardianMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

class GuardianOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var guardianView: View? = null
    private var actionPanel: View? = null
    private var guardianMode: GuardianMode = GuardianMode.DISCREET

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        scope.launch {
            (application as OrdiaApplication).container.preferencesRepository.preferences
                .map { it.guardianMode }
                .distinctUntilChanged()
                .collect { mode ->
                    val changed = guardianMode != mode
                    guardianMode = mode
                    if (changed && guardianView != null) recreateGuardian() else showGuardian()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun showGuardian() {
        if (guardianView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = when (guardianMode) {
            GuardianMode.DORMANT -> 44
            GuardianMode.DISCREET -> 54
            GuardianMode.COMPANION -> 62
        }
        val orb = object : TextView(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }.apply {
            text = when (guardianMode) {
                GuardianMode.DORMANT -> "✦"
                GuardianMode.DISCREET -> "•‿•"
                GuardianMode.COMPANION -> "✦\n•‿•"
            }
            textSize = if (guardianMode == GuardianMode.COMPANION) 18f else 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFF7F3EB.toInt())
            contentDescription = "Guardián de Ordia. Toca para ver acciones rápidas. Mantén y arrastra para moverlo."
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1D1B17.toInt())
                setStroke(dp(2), 0xFFD9BC7A.toInt())
            }
            elevation = dp(10).toFloat()
        }
        val saved = getSharedPreferences(POSITIONS, MODE_PRIVATE)
        val params = WindowManager.LayoutParams(
            dp(size), dp(size),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = saved.getInt("x", dp(20))
            y = saved.getInt("y", dp(180))
        }
        orb.setOnTouchListener(DragTouchListener(params))
        windowManager.addView(orb, params)
        guardianView = orb
    }

    private fun togglePanel(anchorParams: WindowManager.LayoutParams) {
        if (actionPanel != null) {
            hidePanel()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xF21D1B17.toInt())
                setStroke(dp(1), 0x66D9BC7A.toInt())
            }
            addAction("＋ Tarea") { openCapture(QuickCaptureActivity.MODE_TASK) }
            addAction("✎ Nota") { openCapture(QuickCaptureActivity.MODE_NOTE) }
            addAction("◷ Enfoque") { openMain(MainActivity.OPEN_FOCUS) }
            addAction("▣ Abrir Ordia") { openMain(null) }
            addAction("× Ocultar") { stopSelf() }
        }
        val params = WindowManager.LayoutParams(
            dp(150), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (anchorParams.x + dp(66)).coerceAtMost(resources.displayMetrics.widthPixels - dp(160))
            y = anchorParams.y.coerceIn(dp(24), resources.displayMetrics.heightPixels - dp(320))
        }
        windowManager.addView(panel, params)
        actionPanel = panel
    }

    private fun LinearLayout.addAction(label: String, action: () -> Unit) {
        addView(TextView(this@GuardianOverlayService).apply {
            text = label
            textSize = 14f
            setTextColor(0xFFF7F3EB.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = label
            setOnClickListener { hidePanel(); action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun openCapture(mode: String) {
        startActivity(Intent(this, QuickCaptureActivity::class.java).putExtra(QuickCaptureActivity.EXTRA_MODE, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openMain(destination: String?) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            destination?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) }
        })
    }

    private inner class DragTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(v, params)
                    hidePanel()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    if (moved < dp(10)) {
                        v.performClick(); togglePanel(params)
                    } else {
                        val screenWidth = resources.displayMetrics.widthPixels
                        params.x = if (params.x + v.width / 2 < screenWidth / 2) dp(8) else screenWidth - v.width - dp(8)
                        params.y = params.y.coerceIn(dp(24), resources.displayMetrics.heightPixels - v.height - dp(80))
                        windowManager.updateViewLayout(v, params)
                        getSharedPreferences(POSITIONS, MODE_PRIVATE).edit { putInt("x", params.x); putInt("y", params.y) }
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun recreateGuardian() {
        hidePanel()
        guardianView?.let { runCatching { windowManager.removeView(it) } }
        guardianView = null
        showGuardian()
    }

    private fun hidePanel() {
        actionPanel?.let { runCatching { windowManager.removeView(it) } }
        actionPanel = null
    }

    override fun onDestroy() {
        hidePanel()
        guardianView?.let { runCatching { windowManager.removeView(it) } }
        guardianView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, GuardianOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ordia)
            .setContentTitle(getString(R.string.guardian_notification_title))
            .setContentText(getString(R.string.guardian_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Ocultar", stop)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.guardian_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.guardian_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_STOP = "com.ordia.app.action.STOP_GUARDIAN"
        private const val CHANNEL_ID = "ordia_guardian"
        private const val NOTIFICATION_ID = 1001
        private const val POSITIONS = "guardian_position"
    }
}

package com.ordia.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.UserPreferences
import com.ordia.app.domain.GuardianEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.abs

class GuardianOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var guardianView: GuardianPetView? = null
    private var actionPanel: View? = null
    private var preferences = UserPreferences()
    private var quietHoursActive = false

    private val repository get() = (application as OrdiaApplication).container.preferencesRepository

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            scope.launch { repository.setGuardianEnabled(false) }
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        scope.launch {
            repository.preferences.collect { value ->
                val structuralChange = preferences.guardianMode != value.guardianMode
                val previousQuiet = quietHoursActive
                preferences = value
                quietHoursActive = isQuietHours(value)
                when {
                    !value.guardianEnabled || !Settings.canDrawOverlays(this@GuardianOverlayService) -> stopSelf()
                    guardianView == null -> showGuardian()
                    structuralChange || previousQuiet != quietHoursActive -> recreateGuardian()
                    else -> {
                        guardianView?.update(value, animationsAllowed = !quietHoursActive)
                        guardianView?.alpha = if (quietHoursActive) 0.68f else 1f
                    }
                }
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            }
        }
        scope.launch {
            while (true) {
                delay(60_000L)
                val current = isQuietHours(preferences)
                if (current != quietHoursActive && preferences.guardianEnabled) {
                    quietHoursActive = current
                    recreateGuardian()
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { repository.setGuardianEnabled(false) }
            stopSelf()
        }
        // The user or a visible Ordia activity must explicitly start the overlay again.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::windowManager.isInitialized && preferences.guardianEnabled) recreateGuardian()
    }

    private fun showGuardian() {
        if (guardianView != null || !preferences.guardianEnabled || !Settings.canDrawOverlays(this)) return
        val size = if (quietHoursActive) 48 else when (preferences.guardianMode) {
            GuardianMode.DORMANT -> 48
            GuardianMode.DISCREET -> 64
            GuardianMode.COMPANION -> 82
        }
        val pet = GuardianPetView(this).apply {
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            update(preferences, animationsAllowed = !quietHoursActive)
            alpha = if (quietHoursActive) 0.68f else 1f
            setOnClickListener { view ->
                val params = view.tag as? WindowManager.LayoutParams ?: return@setOnClickListener
                if (!quietHoursActive) celebrate()
                togglePanel(params)
            }
            setOnTouchListener(DragTouchListener())
        }
        val saved = getSharedPreferences(POSITIONS, MODE_PRIVATE)
        val params = overlayParams(dp(size), dp(size)).apply {
            val bounds = safeBounds(dp(size), dp(size))
            x = saved.getInt("x", bounds.left + dp(12)).coerceIn(bounds.left, bounds.right)
            y = saved.getInt("y", bounds.top + dp(120)).coerceIn(bounds.top, bounds.bottom)
        }
        pet.tag = params
        runCatching { windowManager.addView(pet, params) }
            .onSuccess { guardianView = pet }
            .onFailure {
                scope.launch { repository.setGuardianEnabled(false) }
                stopSelf()
            }
    }

    private fun togglePanel(anchorParams: WindowManager.LayoutParams) {
        if (actionPanel != null) {
            hidePanel()
            return
        }
        val panelWidth = dp(188)
        val panelHeight = availablePanelHeight().coerceAtMost(dp(400)).coerceAtLeast(dp(220))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            addTitle(preferences.guardianName.ifBlank { preferences.guardianSpecies.defaultName })
            addAction("Acariciar") { interact(GuardianEngine.Interaction.PET) }
            addAction("Jugar") { interact(GuardianEngine.Interaction.PLAY) }
            addAction("Nueva tarea") { openCapture(QuickCaptureActivity.MODE_TASK) }
            addAction("Nueva nota") { openCapture(QuickCaptureActivity.MODE_NOTE) }
            addAction("Iniciar enfoque") { openMain(MainActivity.OPEN_FOCUS) }
            val contextualCount = (application as OrdiaApplication).container.contextualSuggestionStore.list().size
            if (contextualCount > 0) addAction("Sugerencias ($contextualCount)") { openMain(MainActivity.OPEN_CONTEXTUAL) }
            addAction("Abrir refugio") { openMain(MainActivity.OPEN_GUARDIAN) }
            addAction("Ocultar guardián") {
                scope.launch { repository.setGuardianEnabled(false) }
                stopSelf()
            }
        }
        val panel = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(0xF21D1B17.toInt())
                setStroke(dp(1), 0x88D9BC7A.toInt())
            }
            addView(actions, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        }
        val params = overlayParams(panelWidth, panelHeight).apply {
            val bounds = safeBounds(panelWidth, panelHeight)
            x = (anchorParams.x + dp(86)).coerceIn(bounds.left, bounds.right)
            y = anchorParams.y.coerceIn(bounds.top, bounds.bottom)
        }
        runCatching { windowManager.addView(panel, params) }
            .onSuccess { actionPanel = panel }
    }


    private fun availablePanelHeight(): Int {
        val height = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            metrics.bounds.height() - insets.top - insets.bottom
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.heightPixels - dp(72)
        }
        return (height * 0.68f).toInt()
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun safeBounds(viewWidth: Int, viewHeight: Int): Rect {
        val margin = dp(8)
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val systemInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val bounds = metrics.bounds
            val left = systemInsets.left + margin
            val top = systemInsets.top + margin
            val right = (bounds.width() - systemInsets.right - viewWidth - margin).coerceAtLeast(left)
            val effectiveHeight = if (viewHeight > 0) viewHeight else dp(400)
            val bottom = (bounds.height() - systemInsets.bottom - effectiveHeight - margin).coerceAtLeast(top)
            return Rect(left, top, right, bottom)
        }
        @Suppress("DEPRECATION")
        val metrics = resources.displayMetrics
        val left = margin
        val top = dp(24)
        val right = (metrics.widthPixels - viewWidth - margin).coerceAtLeast(left)
        val effectiveHeight = if (viewHeight > 0) viewHeight else dp(400)
        val bottom = (metrics.heightPixels - effectiveHeight - dp(48)).coerceAtLeast(top)
        return Rect(left, top, right, bottom)
    }

    private fun LinearLayout.addTitle(label: String) {
        addView(TextView(this@GuardianOverlayService).apply {
            text = label
            textSize = 15f
            setTextColor(0xFFD9BC7A.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.addAction(label: String, action: () -> Unit) {
        addView(TextView(this@GuardianOverlayService).apply {
            text = label
            textSize = 14f
            setTextColor(0xFFF7F3EB.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { hidePanel(); action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun interact(interaction: GuardianEngine.Interaction) {
        if (!quietHoursActive) guardianView?.celebrate()
        scope.launch { repository.interactGuardian(interaction) }
    }

    private fun openCapture(mode: String) {
        runCatching {
            startActivity(
                Intent(this, QuickCaptureActivity::class.java)
                    .putExtra(QuickCaptureActivity.EXTRA_MODE, mode)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun openMain(destination: String?) {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                destination?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) }
            })
        }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private lateinit var params: WindowManager.LayoutParams

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            params = v.tag as WindowManager.LayoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val bounds = safeBounds(v.width.coerceAtLeast(v.layoutParams.width), v.height.coerceAtLeast(v.layoutParams.height))
                    params.x = (initialX + (event.rawX - touchX).toInt()).coerceIn(bounds.left, bounds.right)
                    params.y = (initialY + (event.rawY - touchY).toInt()).coerceIn(bounds.top, bounds.bottom)
                    runCatching { windowManager.updateViewLayout(v, params) }
                    hidePanel()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    if (moved < dp(10)) v.performClick() else snapAndSave(v)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    snapAndSave(v)
                    return true
                }
            }
            return false
        }

        private fun snapAndSave(v: View) {
            val width = v.width.coerceAtLeast(v.layoutParams.width)
            val height = v.height.coerceAtLeast(v.layoutParams.height)
            val bounds = safeBounds(width, height)
            val screenCenter = (bounds.left + bounds.right + width) / 2
            params.x = if (params.x + width / 2 < screenCenter) bounds.left else bounds.right
            params.y = params.y.coerceIn(bounds.top, bounds.bottom)
            runCatching { windowManager.updateViewLayout(v, params) }
            getSharedPreferences(POSITIONS, MODE_PRIVATE).edit()
                .putInt("x", params.x)
                .putInt("y", params.y)
                .apply()
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
        if (::windowManager.isInitialized) guardianView?.let { runCatching { windowManager.removeView(it) } }
        guardianView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GuardianOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ordia)
            .setContentTitle(preferences.guardianName.ifBlank { getString(R.string.guardian_notification_title) })
            .setContentText(
                if (quietHoursActive) "Horas de silencio: el guardián permanece discreto."
                else "Tu compañero virtual está activo. Tócalo para interactuar."
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Ocultar", stop)
            .build()
    }

    private fun isQuietHours(value: UserPreferences, time: LocalTime = LocalTime.now()): Boolean =
        GuardianEngine.isQuietHours(
            startMinutes = value.quietStartMinutes,
            endMinutes = value.quietEndMinutes,
            currentMinutes = time.hour * 60 + time.minute
        )

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.guardian_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantiene disponible el compañero virtual flotante de Ordia."
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

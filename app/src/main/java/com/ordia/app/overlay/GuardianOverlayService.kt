package com.ordia.app.overlay

import android.app.Service
import android.content.Intent
import android.os.IBinder

class GuardianOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

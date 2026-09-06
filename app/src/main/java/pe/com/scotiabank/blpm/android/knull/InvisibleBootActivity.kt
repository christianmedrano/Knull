package pe.com.scotiabank.blpm.android.knull

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardService

class InvisibleBootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Hacer la actividad visible incluso sobre la pantalla de bloqueo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        Log.d("InvisibleBootActivity", "Iniciando servicio de cámara...")

        try {
            val serviceIntent = Intent(this, FaceGuardService::class.java)
            // 2. Usar startForegroundService
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("InvisibleBootActivity", "Error: ${e.message}")
        }

        // 3. Aumentar el delay a 3-5 segundos para asegurar que CameraX inicie
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("InvisibleBootActivity", "Cerrando activity transparente.")
            finish()
        }, 1000)
    }
}
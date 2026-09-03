package pe.com.scotiabank.blpm.android.knull

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardService

class InvisibleBootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("InvisibleBootActivity", "Iniciando servicio de cámara desde Activity transparente...")

        try {
            val serviceIntent = Intent(this, FaceGuardService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("InvisibleBootActivity", "Error al iniciar FaceGuardService: ${e.message}", e)
        }

        // Permitir que el SO registre que la ventana interactiva se desplegó antes de destruirla
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 1000)
    }
}
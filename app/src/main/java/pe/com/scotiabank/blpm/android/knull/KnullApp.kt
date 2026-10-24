package pe.com.scotiabank.blpm.android.knull

import android.app.Application
import com.google.firebase.FirebaseApp

class KnullApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar Firebase a nivel global de la aplicación antes de cualquier servicio
        FirebaseApp.initializeApp(this)
    }
}
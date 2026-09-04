package pe.com.scotiabank.blpm.android.knull.faceguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.Executors

class FaceGuardService : LifecycleService() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Manejo del ciclo de 30 segundos
    private val mainHandler = Handler(Looper.getMainLooper())
    private val SCAN_INTERVAL_MS = 30_000L // 30 segundos

    private val scanRunnable = object : Runnable {
        override fun run() {
            verifyUserFace()
            // Programar la siguiente verificación en 30 segundos
            mainHandler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    // 1. Escanear inmediatamente al desbloquear
                    mainHandler.removeCallbacks(scanRunnable)
                    mainHandler.post(scanRunnable)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // 2. Apagar los escaneos si la pantalla se apaga para no gastar batería
                    mainHandler.removeCallbacks(scanRunnable)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        startForegroundService()

        // Registrar eventos de desbloqueo y apagado de pantalla
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun verifyUserFace() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            var isDone = false

            // La cámara permanece activa buscando durante 3.5 segundos en cada ciclo
            val stopHandler = Handler(Looper.getMainLooper())
            val stopRunnable = Runnable {
                if (!isDone) {
                    isDone = true
                    cameraProvider.unbindAll()
                }
            }
            stopHandler.postDelayed(stopRunnable, 3500)

            imageAnalysis.setAnalyzer(cameraExecutor, FaceAnalyzer { faces, faceBitmap ->
                if (!isDone) {
                    if (faces.isNotEmpty()) {
                        val currentFace = faces.first()

                        if (FaceMatcher.isBlockedUser(this@FaceGuardService, currentFace, faceBitmap)) {
                            isDone = true
                            stopHandler.removeCallbacks(stopRunnable)
                            mainHandler.removeCallbacks(scanRunnable)

                            lockScreen()
                            cameraProvider.unbindAll()
                        }
                    }
                }
            })

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun lockScreen() {
        val adminComponent = AdminReceiver.getComponentName(this)
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow()
        }
    }

    private fun startForegroundService() {
        val channelId = "face_guard_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Guardia de Seguridad", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Protección activa")
            .setContentText("Supervisando el uso del dispositivo...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(scanRunnable)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cameraExecutor.shutdown()
    }
}
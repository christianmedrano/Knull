package pe.com.scotiabank.blpm.android.knull.faceguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import pe.com.scotiabank.blpm.android.knull.R
import java.util.Calendar
import java.util.Locale

class FaceGuardService : LifecycleService(), TextToSpeech.OnInitListener {

    private val TAG = "FaceGuardService"

    companion object {
        var isUnlockedByAdmin: Boolean = false
        // Caché global en memoria sincronizada al instante desde Realtime Database
        var currentTemplateJson: String = "{\"start_time\":\"17:00\",\"end_time\":\"17:30\",\"is_active\":true}"
    }

    // Tu Blacklist original intacta
    private val blackList = setOf(
        "com.roblox.client", "com.pokemon.pokemontcgp", "jp.pokemon.pokemontcgp",
        "com.epicgames.fortnite", "com.dts.freefireth", "com.dts.freefiremax", "com.tencent.ig",
        "com.activision.callofduty.shooter", "com.supercell.brawlstars", "com.supercell.clashroyale",
        "com.supercell.clashofclans", "com.supercell.squad", "com.mojang.minecraftpe",
        "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea", "com.kiloo.subwaysurf",
        "com.outfit7.talkingtomgoldrun", "com.vNG.Roblox", "com.innersloth.spacemafia",
        "com.craftsman.go", "com.akav.lokicraft", "com.multicraft.game", "com.fungames.blockcraft",
        "com.exploration.master.craft", "com.and.games505.TerrariaPaid", "com.android.chrome",
        "com.android.vending", "com.google.android.youtube", "com.google.android.apps.youtube.kids",
        "com.sec.android.app.samsungapps", "com.sec.android.app.sbrowser",
        "com.google.android.apps.docs", "com.google.android.googlequicksearchbox"
    )

    private var monitorJob: Job? = null

    // --- VARIABLES PARA TTS Y FIREBASE REALTIME DATABASE ---
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastNotifiedPackage: String? = null

    private var databaseRef: DatabaseReference? = null
    private var databaseListener: ValueEventListener? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "Pantalla desbloqueada: iniciando monitoreo Root...")
                    startAppMonitoring()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Pantalla apagada: pausando monitoreo Root.")
                    stopAppMonitoring()
                    lastNotifiedPackage = null // Reseteamos aviso al apagar pantalla
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        // --- INICIALIZAR TTS Y REALTIME DATABASE ---
        tts = TextToSpeech(this, this)
        setupRealtimeDatabase()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        startAppMonitoring()
    }

    // --- CONFIGURACIÓN DE FIREBASE REALTIME DATABASE EN TIEMPO REAL ---
    // --- CONFIGURACIÓN DE FIREBASE REALTIME DATABASE LEYENDO EL ÁRBOL DE NODOS ---
    private fun setupRealtimeDatabase() {
        try {
            // Apunta al nodo "template" principal
            databaseRef = Firebase.database.reference.child("template")

            databaseListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Leemos los nodos hijos independientes directamente
                    val startTime = snapshot.child("start_time").getValue(String::class.java) ?: "17:00"
                    val endTime = snapshot.child("end_time").getValue(String::class.java) ?: "17:30"
                    val isActive = snapshot.child("is_active").getValue(Boolean::class.java) ?: false

                    // Reconstruimos el formato JSON en memoria para que tu checkFreeTimeWindow() funcione intacto
                    currentTemplateJson = "{\"start_time\":\"$startTime\",\"end_time\":\"$endTime\",\"is_active\":$isActive}"

                    Log.d(TAG, "Template actualizado desde nodos de Firebase: $currentTemplateJson")

                    // Reseteamos el aviso para que la voz anuncie de nuevo si cambia el tiempo
                    lastNotifiedPackage = null
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error escuchando Realtime Database", error.toException())
                }
            }

            databaseRef?.addValueEventListener(databaseListener!!)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción configurando Realtime Database", e)
        }
    }

    // --- CALLBACK DE TEXT TO SPEECH ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    private fun startAppMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Bucle de monitoreo iniciado.")
            while (isActive) {
                val (currentPackage, currentClass) = getForegroundPackageAndClass()
                val activePackage = getActiveForegroundPackage()
                val targetPackage = activePackage ?: currentPackage

                if (targetPackage != null) {
                    goHome(targetPackage, currentClass)
                }

                delay(500)
            }
        }
    }

    private fun getActiveForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 2000, time)
        if (!stats.isNullOrEmpty()) {
            val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
            return mostRecent?.packageName
        }
        return null
    }

    private fun stopAppMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun getForegroundPackageAndClass(): Pair<String?, String?> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 3000, time)
        val event = android.app.usage.UsageEvents.Event()

        var lastPackage: String? = null
        var lastClass: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
                lastClass = event.className
            }
        }
        return Pair(lastPackage, lastClass)
    }

    /*private fun goHome(currentPackage: String, currentClass: String?) {
        var isBlocked = false

        // 1. Detección quirúrgica dentro de Ajustes (com.android.settings)
        if (currentPackage == "com.android.settings" && isUnlockedByAdmin.not()) {
            val isDeviceAdminScreen =
                currentClass?.contains("DeviceAdminSettings", ignoreCase = true) == true ||
                        currentClass?.contains("DeviceAdminAdd", ignoreCase = true) == true ||
                        currentClass?.contains("SubSettings", ignoreCase = true) == true

            if (isDeviceAdminScreen) {
                Log.w(TAG, "Intento de entrar a Administración de Dispositivos detectado: $currentClass")
                isBlocked = true
            }
        }
        // 2. Bloqueo normal para el resto de la lista negra
        else if (blackList.contains(currentPackage)) {

            // --- EVALUACIÓN DE HORARIO LIBRE USANDO REALTIME DATABASE ---
            val (isFreeTime, remainingMinutes) = checkFreeTimeWindow()

            if (isFreeTime) {
                Log.d(TAG, "Horario libre activo para $currentPackage. Minutos restantes: $remainingMinutes")

                if (lastNotifiedPackage != currentPackage) {
                    lastNotifiedPackage = currentPackage
                    speakRemainingTime(remainingMinutes)
                }
                isBlocked = false
            } else {
                lastNotifiedPackage = null
                isBlocked = true
            }
        } else {
            lastNotifiedPackage = null
        }

        if (isBlocked) {
            Log.w(TAG, "Detección bloqueada: $currentPackage ($currentClass). Redirigiondo al Home...")
            playCastigoSound()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        }
    }*/
    private fun goHome(currentPackage: String, currentClass: String?) {
        var isBlocked = false

        // 1. BLOQUEO PERMANENTE: Google Play Store nunca se permite, ni en horario libre
        if (currentPackage == "com.android.vending") {
            Log.w(TAG, "Intento de abrir Google Play Store bloqueado permanentemente.")
            isBlocked = true
        }
        // 2. Detección quirúrgica dentro de Ajustes (com.android.settings)
        else if (currentPackage == "com.android.settings" && isUnlockedByAdmin.not()) {
            val isDeviceAdminScreen =
                currentClass?.contains("DeviceAdminSettings", ignoreCase = true) == true ||
                        currentClass?.contains("DeviceAdminAdd", ignoreCase = true) == true ||
                        currentClass?.contains("SubSettings", ignoreCase = true) == true

            if (isDeviceAdminScreen) {
                Log.w(TAG, "Intento de entrar a Administración de Dispositivos detectado: $currentClass")
                isBlocked = true
            }
        }
        // 3. Bloqueo condicional para el resto de la lista negra (evalúa horario libre)
        else if (blackList.contains(currentPackage)) {

            // --- EVALUACIÓN DE HORARIO LIBRE USANDO REALTIME DATABASE ---
            val (isFreeTime, remainingMinutes) = checkFreeTimeWindow()

            if (isFreeTime) {
                Log.d(TAG, "Horario libre activo para $currentPackage. Minutos restantes: $remainingMinutes")

                if (lastNotifiedPackage != currentPackage) {
                    lastNotifiedPackage = currentPackage
                    speakRemainingTime(remainingMinutes)
                }
                isBlocked = false // Juegos permitidos solo si hay horario libre
            } else {
                lastNotifiedPackage = null
                isBlocked = true // Fuera de horario libre -> Bloqueado
            }
        } else {
            lastNotifiedPackage = null
        }

        if (isBlocked) {
            Log.w(TAG, "Detección bloqueada: $currentPackage ($currentClass). Redirigiendo al Home...")
            playCastigoSound()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        }
    }

    // --- PARSEA EL JSON OBTENIDO DE REALTIME DATABASE ---
    private fun checkFreeTimeWindow(): Pair<Boolean, Int> {
        return try {
            val jsonString = currentTemplateJson
            if (jsonString.isEmpty()) return Pair(false, 0)

            val json = JSONObject(jsonString)
            val template = Template(
                start_time = json.getString("start_time"),
                end_time = json.getString("end_time"),
                is_active = json.optBoolean("is_active", false)
            )

            if (!template.is_active) return Pair(false, 0)

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentTotalMinutes = currentHour * 60 + currentMinute

            val startParts = template.start_time.split(":")
            val startTotalMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()

            val endParts = template.end_time.split(":")
            val endTotalMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()

            if (currentTotalMinutes in startTotalMinutes until endTotalMinutes) {
                val remainingMinutes = endTotalMinutes - currentTotalMinutes
                Pair(true, remainingMinutes)
            } else {
                Pair(false, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando el horario libre", e)
            Pair(false, 0)
        }
    }

    private fun speakRemainingTime(minutes: Int) {
        if (isTtsReady) {
            val message = if (minutes == 1) "Te queda $minutes minuto" else "Te quedan $minutes minutos"
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun startForegroundService() {
        val channelId = "app_guard_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Guardia de Aplicaciones", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Protección activa")
            .setContentText("Supervisando ejecución de aplicaciones...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun playCastigoSound() {
        try {
            val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.madrina)
            mediaPlayer.setOnCompletionListener { mp -> mp.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sonido: ${e.message}")
        }
    }

    override fun onDestroy() {
        // Remover el listener de la base de datos al destruir el servicio
        if (databaseRef != null && databaseListener != null) {
            databaseRef?.removeEventListener(databaseListener!!)
        }

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
        stopAppMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
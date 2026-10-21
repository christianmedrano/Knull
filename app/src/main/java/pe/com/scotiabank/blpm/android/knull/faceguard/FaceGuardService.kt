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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.com.scotiabank.blpm.android.knull.BlockerActivity
import pe.com.scotiabank.blpm.android.knull.R

class FaceGuardService : LifecycleService() {

    private val TAG = "FaceGuardService"

    companion object {
        var isUnlockedByAdmin: Boolean = false
    }

    // Blacklist de paquetes que se cerrarán forzosamente al pasar a primer plano
    private val blackList = setOf(
        // Solicitados
        "com.roblox.client",                      // Roblox
        "com.pokemon.pokemontcgp",               // Pokémon TCG Pocket
        "jp.pokemon.pokemontcgp",

        // Juegos de disparos / Battle Royale populares
        "com.epicgames.fortnite",                 // Fortnite
        "com.dts.freefireth",                     // Free Fire
        "com.dts.freefiremax",                    // Free Fire MAX
        "com.tencent.ig",                         // PUBG Mobile
        "com.activision.callofduty.shooter",      // Call of Duty: Mobile

        // Juegos de Supercell (Muy jugados a los 12 años)
        "com.supercell.brawlstars",               // Brawl Stars
        "com.supercell.clashroyale",              // Clash Royale
        "com.supercell.clashofclans",             // Clash of Clans
        "com.supercell.squad",                    // Squad Busters

        // Bloques / Aventura / Sandbox
        "com.mojang.minecraftpe",                 // Minecraft
        "com.miHoYo.GenshinImpact",               // Genshin Impact
        "com.HoYoverse.hkrpgoversea",             // Honkai: Star Rail

        // Juegos casuales / Arcade en tendencia
        "com.kiloo.subwaysurf",                   // Subway Surfers
        "com.outfit7.talkingtomgoldrun",          // Talking Tom Gold Run
        "com.vNG.Roblox",                          // Roblox (variante de región si aplica)



        "com.innersloth.spacemafia",               // Among Us

        // --- Juegos tipo Minecraft (Clones y Alternativas) ---
        "com.craftsman.go",                        // Craftsman: Building Craft
        "com.akav.lokicraft",                      // LokiCraft
        "com.multicraft.game",                     // MultiCraft
        "com.fungames.blockcraft",                 // Block Craft 3D
        "com.exploration.master.craft",            // Master Craft
        "com.and.games505.TerrariaPaid",           // Terraria (Estilo similar)

        // --- Ya existentes en tu lista ---
        "com.roblox.client",                      // Roblox
        "com.pokemon.pokemontcgp",               // Pokémon TCG Pocket
        "jp.pokemon.pokemontcgp",
        "com.epicgames.fortnite",                 // Fortnite
        "com.dts.freefireth",                     // Free Fire
        "com.dts.freefiremax",                    // Free Fire MAX
        "com.tencent.ig",                         // PUBG Mobile
        "com.activision.callofduty.shooter",      // Call of Duty: Mobile
        "com.supercell.brawlstars",               // Brawl Stars
        "com.supercell.clashroyale",              // Clash Royale
        "com.supercell.clashofclans",             // Clash of Clans
        "com.supercell.squad",                    // Squad Busters
        "com.mojang.minecraftpe",                 // Minecraft (Original)
        "com.miHoYo.GenshinImpact",               // Genshin Impact
        "com.HoYoverse.hkrpgoversea",             // Honkai: Star Rail
        "com.kiloo.subwaysurf",                   // Subway Surfers
        "com.outfit7.talkingtomgoldrun",          // Talking Tom Gold Run
        "com.vNG.Roblox",                          // Roblox (variante)

        // Google Chrome y Google Play Store
        "com.android.chrome",
        "com.android.vending",

        // Google Gemini
        //"com.google.android.googlequicksearchbox",

        // YouTube y variantes
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",

        // Tienda y Navegador de Samsung
        "com.sec.android.app.samsungapps",
        "com.sec.android.app.sbrowser",

        // Google Drive
        "com.google.android.apps.docs",

        //"com.android.settings",

        "com.google.android.googlequicksearchbox",
    )

    private var monitorJob: Job? = null

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
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)

        // Iniciar monitoreo inmediatamente al crearse el servicio (e.g. al encender el teléfono)
        startAppMonitoring()
    }

    /*private fun startAppMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Bucle de monitoreo iniciado.")
            while (isActive) {
                val currentPackage = getForegroundPackage()

                if (currentPackage != null && blackList.contains(currentPackage)) {
                    Log.w(TAG, "Detección en Foreground: $currentPackage. Ejecutando force-stop...")
                    //forceStopPackage(currentPackage)
                    goHome(currentPackage)
                }

                delay(500)
            }
        }
    }*/
    private fun startAppMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Bucle de monitoreo iniciado.")
            while (isActive) {
                val (currentPackage, currentClass) = getForegroundPackageAndClass()

                if (currentPackage != null) {
                    goHome(currentPackage, currentClass)
                }

                delay(500)
            }
        }
    }

    private fun stopAppMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Consulta el paquete actualmente enfocado en pantalla usando comandos Root Shell.
     */
    /*private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        // Consultar el historial de uso en la última ventana de 5 segundos
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 5000,
            time
        )
        if (!stats.isNullOrEmpty()) {
            val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
            return mostRecent?.packageName
        }
        return null
    }*/
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

    /*private fun goHome(currentPackage: String) {
        if (currentPackage != null && blackList.contains(currentPackage)) {
            Log.w(TAG, "App bloqueada detectada: $currentPackage. Redirigiendo al Home...")

            playCastigoSound()

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
    }*/
    private fun goHome(currentPackage: String, currentClass: String?) {
        var isBlocked = false

        // 1. Detección quirúrgica dentro de Ajustes (com.android.settings)
        if (currentPackage == "com.android.settings" && isUnlockedByAdmin.not()) {
            val isDeviceAdminScreen = //currentClass?.contains("DeviceAdmin", ignoreCase = true) == true ||
                    currentClass?.contains("DeviceAdminSettings", ignoreCase = true) == true ||
                    currentClass?.contains("DeviceAdminAdd", ignoreCase = true) == true ||
                    currentClass?.contains("SubSettings", ignoreCase = true) == true
                    //currentClass?.contains("SecuritySettings", ignoreCase = true) == true

            if (isDeviceAdminScreen) {
                Log.w(TAG, "Intento de entrar a Administración de Dispositivos detectado: $currentClass")
                isBlocked = true
            }
        }
        // 2. Bloqueo normal para el resto de la lista negra
        else if (blackList.contains(currentPackage)) {
            isBlocked = true
        }

        // Si cae en bloqueo, reproducimos sonido y mandamos al Home
        if (isBlocked) {
            Log.w(TAG, "Detección bloqueada: $currentPackage ($currentClass). Redirigiendo al Home...")

            playCastigoSound()

            /*val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)*/
            /*val blockerIntent = Intent(applicationContext, BlockerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK ora
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            startActivity(blockerIntent)*/
            forceStopPackage(currentPackage)
        }
    }

    private fun forceStopPackage(targetPackage: String) {
        try {
            // Ejecutar su pasando un Array asegura que los argumentos no se corrompan en la Shell
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $targetPackage"))
            process.waitFor()
            Log.d(TAG, "Cierre forzado ejecutado con éxito para: $targetPackage")
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando force-stop para $targetPackage", e)
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

        // Pasa siempre DATA_SYNC en API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun playCastigoSound() {
        try {
            // R.raw.castigo es el archivo en res/raw/castigo.mp3
            val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.castigo)
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release() // Liberar recursos cuando termine de sonar
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sonido: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAppMonitoring()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
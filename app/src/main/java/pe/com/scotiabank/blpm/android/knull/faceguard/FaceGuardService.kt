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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.com.scotiabank.blpm.android.knull.R

class FaceGuardService : LifecycleService() {

    private val TAG = "FaceGuardService"

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

        "com.android.chrome",                      // Google Chrome
        "com.android.vending",                     // Google Play Store
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

    private fun startAppMonitoring() {
        if (monitorJob?.isActive == true) return

        monitorJob = lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Bucle de monitoreo iniciado.")
            while (isActive) {
                val currentPackage = getForegroundPackage() // o getForegroundPackageWithRoot()

                if (currentPackage != null && blackList.contains(currentPackage)) {
                    Log.w(TAG, "Detección en Foreground: $currentPackage. Ejecutando force-stop...")
                    //forceStopPackage(currentPackage)
                    goHome(currentPackage)
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
    private fun getForegroundPackage(): String? {
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
    }

    /*private fun forceStopPackage(packageName: String): Boolean {
        return try {
            // Cierre directo usando 'su -c'
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error al ejecutar am force-stop sobre $packageName: ${e.message}")
            false
        }
    }*/
    private fun goHome(currentPackage: String) {
        if (currentPackage != null && blackList.contains(currentPackage)) {
            Log.w(TAG, "App bloqueada detectada: $currentPackage. Redirigiendo al Home...")

            playCastigoSound()

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
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
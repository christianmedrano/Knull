package pe.com.scotiabank.blpm.android.knull.faceguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import android.media.MediaPlayer
import pe.com.scotiabank.blpm.android.knull.R

class AppAccessibilityService : AccessibilityService() {

    private val TAG = "AppAccessibilityService"

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

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Forzamos la configuración directamente por código para que Samsung One UI la acepte al instante
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 0
        }
        serviceInfo = info

        Log.e(TAG, "¡Servicio de accesibilidad conectado y configurado por código exitosamente!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        Log.d(TAG, "Evento detectado -> Paquete: $packageName | Clase: $className")

        var isBlocked = false

        // Bloqueo quirúrgico para Ajustes / Admin de dispositivos
        if (packageName == "com.android.settings") {
            if (className.contains("DeviceAdmin", ignoreCase = true) ||
                className.contains("DeviceAdminAdd", ignoreCase = true) ||
                className.contains("DeviceAdminSettings", ignoreCase = true) ||
                className.contains("SecDeviceAdmin", ignoreCase = true)) {
                isBlocked = true
            }
        }
        // Bloqueo para la lista negra de apps y juegos
        else if (blackList.contains(packageName)) {
            isBlocked = true
        }

        if (isBlocked) {
            Log.w(TAG, "¡BLOQUEADO!: $packageName ($className)")

            // 1. Reproducir sonido de castigo
            playCastigoSound()

            // 2. Forzar el regreso al escritorio de manera instantánea (cierra pantallas completas y ventanas flotantes)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun playCastigoSound() {
        try {
            val mediaPlayer = MediaPlayer.create(applicationContext, R.raw.castigo)
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir el sonido de castigo", e)
        }
    }

    override fun onInterrupt() {
        // Requerido por la interfaz
    }
}
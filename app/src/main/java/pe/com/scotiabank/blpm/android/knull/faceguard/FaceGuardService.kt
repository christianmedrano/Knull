package pe.com.scotiabank.blpm.android.knull.faceguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import org.json.JSONObject
import pe.com.scotiabank.blpm.android.knull.R
import java.util.Calendar
import java.util.Locale

class FaceGuardService : AccessibilityService(), TextToSpeech.OnInitListener {

    private val TAG = "FaceGuardService"

    companion object {
        var isUnlockedByAdmin: Boolean = false
        // Caché global en memoria sincronizada al instante desde Realtime Database
        var currentTemplateJson: String = "{\"start_time\":\"17:00\",\"end_time\":\"17:30\",\"is_active\":true}"
    }

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
        //"com.google.android.apps.docs",
        "com.google.android.googlequicksearchbox",
        "com.good.boy.education.craft.best.building"
    )

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastNotifiedPackage: String? = null

    private var databaseRef: DatabaseReference? = null
    private var databaseListener: ValueEventListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de Accesibilidad conectado e iniciado con éxito.")
        tts = TextToSpeech(this, this)
        setupRealtimeDatabase()
    }

    private fun setupRealtimeDatabase() {
        try {
            databaseRef = Firebase.database.reference.child("template")

            databaseListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val startTime = snapshot.child("start_time").getValue(String::class.java) ?: "17:00"
                    val endTime = snapshot.child("end_time").getValue(String::class.java) ?: "17:30"
                    val isActive = snapshot.child("is_active").getValue(Boolean::class.java) ?: false

                    currentTemplateJson = "{\"start_time\":\"$startTime\",\"end_time\":\"$endTime\",\"is_active\":$isActive}"
                    Log.d(TAG, "Template actualizado desde nodos de Firebase: $currentTemplateJson")
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    // --- MOTOR DE ACCESIBILIDAD: El sistema llama a esto instantáneamente al cambiar de app ---
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPackage = event.packageName?.toString() ?: return
            val currentClass = event.className?.toString()

            goHome(currentPackage, currentClass)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "El Servicio de Accesibilidad ha sido interrumpido.")
    }

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
        if (databaseRef != null && databaseListener != null) {
            databaseRef?.removeEventListener(databaseListener!!)
        }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
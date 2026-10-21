package pe.com.scotiabank.blpm.android.knull.faceguard

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Este mensaje aparece en la pantalla de confirmación de Samsung cuando intentan desactivarlo
        return "¡WILLY ESTAS CASTIGADO!\nSI LO DESACTIVAS EL CASTIGO SERÁ PEOR Y POR MÁS TIEMPO"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)

        // 1. Verificar si el usuario ingresó el código correcto en la app
        val prefs = context.getSharedPreferences("FaceGuardPrefs", Context.MODE_PRIVATE)
        val isUnlockedByAdmin = prefs.getBoolean("unlocked_by_admin", false)

        if (!isUnlockedByAdmin) {
            // 2. Si NO colocó el código, forzamos la reactivación reabriendo la pantalla de Device Admin
            val intentReactivate = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    getComponentName(context)
                )
                putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Reactivación obligatoria: Se requiere el código de seguridad."
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intentReactivate)

            Toast.makeText(context, "¡Acceso denegado! Reactivando protección...", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, AdminReceiver::class.java)
        }
    }
}
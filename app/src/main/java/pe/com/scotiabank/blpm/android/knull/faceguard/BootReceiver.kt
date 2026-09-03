package pe.com.scotiabank.blpm.android.knull.faceguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import pe.com.scotiabank.blpm.android.knull.InvisibleBootActivity

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val UNIQUE_WORK_NAME = "FaceGuardBootWork"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "Evento BOOT_COMPLETED recibido. Lanzando Activity invisible...")

            // 1. Abrir la Activity invisible para ganar estado de Foreground en Android 14
            val bootActivityIntent = Intent(context, InvisibleBootActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(bootActivityIntent)

            // 2. Opción opcional: Mantener WorkManager solo para tareas secundarias en background
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<FaceGuardWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
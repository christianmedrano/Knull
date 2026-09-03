package pe.com.scotiabank.blpm.android.knull.faceguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FaceGuardWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FaceGuardWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Ejecutando FaceGuardWorker...")

        // 1. Verificar si tenemos los permisos necesarios (Cámara)
        if (!hasCameraPermission()) {
            Log.w(TAG, "No se ejecutó el análisis facial: Permiso de CÁMARA no concedido.")
            // Retornamos éxito para no reintentar infinitamente si el usuario aún no otorgó el permiso
            return Result.success()
        }

        return try {
            // 2. Ejecutar únicamente las tareas de fondo de preparación o sincronización
            performBackgroundFaceVerification()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante la ejecución en FaceGuardWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun performBackgroundFaceVerification() {
        // Lógica background personalizada o llamada a la lógica de negocio/repositorio
        Log.d(TAG, "Tarea de verificación en background finalizada.")
    }
}
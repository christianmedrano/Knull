package pe.com.scotiabank.blpm.android.knull

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import pe.com.scotiabank.blpm.android.knull.faceguard.AdminReceiver
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardService
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardWorker
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceMatcher
import pe.com.scotiabank.blpm.android.knull.faceguard.FacePreloader
import kotlin.ranges.coerceIn

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${packageName}")
            )
            startActivity(intent)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        onEnableAdmin = { requestDeviceAdmin() },
                        onStartService = { startGuardService() }
                    )
                }
            }
        }
    }

    private fun requestDeviceAdmin() {
        val componentName = ComponentName(this, AdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permite bloquear la pantalla si se detecta uso no autorizado.")
        }
        startActivity(intent)
    }

    private fun startGuardService() {
        val intent = Intent(this, FaceGuardService::class.java)
        startService(intent)
    }
}

@Composable
fun HomeScreen(
    onEnableAdmin: () -> Unit,
    onStartService: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Ninguna foto cargada") }

    // Selector nativo de fotos de Android
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            statusText = "Analizando foto..."
            try {
                // 1. Convertir la URI de la galería a un Bitmap
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true // Para que sea modificable
                    }
                } else {
                    @Suppress("DEPRECATION")
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                // 2. Llamar a la función que recorta el rostro y guarda el embedding
                procesarFotoGaleria(context, bitmap)

                statusText = "Procesamiento de galería completado"
            } catch (e: Exception) {
                statusText = "Error al cargar imagen: ${e.message}"
                Toast.makeText(context, "Error al abrir la foto", Toast.LENGTH_SHORT).show()
            }
        } else {
            statusText = "Selección de foto cancelada"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Guardia Facial", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onEnableAdmin) {
            Text("1. Activar Permiso de Bloqueo")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Text("2. Seleccionar Foto de la Galería")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = statusText, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para consultar la cantidad de fotos cargadas
        Button(onClick = {
            val total = FaceMatcher.getSavedEmbeddingsCount(context)
            val msg = "Fotos/rostros registrados actualmente: $total"
            statusText = msg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }) {
            Text("Consultar Rostros Registrados")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón para limpiar los registros de SharedPreferences
        Button(onClick = {
            context.getSharedPreferences("face_guard_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            val msg = "Todos los registros de rostros han sido eliminados"
            statusText = msg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }) {
            Text("Limpiar Todos los Registros")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onStartService) {
            Text("3. Iniciar Servicio en Segundo Plano")
        }
    }
}

fun procesarFotoGaleria(context: Context, bitmapGaleria: Bitmap) {
    // 1. Configurar el detector de rostros (igual que en el analizador)
    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .build()
    val detector = FaceDetection.getClient(options)
    val image = InputImage.fromBitmap(bitmapGaleria, 0)

    detector.process(image)
        .addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val face = faces.first()

                // 2. Recortar el rostro del bitmap de la galería
                // Nota: Asegúrate de tener la función cropFace disponible (la que definimos antes)
                val croppedFace = cropFace(bitmapGaleria, face.boundingBox)

                if (croppedFace != null) {
                    // 3. Guardar el embedding usando FaceMatcher
                    FaceMatcher.saveBlockedFace(context, face, croppedFace)
                    Toast.makeText(context, "Rostro de galería registrado con éxito", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No se detectó ningún rostro en la foto", Toast.LENGTH_SHORT).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, "Error al procesar imagen de galería", Toast.LENGTH_SHORT).show()
        }
}

// Función auxiliar de recorte (puedes ponerla aquí o en un archivo de Utils)
private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
    return try {
        val left = boundingBox.left.coerceIn(0, bitmap.width - 1)
        val top = boundingBox.top.coerceIn(0, bitmap.height - 1)
        val width = boundingBox.width().coerceAtMost(bitmap.width - left)
        val height = boundingBox.height().coerceAtMost(bitmap.height - top)
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } catch (e: Exception) {
        null
    }
}

fun triggerFaceGuardWorkerManually(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<FaceGuardWorker>().build()
    WorkManager.getInstance(context).enqueue(workRequest)
}
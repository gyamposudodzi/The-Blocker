package com.theblocker.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.theblocker.app.accessibility.BlockerAccessibilityService
import com.theblocker.app.data.SettingsRepository
import com.theblocker.app.vpn.LocalVpnService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BlockerApp(settingsRepository)
                }
            }
        }
    }
}

@Composable
fun BlockerApp(settingsRepository: SettingsRepository) {
    val savedPin = settingsRepository.getPin()
    var isUnlocked by remember { mutableStateOf(savedPin == null) }

    if (!isUnlocked && savedPin != null) {
        PinEntryScreen(
            correctPin = savedPin,
            onUnlock = { isUnlocked = true }
        )
    } else {
        DashboardScreen(settingsRepository)
    }
}

@Composable
fun PinEntryScreen(correctPin: String, onUnlock: () -> Unit) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("App Locked", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = enteredPin,
            onValueChange = { enteredPin = it; isError = false },
            label = { Text("Enter PIN") },
            isError = isError,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (enteredPin == correctPin) {
                onUnlock()
            } else {
                isError = true
            }
        }) {
            Text("Unlock")
        }

        if (isError) {
            Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DashboardScreen(settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVpnActive by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }
    var currentPin by remember { mutableStateOf(settingsRepository.getPin() ?: "") }
    var pinSetupInput by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_START
            }
            context.startForegroundService(intent)
            isVpnActive = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "The Blocker Dashboard", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isVpnActive) "Protection: On" else "Protection: Off",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isVpnActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPermissionLauncher.launch(vpnIntent)
            } else {
                val intent = Intent(context, LocalVpnService::class.java).apply {
                    action = LocalVpnService.ACTION_START
                }
                context.startForegroundService(intent)
                isVpnActive = true
            }
        }) {
            Text(text = "Enable Protection")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val intent = Intent(context, LocalVpnService::class.java).apply {
                action = LocalVpnService.ACTION_STOP
            }
            context.startService(intent)
            isVpnActive = false
        }) {
            Text(text = "Disable Protection")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 32.dp))

        Text(text = "Anti-Tamper", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAccessibilityEnabled) {
                "Anti-tamper: Enabled"
            } else {
                "Anti-tamper: Disabled — enable in Accessibility settings"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isAccessibilityEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) {
            Text(if (isAccessibilityEnabled) "Open Accessibility Settings" else "Enable Anti-Tamper")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 32.dp))

        Text(text = "Security Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        if (currentPin.isEmpty()) {
            OutlinedTextField(
                value = pinSetupInput,
                onValueChange = { pinSetupInput = it },
                label = { Text("New PIN") },
                singleLine = true
            )
            Button(onClick = {
                if (pinSetupInput.isNotEmpty()) {
                    settingsRepository.setPin(pinSetupInput)
                    currentPin = pinSetupInput
                }
            }) {
                Text("Set View PIN")
            }
        } else {
            Text("PIN is active.")
            Button(onClick = {
                settingsRepository.setPin(null)
                currentPin = ""
                pinSetupInput = ""
            }) {
                Text("Remove PIN")
            }
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val expected = "${context.packageName}/${BlockerAccessibilityService::class.java.name}"
    return enabled.any { it.id.equals(expected, ignoreCase = true) }
}

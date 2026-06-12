package com.example.runtraining.ui.options

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runtraining.ble.HrmClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onBack: () -> Unit,
    viewModel: OptionsViewModel = viewModel(factory = OptionsViewModel.Factory),
) {
    val settings by viewModel.state.collectAsState()
    val hrmState by viewModel.hrmConnectionState.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val liveSample by viewModel.liveHrSample.collectAsState()
    var text by remember { mutableStateOf(viewModel.formatThresholdPaceText(settings.thresholdPaceSecPerKm)) }
    var confirmOutOfRange by remember { mutableStateOf<Int?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val btPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.all { it }
        permissionDenied = !granted
        if (granted) viewModel.startScan()
    }

    LaunchedEffect(settings.thresholdPaceSecPerKm) {
        // Re-sync the field if the setting changes outside this screen.
        text = viewModel.formatThresholdPaceText(settings.thresholdPaceSecPerKm)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Threshold pace",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Used to compute TSS for every workout. Format: m:ss / km (e.g. 4:30).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Threshold pace (m:ss/km)") },
                placeholder = { Text("4:30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val parsed = viewModel.parseThresholdPaceText(text)
                    if (parsed == null) {
                        // Bad input — keep field for the user to fix.
                        return@Button
                    }
                    if (parsed !in 180..600) {
                        // Outside 3:00..10:00 — confirm.
                        confirmOutOfRange = parsed
                    } else {
                        viewModel.saveThresholdPace(parsed)
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = {
                    text = ""
                    viewModel.clearThresholdPace()
                }) { Text("Clear") }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Display unit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How target intensities are shown across the app.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.displayUnit == com.example.runtraining.settings.DisplayUnit.PACE,
                    onClick = { viewModel.setDisplayUnit(com.example.runtraining.settings.DisplayUnit.PACE) },
                    label = { Text("Pace (min:ss/km)") },
                )
                FilterChip(
                    selected = settings.displayUnit == com.example.runtraining.settings.DisplayUnit.SPEED,
                    onClick = { viewModel.setDisplayUnit(com.example.runtraining.settings.DisplayUnit.SPEED) },
                    label = { Text("Speed (km/h)") },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Heart-rate monitor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (hrmState) {
                    HrmClient.ConnectionState.CONNECTED ->
                        "Connected" + (liveSample?.let { " · ${it.bpm} bpm" } ?: "")
                    HrmClient.ConnectionState.CONNECTING -> "Connecting…"
                    HrmClient.ConnectionState.SCANNING -> "Scanning for devices…"
                    HrmClient.ConnectionState.BLUETOOTH_OFF -> "Bluetooth is off."
                    HrmClient.ConnectionState.PERMISSION_MISSING ->
                        "Nearby Devices permission required."
                    HrmClient.ConnectionState.DISCONNECTED ->
                        if (settings.lastPairedDeviceId != null)
                            "Not connected · last paired ${settings.lastPairedDeviceId}"
                        else "No monitor paired."
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            if (permissionDenied || hrmState == HrmClient.ConnectionState.PERMISSION_MISSING) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Heart-rate monitor needs Nearby Devices permission. " +
                                "The rest of the app keeps working.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }) { Text("Open app info") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hrmState == HrmClient.ConnectionState.SCANNING) {
                    OutlinedButton(onClick = { viewModel.stopScan() }) { Text("Stop scan") }
                } else {
                    Button(onClick = {
                        permissionDenied = false
                        permLauncher.launch(btPermissions)
                    }) { Text("Scan for HRM") }
                }
                if (settings.lastPairedDeviceId != null) {
                    OutlinedButton(onClick = { viewModel.forget() }) { Text("Forget") }
                }
            }

            if (scanResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Devices found",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                for (d in scanResults) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = d.name ?: "Unknown device",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${d.deviceId} · ${d.rssi} dBm",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Button(onClick = { viewModel.connect(d) }) { Text("Connect") }
                        }
                    }
                }
            }
            Box(modifier = Modifier.height(32.dp))
        }
    }

    val pendingOOR = confirmOutOfRange
    if (pendingOOR != null) {
        AlertDialog(
            onDismissRequest = { confirmOutOfRange = null },
            title = { Text("Unusual threshold pace") },
            text = {
                Text(
                    text = "${viewModel.formatThresholdPaceText(pendingOOR)} /km is outside the typical " +
                        "3:00–10:00 range. Save anyway?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveThresholdPace(pendingOOR)
                    confirmOutOfRange = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOutOfRange = null }) { Text("Cancel") }
            },
        )
    }
}

package com.questor.smsgateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questor.smsgateway.data.model.GatewaySettings
import com.questor.smsgateway.data.model.TransportMode
import com.questor.smsgateway.ui.theme.SuccessGreen

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val context = LocalContext.current

    var portText by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var keyText by remember(settings.gatewayKey) { mutableStateOf(settings.gatewayKey) }
    var selectedTransport by remember(settings.activeTransport) { mutableStateOf(settings.activeTransport) }
    var selectedSimSlot by remember(settings.preferredSimSlot) { mutableIntStateOf(settings.preferredSimSlot) }
    var delaySlider by remember(settings.rateLimitDelayMs) { mutableFloatStateOf(settings.rateLimitDelayMs.toFloat()) }
    var autoBoot by remember(settings.autoStartOnBoot) { mutableStateOf(settings.autoStartOnBoot) }
    var wakeLock by remember(settings.acquireWakeLock) { mutableStateOf(settings.acquireWakeLock) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gateway Configuration",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Transport Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Active Transport Protocol",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTransport == TransportMode.USB_TETHER,
                        onClick = { selectedTransport = TransportMode.USB_TETHER },
                        label = { Text("USB Tether") }
                    )
                    FilterChip(
                        selected = selectedTransport == TransportMode.WIFI_LAN,
                        onClick = { selectedTransport = TransportMode.WIFI_LAN },
                        label = { Text("Wi-Fi / LAN") }
                    )
                    FilterChip(
                        selected = selectedTransport == TransportMode.BLUETOOTH,
                        onClick = { selectedTransport = TransportMode.BLUETOOTH },
                        label = { Text("Bluetooth") }
                    )
                }
            }
        }

        // Network & Security Parameters
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Server & Security Settings",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("HTTP & WebSocket Port") },
                    placeholder = { Text("8765") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Gateway Secret Key (Optional)") },
                    placeholder = { Text("Leave empty for open local access") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // SIM & Telephony Dispatching
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SMS Dispatching & SIM Slot",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSimSlot == 0,
                        onClick = { selectedSimSlot = 0 },
                        label = { Text("Default SIM") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 1,
                        onClick = { selectedSimSlot = 1 },
                        label = { Text("SIM 1") }
                    )
                    FilterChip(
                        selected = selectedSimSlot == 2,
                        onClick = { selectedSimSlot = 2 },
                        label = { Text("SIM 2") }
                    )
                }

                Text(
                    text = "Rate Limiter Delay: ${(delaySlider / 1000f).let { "%.1f".format(it) }}s between SMS",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = delaySlider,
                    onValueChange = { delaySlider = it },
                    valueRange = 500f..5000f,
                    steps = 8
                )
            }
        }

        // Power & Background Persistence
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Appliance Persistence & Power",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Auto-start on Device Boot", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoBoot, onCheckedChange = { autoBoot = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Acquire WakeLock & WifiLock", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = wakeLock, onCheckedChange = { wakeLock = it })
                }

                val isExempt = viewModel.isIgnoringBatteryOptimizations(context)
                OutlinedButton(
                    onClick = { viewModel.requestBatteryOptimizationExemption(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExempt) "Battery Optimization: Unrestricted ✓" else "Disable Battery Optimization (Recommended)"
                    )
                }
            }
        }

        // Save Button
        Button(
            onClick = {
                val parsedPort = portText.toIntOrNull() ?: 8765
                viewModel.updateSettings(
                    GatewaySettings(
                        serverPort = parsedPort,
                        gatewayKey = keyText.trim(),
                        activeTransport = selectedTransport,
                        preferredSimSlot = selectedSimSlot,
                        rateLimitDelayMs = delaySlider.toLong(),
                        autoStartOnBoot = autoBoot,
                        acquireWakeLock = wakeLock
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

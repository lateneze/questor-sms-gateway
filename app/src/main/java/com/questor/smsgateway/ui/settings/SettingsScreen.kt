package com.questor.smsgateway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questor.smsgateway.data.model.GatewaySettings
import com.questor.smsgateway.data.model.TransportMode
import com.questor.smsgateway.ui.theme.QuestorBlue
import com.questor.smsgateway.ui.theme.SuccessGreen
import com.questor.smsgateway.ui.theme.WarningYellow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var portText by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var keyText by remember(settings.gatewayKey) { mutableStateOf(settings.gatewayKey) }
    var selectedTransport by remember(settings.activeTransport) { mutableStateOf(settings.activeTransport) }
    var selectedSimSlot by remember(settings.preferredSimSlot) { mutableIntStateOf(settings.preferredSimSlot) }
    var delaySlider by remember(settings.rateLimitDelayMs) { mutableFloatStateOf(settings.rateLimitDelayMs.toFloat()) }
    var autoBoot by remember(settings.autoStartOnBoot) { mutableStateOf(settings.autoStartOnBoot) }
    var wakeLock by remember(settings.acquireWakeLock) { mutableStateOf(settings.acquireWakeLock) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Gateway Configuration",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Active Transport Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Active Transport Protocol",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose which transport listener is active on this phone for incoming commands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransportMode.values().forEach { mode ->
                            FilterChip(
                                selected = selectedTransport == mode,
                                onClick = { selectedTransport = mode },
                                label = { Text(mode.displayName) }
                            )
                        }
                    }

                    if (selectedTransport != settings.activeTransport) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    WarningYellow.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = WarningYellow,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "Transport switched to ${selectedTransport.displayName}. Tap 'Save Settings' below to apply changes.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // HTTP & Network Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Network & Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("HTTP & WebSocket Port") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { Text("Gateway Security API Key (Optional)") },
                        placeholder = { Text("Leave blank to allow unauthenticated LAN access") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // SIM & Dispatch Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SIM Preference & Throttling",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(text = "Preferred SIM Slot", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "System Default",
                            1 to "SIM 1",
                            2 to "SIM 2"
                        ).forEach { (slot, label) ->
                            FilterChip(
                                selected = selectedSimSlot == slot,
                                onClick = { selectedSimSlot = slot },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Inter-SMS Delay: ${delaySlider.toLong()} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = delaySlider,
                        onValueChange = { delaySlider = it },
                        valueRange = 0f..5000f,
                        steps = 9
                    )
                }
            }

            // Power & Resilience Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Background Resilience",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Auto-start Service on Device Boot", style = MaterialTheme.typography.bodyMedium)
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
                    val newSettings = GatewaySettings(
                        serverPort = parsedPort,
                        gatewayKey = keyText.trim(),
                        activeTransport = selectedTransport,
                        preferredSimSlot = selectedSimSlot,
                        rateLimitDelayMs = delaySlider.toLong(),
                        autoStartOnBoot = autoBoot,
                        acquireWakeLock = wakeLock
                    )
                    viewModel.updateSettings(context, newSettings)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Settings saved! Active Transport: ${selectedTransport.displayName}"
                        )
                    }
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
}

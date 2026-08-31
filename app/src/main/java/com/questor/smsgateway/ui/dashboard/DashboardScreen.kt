package com.questor.smsgateway.ui.dashboard

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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questor.smsgateway.ui.components.MetricCard
import com.questor.smsgateway.ui.components.SimCardView
import com.questor.smsgateway.ui.components.StatusBadge
import com.questor.smsgateway.ui.theme.DangerRed
import com.questor.smsgateway.ui.theme.QuestorBlue
import com.questor.smsgateway.ui.theme.SuccessGreen
import com.questor.smsgateway.ui.theme.WarningYellow

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showTestDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Questor SMS Gateway",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Dedicated SMS Appliance for Questor School Manager",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Service Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusBadge(isRunning = state.isServiceRunning)

                    Button(
                        onClick = { viewModel.toggleService(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isServiceRunning) DangerRed else SuccessGreen
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (state.isServiceRunning) "Stop" else "Start")
                    }
                }

                // Connection Endpoint Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = QuestorBlue,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "Transport: ${state.activeTransport}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "Endpoint URL: http://${state.localIp}:${state.serverPort}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = QuestorBlue
                        )
                        if (state.isWebSocketConnected) {
                            Text(
                                text = "● Questor PC WebSocket Connected (Live Stream Active)",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // SIM Cards Section
        Text(
            text = "Cellular SIM Status",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (state.simCards.isEmpty()) {
            Text(
                text = "No SIM card detected or permissions pending.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.simCards.forEach { sim ->
                SimCardView(
                    sim = sim,
                    isPreferred = (sim.slotIndex + 1) == state.preferredSimSlot || (state.preferredSimSlot == 0 && sim.slotIndex == 0)
                )
            }
        }

        // Statistics Cards Grid Header with Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Message Traffic Queue",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showResetDialog = true }) {
                    Text("Reset Stats", fontSize = 12.sp, color = QuestorBlue)
                }
                TextButton(onClick = { showClearHistoryDialog = true }) {
                    Text("Clear Records", fontSize = 12.sp, color = DangerRed)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Pending",
                value = state.pendingCount.toString(),
                accentColor = WarningYellow,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Sent",
                value = state.sentCount.toString(),
                accentColor = QuestorBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Delivered",
                value = state.deliveredCount.toString(),
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Inbound",
                value = state.inboundCount.toString(),
                accentColor = Color(0xFF6F42C1),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Failed",
                value = state.failedCount.toString(),
                accentColor = DangerRed,
                modifier = Modifier.weight(1f)
            )
        }

        // Test SMS Action Button
        OutlinedButton(
            onClick = { showTestDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Send Test SMS")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showTestDialog) {
        TestSmsDialog(
            onDismiss = { showTestDialog = false },
            onSend = { phone, msg ->
                viewModel.sendTestSms(phone, msg)
                showTestDialog = false
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = "Reset Message Queue Stats") },
            text = { Text("Are you sure you want to reset all queue statistics and message records?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetQueueStats()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuestorBlue)
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(text = "Clear Completed Message History") },
            text = { Text("This will purge old sent, delivered, and acknowledged messages to save storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCompletedHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Purge History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TestSmsDialog(
    onDismiss: () -> Unit,
    onSend: (phone: String, message: String) -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Test SMS from Questor Gateway") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Send Test SMS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Recipient Phone Number") },
                    placeholder = { Text("024XXXXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message Text") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (phone.isNotBlank()) onSend(phone, message) },
                enabled = phone.isNotBlank() && message.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

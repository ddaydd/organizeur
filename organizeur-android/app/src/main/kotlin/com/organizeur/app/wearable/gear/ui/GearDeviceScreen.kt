package com.organizeur.app.wearable.gear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.gear.sap.GearManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearDeviceScreen(
    state: GearManager.GearState,
    deviceInfo: GearManager.DeviceInfo,
    activeClock: String,
    logs: List<String>,
    connectedServices: List<String> = emptyList(),
    clockSettingsConnected: Boolean = false,
    onBack: () -> Unit,
    onSyncTime: () -> Unit,
    onDisconnect: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenWallpaper: () -> Unit = {},
    onOpenClocks: () -> Unit = {},
    onSendTestMessage: (String, String) -> Unit = { _, _ -> },
    onSendClockSettings: (Map<String, Any>) -> Unit = {},
) {
    val deviceName = when (state) {
        is GearManager.GearState.Connected -> state.deviceName
        is GearManager.GearState.Connecting -> "Connexion..."
        else -> "Gear"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName) },
                navigationIcon = {
                    IconButton(onClick = {
                        onDisconnect()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status + Device info
            item {
                GearStatusCard(state = state, deviceInfo = deviceInfo, activeClock = activeClock)
            }

            // Actions
            if (state is GearManager.GearState.Connected) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Actions", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onOpenClocks,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.WatchLater, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cadrans")
                                }
                                Button(
                                    onClick = onOpenWallpaper,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Wallpaper, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fond")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onSyncTime,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Synchroniser l'heure")
                            }
                        }
                    }
                }
            }

            // Test message
            if (state is GearManager.GearState.Connected && connectedServices.isNotEmpty()) {
                item {
                    TestMessageCard(
                        connectedServices = connectedServices,
                        onSend = onSendTestMessage,
                    )
                }
            }

            // Logs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Logs SAP", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Effacer")
                    }
                }
            }

            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestMessageCard(
    connectedServices: List<String>,
    onSend: (String, String) -> Unit,
) {
    var selectedService by remember { mutableStateOf(connectedServices.firstOrNull { it.contains("setting") } ?: connectedServices.first()) }
    var jsonText by remember { mutableStateOf("""{"msgId":""}""") }
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Test SAP", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Service selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selectedService.removePrefix("/system/"),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Service") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    connectedServices.forEach { svc ->
                        DropdownMenuItem(
                            text = { Text(svc.removePrefix("/system/")) },
                            onClick = { selectedService = svc; expanded = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // JSON input
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                label = { Text("JSON") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSend(selectedService, jsonText) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Envoyer")
            }
        }
    }
}

@Composable
private fun GearStatusCard(
    state: GearManager.GearState,
    deviceInfo: GearManager.DeviceInfo,
    activeClock: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is GearManager.GearState.Connected -> MaterialTheme.colorScheme.primaryContainer
                is GearManager.GearState.Error -> MaterialTheme.colorScheme.errorContainer
                is GearManager.GearState.Connecting -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state is GearManager.GearState.Connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = when (state) {
                        is GearManager.GearState.Disconnected -> "Deconnecte"
                        is GearManager.GearState.Connecting -> state.step
                        is GearManager.GearState.Connected -> "Connecte"
                        is GearManager.GearState.Error -> "Erreur: ${state.message}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Device info (when connected and info available)
            if (state is GearManager.GearState.Connected && deviceInfo.model.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "${deviceInfo.model} (${deviceInfo.modelName})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "FW: ${deviceInfo.firmware}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    if (activeClock.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Cadran",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = activeClock.substringAfterLast("."),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

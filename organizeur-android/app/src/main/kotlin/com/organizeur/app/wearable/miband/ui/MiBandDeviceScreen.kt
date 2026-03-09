package com.organizeur.app.wearable.miband.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.organizeur.app.wearable.model.BandData
import com.organizeur.app.wearable.model.BandState
import com.organizeur.app.wearable.model.DeviceInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MiBandDeviceScreen(
    state: BandState,
    bandData: BandData,
    deviceInfo: DeviceInfo,
    logs: List<String>,
    savedAuthKey: String = "",
    onBack: () -> Unit,
    onSetAuthKey: (String) -> Boolean,
    onAuthenticate: () -> Unit,
    onReadBattery: () -> Unit,
    onStartHeartRate: (Boolean) -> Unit,
    onStopHeartRate: () -> Unit,
    onSendNotification: () -> Unit,
    onEnableMusicControls: () -> Unit,
    onDisconnect: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenWatchface: () -> Unit = {},
) {
    var authKeyInput by remember { mutableStateOf(savedAuthKey) }
    var authKeyError by remember { mutableStateOf(false) }

    val isAuthenticated = state is BandState.Authenticated
    val isAuthenticating = state is BandState.Authenticating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceInfo.name.ifEmpty { "Device" }) },
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
            // Status
            item {
                StatusCard(state = state, deviceInfo = deviceInfo)
            }

            // Auth section
            if (!isAuthenticated) {
                item {
                    AuthCard(
                        authKeyInput = authKeyInput,
                        authKeyError = authKeyError,
                        isAuthenticating = isAuthenticating,
                        onAuthKeyChange = {
                            authKeyInput = it
                            authKeyError = false
                        },
                        onAuthenticate = {
                            if (onSetAuthKey(authKeyInput)) {
                                onAuthenticate()
                            } else {
                                authKeyError = true
                            }
                        },
                    )
                }
            }

            // Data dashboard
            if (isAuthenticated) {
                item {
                    DataDashboard(
                        bandData = bandData,
                        onReadBattery = onReadBattery,
                        onStartHeartRate = onStartHeartRate,
                        onStopHeartRate = onStopHeartRate,
                        onSendNotification = onSendNotification,
                        onEnableMusicControls = onEnableMusicControls,
                        onOpenWatchface = onOpenWatchface,
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
                    Text("Logs BLE", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun StatusCard(state: BandState, deviceInfo: DeviceInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is BandState.Authenticated -> MaterialTheme.colorScheme.primaryContainer
                is BandState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (state) {
                    is BandState.Connected -> "Connecte"
                    is BandState.Authenticating -> "Authentification: ${state.step}"
                    is BandState.Authenticated -> "Authentifie"
                    is BandState.Error -> "Erreur: ${state.message}"
                    else -> "..."
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (deviceInfo.address.isNotEmpty()) {
                Text(
                    text = deviceInfo.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (deviceInfo.softwareRevision.isNotEmpty()) {
                Text(
                    text = "FW: ${deviceInfo.softwareRevision} | HW: ${deviceInfo.hardwareRevision}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AuthCard(
    authKeyInput: String,
    authKeyError: Boolean,
    isAuthenticating: Boolean,
    onAuthKeyChange: (String) -> Unit,
    onAuthenticate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Authentification", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Entrez la cle d'auth (32 caracteres hexadecimaux).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = authKeyInput,
                onValueChange = { input ->
                    val filtered = input.filter { it in "0123456789abcdefABCDEF" }.lowercase()
                    onAuthKeyChange(filtered)
                },
                label = { Text("Auth Key") },
                placeholder = { Text("0123456789abcdef0123456789abcdef") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                isError = authKeyError,
                supportingText = if (authKeyError) {
                    { Text("Cle invalide (32 hex chars)") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onAuthenticate,
                enabled = authKeyInput.isNotEmpty() && !isAuthenticating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isAuthenticating) "Authentification..." else "S'authentifier")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataDashboard(
    bandData: BandData,
    onReadBattery: () -> Unit,
    onStartHeartRate: (Boolean) -> Unit,
    onStopHeartRate: () -> Unit,
    onSendNotification: () -> Unit,
    onEnableMusicControls: () -> Unit,
    onOpenWatchface: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Donnees", style = MaterialTheme.typography.titleMedium)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Battery5Bar, contentDescription = null) },
                    label = { Text(bandData.batteryLevel?.let { "$it%" } ?: "--") },
                )
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                    label = { Text(bandData.heartRate?.let { "$it bpm" } ?: "--") },
                )
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null) },
                    label = { Text(bandData.steps?.let { "$it pas" } ?: "--") },
                )
                if (bandData.lastMusicEvent != null) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        label = { Text(bandData.lastMusicEvent.name) },
                    )
                }
            }

            Text("Actions", style = MaterialTheme.typography.titleSmall)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedButton(onClick = onReadBattery) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Batterie")
                }

                if (bandData.isHeartRateMonitoring) {
                    FilledTonalButton(onClick = onStopHeartRate) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop HR")
                    }
                } else {
                    ElevatedButton(onClick = { onStartHeartRate(false) }) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HR ponctuel")
                    }
                    ElevatedButton(onClick = { onStartHeartRate(true) }) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HR continu")
                    }
                }

                ElevatedButton(onClick = onSendNotification) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Vibrer")
                }

                ElevatedButton(onClick = onEnableMusicControls) {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Musique")
                }

                ElevatedButton(onClick = onOpenWatchface) {
                    Icon(Icons.Default.Watch, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cadran")
                }
            }
        }
    }
}

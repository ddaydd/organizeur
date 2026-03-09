package com.organizeur.app.wearable.miband.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.model.BandState
import com.organizeur.app.wearable.model.ScannedDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiBandScanScreen(
    state: BandState,
    devices: List<ScannedDevice>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val isScanning = state is BandState.Scanning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Band 4") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isScanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Scan en cours...")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onStopScan) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Arreter")
            }
        } else {
            Button(onClick = onStartScan) {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanner")
            }
        }

        if (state is BandState.Connecting) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Connexion a ${state.deviceName}...")
            }
        }

        if (state is BandState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isNotEmpty()) {
            Text(
                text = "${devices.size} appareil(s) trouve(s)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(devices, key = { it.address }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { onDeviceSelected(device.address) },
                )
            }
        }
    }
    } // Scaffold
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp),
                    tint = when {
                        device.rssi > -60 -> MaterialTheme.colorScheme.secondary
                        device.rssi > -80 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

package com.organizeur.app.wearable.gear.ui

import android.bluetooth.BluetoothDevice
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
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.gear.sap.GearManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearScanScreen(
    gearManager: GearManager,
    state: GearManager.GearState,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
) {
    var devices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Samsung Gear 1") },
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
        Icon(
            imageVector = Icons.Default.Watch,
            contentDescription = null,
            modifier = Modifier.height(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            onRequestPermissions()
            devices = gearManager.getBondedGearDevices()
        }) {
            Icon(Icons.Default.Bluetooth, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Appareils appaires")
        }

        if (state is GearManager.GearState.Connecting) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connexion: ${state.step}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state is GearManager.GearState.Error) {
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
                text = "${devices.size} Gear appairee(s)",
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
                GearDeviceCard(
                    device = device,
                    onClick = { onDeviceSelected(device) },
                    onUnpair = {
                        gearManager.unpairDevice(device)
                        devices = gearManager.getBondedGearDevices()
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun GearDeviceCard(
    device: BluetoothDevice,
    onClick: () -> Unit,
    onUnpair: () -> Unit,
) {
    val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
    val address = device.address

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = onUnpair) {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = "Dissocier",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

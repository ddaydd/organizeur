package com.organizeur.app.wearable.gear.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.gear.sap.GearFileTransfer
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearWallpaperScreen(
    transferState: GearFileTransfer.TransferState,
    onSendWallpaper: (imageData: ByteArray, fileName: String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var jpegData by remember { mutableStateOf<ByteArray?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(input)
                input?.close()
                if (original != null) {
                    // Center-crop to square then resize to 320x320 (Gear 1 resolution)
                    val size = minOf(original.width, original.height)
                    val x = (original.width - size) / 2
                    val y = (original.height - size) / 2
                    val cropped = Bitmap.createBitmap(original, x, y, size, size)
                    val scaled = Bitmap.createScaledBitmap(cropped, 320, 320, true)
                    if (cropped != original) original.recycle()
                    if (scaled != cropped) cropped.recycle()
                    selectedBitmap = scaled
                    // Convert to JPEG
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                    jpegData = baos.toByteArray()
                }
            } catch (_: Exception) {
                selectedBitmap = null
                jpegData = null
            }
        }
    }

    val isSending = transferState is GearFileTransfer.TransferState.Sending

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fond d'ecran Gear") },
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
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Image preview
            val bitmap = selectedBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Apercu",
                    modifier = Modifier.size(200.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    "320x320 JPEG (${jpegData?.size ?: 0} octets)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Aucune image selectionnee",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Pick button
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Choisir une image")
            }

            // Send button
            Button(
                onClick = {
                    val data = jpegData
                    if (data != null) {
                        onSendWallpaper(data, "wallpaper.jpg")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = jpegData != null && !isSending,
            ) {
                Text("Envoyer")
            }

            // Progress / status
            when (transferState) {
                is GearFileTransfer.TransferState.Sending -> {
                    LinearProgressIndicator(
                        progress = { transferState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Envoi... ${(transferState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is GearFileTransfer.TransferState.Success -> {
                    Text(
                        "Transfert termine !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is GearFileTransfer.TransferState.Error -> {
                    Text(
                        "Erreur: ${transferState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is GearFileTransfer.TransferState.Idle -> {}
            }
        }
    }
}

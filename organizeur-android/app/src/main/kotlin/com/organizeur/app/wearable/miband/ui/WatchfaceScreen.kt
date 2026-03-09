package com.organizeur.app.wearable.miband.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.miband.watchface.WatchfaceBinary
import com.organizeur.app.wearable.miband.watchface.WatchfaceElement
import com.organizeur.app.wearable.miband.watchface.WatchfaceImage
import com.organizeur.app.wearable.miband.watchface.WatchfaceProject
import com.organizeur.app.wearable.miband.watchface.WatchfaceUploader

private const val TAG = "WatchfaceScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WatchfaceScreen(
    isConnected: Boolean,
    onBack: () -> Unit,
    onUpload: (ByteArray) -> Unit,
    uploadState: WatchfaceUploader.UploadState,
) {
    val context = LocalContext.current
    var project by remember {
        val savedFile = java.io.File(context.filesDir, "last_watchface.bin")
        if (savedFile.exists()) {
            val parsed = WatchfaceBinary.parse(savedFile.readBytes())
            if (parsed != null) {
                Log.d(TAG, "Restored saved watchface: ${parsed.elements.size} elements, ${parsed.images.size} images")
                mutableStateOf(parsed)
            } else {
                mutableStateOf(WatchfaceProject.createDefault())
            }
        } else {
            mutableStateOf(WatchfaceProject.createDefault())
        }
    }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // File picker for loading .bin (parse + edit)
    val binLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                Log.d(TAG, "Loaded .bin: ${bytes.size} bytes")
                // Run round-trip test to compare original vs regenerated
                WatchfaceBinary.debugRoundTrip(bytes)
                val parsed = WatchfaceBinary.parse(bytes)
                if (parsed != null) {
                    Log.d(TAG, "Parsed: ${parsed.elements.size} elements, ${parsed.images.size} images")
                    project = parsed
                    selectedIndex = -1
                } else {
                    Log.e(TAG, "Failed to parse .bin")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading .bin: ${e.message}", e)
        }
    }

    // File picker for direct .bin upload (no parsing, raw bytes)
    val directBinLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                Log.d(TAG, "Direct upload .bin: ${bytes.size} bytes")
                onUpload(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading .bin for direct upload: ${e.message}", e)
        }
    }

    // Image picker for replacing an element's image
    var pendingImageIndex by remember { mutableIntStateOf(-1) }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (pendingImageIndex < 0) return@rememberLauncherForActivityResult
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            if (bitmap != null) {
                Log.d(TAG, "Image loaded: ${bitmap.width}x${bitmap.height}, pendingIdx=$pendingImageIndex")
                val element = project.elements.getOrNull(selectedIndex)
                val isBackground = element is WatchfaceElement.Background
                val finalBitmap = if (isBackground) {
                    // Background: crop+scale to fill exactly 120x240
                    WatchfaceImage.scaleBitmapToFill(bitmap,
                        WatchfaceProject.SCREEN_WIDTH, WatchfaceProject.SCREEN_HEIGHT)
                } else {
                    val maxW = 30
                    val maxH = 40
                    if (bitmap.width > maxW || bitmap.height > maxH) {
                        WatchfaceImage.scaleBitmapToFit(bitmap, maxW, maxH)
                    } else {
                        bitmap
                    }
                }
                Log.d(TAG, "Scaled to: ${finalBitmap.width}x${finalBitmap.height}")

                val newImages = ArrayList(project.images)
                if (pendingImageIndex < newImages.size) {
                    newImages[pendingImageIndex] = finalBitmap
                } else {
                    newImages.add(finalBitmap)
                }
                project = project.copy(images = newImages)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: ${e.message}", e)
        }
        pendingImageIndex = -1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editeur de cadran") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // Preview
            item {
                WatchfacePreview(
                    project = project,
                    selectedElementIndex = selectedIndex,
                )
            }

            // Actions bar
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ElevatedButton(onClick = { binLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Charger .bin")
                    }

                    if (isConnected) {
                        FilledTonalButton(
                            onClick = {
                                val data = WatchfaceBinary.generate(project)
                                try {
                                    java.io.File(context.filesDir, "last_watchface.bin").writeBytes(data)
                                    Log.d(TAG, "Saved watchface: ${data.size} bytes")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to save watchface: ${e.message}")
                                }
                                onUpload(data)
                            },
                            enabled = uploadState is WatchfaceUploader.UploadState.Idle ||
                                    uploadState is WatchfaceUploader.UploadState.Success ||
                                    uploadState is WatchfaceUploader.UploadState.Error,
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uploader")
                        }

                        FilledTonalButton(
                            onClick = { directBinLauncher.launch("*/*") },
                            enabled = uploadState is WatchfaceUploader.UploadState.Idle ||
                                    uploadState is WatchfaceUploader.UploadState.Success ||
                                    uploadState is WatchfaceUploader.UploadState.Error,
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload .bin direct")
                        }
                    }
                }
            }

            // Upload progress
            item {
                UploadStatusCard(uploadState)
            }

            // Element list header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Elements", style = MaterialTheme.typography.titleMedium)
                    AddElementMenu(
                        onAddElement = { element ->
                            val newImages = ArrayList(project.images)
                            val indexed = element.withImageIndex(newImages.size)
                            val defaultImages = WatchfaceImage.generateDefaultImages(element)
                            newImages.addAll(defaultImages)
                            val newElements = ArrayList(project.elements)
                            newElements.add(indexed)
                            project = project.copy(elements = newElements, images = newImages)
                            Log.d(TAG, "Added element: ${indexed.displayName}, imgIdx=${indexed.imageIndex}, +${defaultImages.size} images, total=${newElements.size}")
                        },
                    )
                }
            }

            // Element cards
            itemsIndexed(
                items = project.elements,
                key = { index, el -> "${el.displayName}-$index" },
            ) { index, element ->
                // For WeekDay, sample the first real day image (skip 14 dummies)
                val sampleImgIdx = if (element is WatchfaceElement.WeekDay)
                    element.imageIndex + 14 else element.imageIndex
                ElementCard(
                    element = element,
                    isSelected = index == selectedIndex,
                    currentImageHeight = project.images.getOrNull(sampleImgIdx)?.height ?: 24,
                    onSelect = { selectedIndex = if (selectedIndex == index) -1 else index },
                    onDelete = {
                        val newElements = ArrayList(project.elements)
                        newElements.removeAt(index)
                        project = project.copy(elements = newElements)
                        if (selectedIndex == index) selectedIndex = -1
                        else if (selectedIndex > index) selectedIndex--
                    },
                    onChangeImage = {
                        pendingImageIndex = element.imageIndex
                        selectedIndex = index
                        imageLauncher.launch("image/*")
                    },
                    onPositionChanged = { newX, newY ->
                        val newElements = ArrayList(project.elements)
                        newElements[index] = element.withPosition(newX, newY)
                        project = project.copy(elements = newElements)
                    },
                    onRegenerateImages = { newHeight ->
                        val newImgs = WatchfaceImage.generateDefaultImages(element, newHeight)
                        if (newImgs.isNotEmpty()) {
                            val newImages = ArrayList(project.images)
                            for (i in newImgs.indices) {
                                val imgIdx = element.imageIndex + i
                                if (imgIdx < newImages.size) {
                                    newImages[imgIdx] = newImgs[i]
                                }
                            }
                            project = project.copy(images = newImages)
                        }
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun UploadStatusCard(uploadState: WatchfaceUploader.UploadState) {
    when (uploadState) {
        is WatchfaceUploader.UploadState.Idle -> {}
        is WatchfaceUploader.UploadState.Preparing -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(uploadState.message)
                }
            }
        }
        is WatchfaceUploader.UploadState.Uploading -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Upload en cours...")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uploadState.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${(uploadState.progress * 100).toInt()}% - ${uploadState.bytesSent}/${uploadState.totalBytes} octets",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        is WatchfaceUploader.UploadState.Verifying -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Verification CRC...")
                }
            }
        }
        is WatchfaceUploader.UploadState.Success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text(
                    "Cadran uploade avec succes !",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        is WatchfaceUploader.UploadState.Error -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    uploadState.message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun ElementCard(
    element: WatchfaceElement,
    isSelected: Boolean,
    currentImageHeight: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onChangeImage: () -> Unit,
    onPositionChanged: (Int, Int) -> Unit,
    onRegenerateImages: (Int) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    element.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    IconButton(onClick = onChangeImage) {
                        Icon(Icons.Default.Image, contentDescription = "Changer image")
                    }
                    if (element !is WatchfaceElement.Background) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            Text(
                "Position: ${element.x}, ${element.y} | Images: ${element.imageIndex}..${element.imageIndex + element.imagesCount - 1}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))

                // Font size controls (not for Background)
                if (element !is WatchfaceElement.Background) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Taille:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                onRegenerateImages((currentImageHeight - 2).coerceAtLeast(12))
                            }) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "${currentImageHeight}px",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            IconButton(onClick = {
                                onRegenerateImages((currentImageHeight + 2).coerceAtMost(48))
                            }) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Position controls with +/- buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // X controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            onPositionChanged((element.x - 10).coerceAtLeast(0), element.y)
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "-10")
                        }
                        IconButton(onClick = {
                            onPositionChanged((element.x - 1).coerceAtLeast(0), element.y)
                        }) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${element.x}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = {
                            onPositionChanged((element.x + 1).coerceAtMost(WatchfaceProject.SCREEN_WIDTH), element.y)
                        }) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            onPositionChanged((element.x + 10).coerceAtMost(WatchfaceProject.SCREEN_WIDTH), element.y)
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "+10")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Y controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Y:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = {
                            onPositionChanged(element.x, (element.y - 10).coerceAtLeast(0))
                        }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "-10")
                        }
                        IconButton(onClick = {
                            onPositionChanged(element.x, (element.y - 1).coerceAtLeast(0))
                        }) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "${element.y}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = {
                            onPositionChanged(element.x, (element.y + 1).coerceAtMost(WatchfaceProject.SCREEN_HEIGHT))
                        }) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = {
                            onPositionChanged(element.x, (element.y + 10).coerceAtMost(WatchfaceProject.SCREEN_HEIGHT))
                        }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "+10")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddElementMenu(onAddElement: (WatchfaceElement) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Ajouter")
        }
    } else {
        Card {
            FlowRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val elementTypes = listOf(
                    "Heures" to { WatchfaceElement.TimeHours() },
                    "Minutes" to { WatchfaceElement.TimeMinutes() },
                    "Separateur" to { WatchfaceElement.TimeColon() },
                    "Pas" to { WatchfaceElement.Steps() },
                    "HR" to { WatchfaceElement.HeartRate() },
                    "Batterie" to { WatchfaceElement.Battery() },
                    "Date" to { WatchfaceElement.Date() },
                    "Jour" to { WatchfaceElement.WeekDay() },
                )
                for ((name, factory) in elementTypes) {
                    OutlinedButton(onClick = {
                        onAddElement(factory())
                        expanded = false
                    }) {
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(onClick = { expanded = false }) {
                    Text("Annuler", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

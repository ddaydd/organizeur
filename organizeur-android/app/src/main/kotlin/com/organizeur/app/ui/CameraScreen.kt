package com.organizeur.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.organizeur.app.R
import com.organizeur.app.camera.CameraManager
import com.organizeur.app.camera.MjpegStreamingService
import com.organizeur.app.camera.NetworkUtils
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    cameraManager: CameraManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var isStreaming by remember { mutableStateOf(false) }
    var clientCount by remember { mutableIntStateOf(0) }
    var lensFacing by remember { mutableIntStateOf(cameraManager.lensFacing) }
    var resolutionIndex by remember { mutableIntStateOf(cameraManager.resolutionIndex) }
    var serviceBinder by remember { mutableStateOf<MjpegStreamingService.LocalBinder?>(null) }

    val wifiIp = remember { NetworkUtils.getWifiIpAddress(context) }
    val port = cameraManager.port

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                serviceBinder = binder as? MjpegStreamingService.LocalBinder
                isStreaming = serviceBinder?.service?.isStreaming == true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
                isStreaming = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, MjpegStreamingService::class.java)
        context.bindService(intent, serviceConnection, 0)
        onDispose {
            previewViewRef = null
            serviceBinder?.service?.setPreviewView(null)
            try { context.unbindService(serviceConnection) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(serviceBinder, previewViewRef) {
        previewViewRef?.let { pv ->
            serviceBinder?.service?.setPreviewView(pv)
        }
    }

    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            clientCount = serviceBinder?.service?.clientCount ?: 0
            delay(2000)
        }
        clientCount = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.camera_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasCameraPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.camera_permission_needed),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.camera_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text(text = stringResource(R.string.silent_mode_perm_grant))
                        }
                    }
                }
            } else {
                // Camera preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStreaming) {
                        AndroidView(
                            factory = {
                                PreviewView(context).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    previewViewRef = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.camera_stopped),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // URL card (when streaming)
                if (isStreaming && wifiIp != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.camera_url_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "http://$wifiIp:$port/",
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.camera_clients, clientCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (isStreaming && wifiIp == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.camera_no_wifi),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isStreaming) {
                        Button(
                            onClick = {
                                val intent = Intent(context, MjpegStreamingService::class.java).apply {
                                    putExtra(MjpegStreamingService.EXTRA_PORT, port)
                                    putExtra(MjpegStreamingService.EXTRA_QUALITY, cameraManager.jpegQuality)
                                    putExtra(MjpegStreamingService.EXTRA_LENS_FACING, lensFacing)
                                    putExtra(MjpegStreamingService.EXTRA_RESOLUTION, resolutionIndex)
                                }
                                context.startForegroundService(intent)
                                context.bindService(
                                    Intent(context, MjpegStreamingService::class.java),
                                    serviceConnection,
                                    Context.BIND_AUTO_CREATE
                                )
                                isStreaming = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.camera_start))
                        }
                    } else {
                        Button(
                            onClick = {
                                serviceBinder?.service?.setPreviewView(null)
                                val intent = Intent(context, MjpegStreamingService::class.java).apply {
                                    action = MjpegStreamingService.ACTION_STOP
                                }
                                context.startService(intent)
                                isStreaming = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(text = stringResource(R.string.camera_stop))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val newFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT
                            else
                                CameraSelector.LENS_FACING_BACK
                            lensFacing = newFacing
                            cameraManager.lensFacing = newFacing
                            serviceBinder?.service?.switchCamera(newFacing)
                        },
                        enabled = isStreaming
                    ) {
                        Text(
                            text = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                stringResource(R.string.camera_back)
                            else
                                stringResource(R.string.camera_front)
                        )
                    }
                }

                // Resolution selector (only when not streaming)
                if (!isStreaming) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.camera_resolution),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CameraManager.RESOLUTION_LABELS.forEachIndexed { index, label ->
                                    OutlinedButton(
                                        onClick = {
                                            resolutionIndex = index
                                            cameraManager.resolutionIndex = index
                                        },
                                        colors = if (index == resolutionIndex) {
                                            ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        } else {
                                            ButtonDefaults.outlinedButtonColors()
                                        }
                                    ) {
                                        Text(text = label)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

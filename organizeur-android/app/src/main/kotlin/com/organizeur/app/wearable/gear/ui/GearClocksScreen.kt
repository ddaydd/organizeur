package com.organizeur.app.wearable.gear.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.organizeur.app.wearable.gear.sap.GearManager
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearClocksScreen(
    clocks: List<GearManager.ClockInfo>,
    activeClock: String,
    logs: List<String>,
    clockSettingsConnected: Boolean = false,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetIdleClock: (String) -> Unit,
    onRequestSettings: (String) -> Unit,
    onRequestPreview: (String) -> Unit,
    onSendClockSettings: (Map<String, Any>) -> Unit = {},
) {
    var showOrganiseurSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadrans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraichir")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Active clock info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.WatchLater, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Cadran actif", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = if (activeClock.isNotEmpty()) activeClock else "Inconnu",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            // Refresh button if no clocks
            if (clocks.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Charger la liste des cadrans")
                    }
                }
            }

            // Clock list
            if (clocks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${clocks.size} cadran(s) trouves",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            items(clocks) { clock ->
                val isOrganizeur = clock.packageName == "organizeur"
                ClockCard(
                    clock = clock,
                    isActive = clock.packageName == activeClock,
                    hasSettings = isOrganizeur,
                    onActivate = { onSetIdleClock(clock.packageName) },
                    onSettings = {
                        if (isOrganizeur) {
                            showOrganiseurSettings = !showOrganiseurSettings
                        }
                    },
                )

                // Inline settings for Organizeur clock
                if (isOrganizeur) {
                    AnimatedVisibility(visible = showOrganiseurSettings) {
                        OrganizeurSettingsCard(
                            connected = clockSettingsConnected,
                            onApply = onSendClockSettings,
                        )
                    }
                }
            }

            // Recent logs (filtered to clock-related)
            val clockLogs = logs.filter {
                it.contains("clock", ignoreCase = true) ||
                it.contains("idle", ignoreCase = true) ||
                it.contains("clocks_list", ignoreCase = true)
            }.take(20)
            if (clockLogs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Logs cadrans", style = MaterialTheme.typography.titleMedium)
                }
                items(clockLogs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ClockCard(
    clock: GearManager.ClockInfo,
    isActive: Boolean,
    hasSettings: Boolean,
    onActivate: () -> Unit,
    onSettings: () -> Unit,
) {
    val borderMod = if (isActive) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardDefaults.shape)
    } else {
        Modifier
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(borderMod),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Actif",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clock.name.ifEmpty { clock.packageName.substringAfterLast(".") },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        text = clock.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isActive) {
                    Button(onClick = onActivate) {
                        Text("Activer")
                    }
                }
                if (hasSettings) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Reglages")
                    }
                }
            }
        }
    }
}

// --- Organizeur Clock Settings ---

private val PRESET_COLORS = listOf(
    "#ffffff", "#4fc3f7", "#81c784", "#ffa726",
    "#ef5350", "#ce93d8", "#ffee58", "#aaaaaa",
    "#66bb6a", "#ff7043", "#ab47bc", "#26c6da",
    "#d4e157", "#ec407a", "#78909c", "#555555",
)

private fun parseHexColor(hex: String): Color? {
    return try {
        val clean = hex.removePrefix("#")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else null
    } catch (_: Exception) { null }
}

private fun colorToHex(color: Color): String {
    val r = (color.red * 255).roundToInt()
    val g = (color.green * 255).roundToInt()
    val b = (color.blue * 255).roundToInt()
    return "#%02x%02x%02x".format(r, g, b)
}

private fun hsvToColor(hue: Float, sat: Float, value: Float): Color {
    val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    return Color(c)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = parseHexColor(currentColor) ?: Color.White
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.rgb(
            (initial.red * 255).roundToInt(),
            (initial.green * 255).roundToInt(),
            (initial.blue * 255).roundToInt(),
        ), hsv,
    )
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var bri by remember { mutableFloatStateOf(hsv[2]) }
    var hexInput by remember { mutableStateOf(currentColor) }
    val previewColor = hsvToColor(hue, sat, bri)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onColorSelected(colorToHex(previewColor)); onDismiss() }) {
                Text("OK")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Annuler") }
        },
        text = {
            Column {
                // Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewColor),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Hue slider (0-360)
                Text("Teinte", style = MaterialTheme.typography.labelSmall)
                // Rainbow bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = (0..6).map { hsvToColor(it * 60f, 1f, 1f) },
                            ),
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                hexInput = colorToHex(hsvToColor(hue, sat, bri))
                            }
                        }
                        .clickable { },
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Saturation slider
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                androidx.compose.material3.Slider(
                    value = sat,
                    onValueChange = { sat = it; hexInput = colorToHex(hsvToColor(hue, sat, bri)) },
                )

                // Brightness slider
                Text("Luminosite", style = MaterialTheme.typography.labelSmall)
                androidx.compose.material3.Slider(
                    value = bri,
                    onValueChange = { bri = it; hexInput = colorToHex(hsvToColor(hue, sat, bri)) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Hex input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input
                        val parsed = parseHexColor(input)
                        if (parsed != null) {
                            val h = FloatArray(3)
                            android.graphics.Color.colorToHSV(
                                android.graphics.Color.rgb(
                                    (parsed.red * 255).roundToInt(),
                                    (parsed.green * 255).roundToInt(),
                                    (parsed.blue * 255).roundToInt(),
                                ), h,
                            )
                            hue = h[0]; sat = h[1]; bri = h[2]
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                // Quick presets
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (hex in PRESET_COLORS) {
                        val c = parseHexColor(hex) ?: Color.Gray
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    hexInput = hex
                                    val h = FloatArray(3)
                                    android.graphics.Color.colorToHSV(
                                        android.graphics.Color.rgb(
                                            (c.red * 255).roundToInt(),
                                            (c.green * 255).roundToInt(),
                                            (c.blue * 255).roundToInt(),
                                        ), h,
                                    )
                                    hue = h[0]; sat = h[1]; bri = h[2]
                                },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun ColorRow(
    label: String,
    currentColor: String,
    onColorSelected: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(parseHexColor(currentColor) ?: Color.Gray)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        ColorPickerDialog(
            currentColor = currentColor,
            onColorSelected = onColorSelected,
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Watch face is 320x320, preview is scaled to fit phone screen
private const val WATCH_SIZE = 320f

@Composable
private fun DraggableWatchPreview(
    bgColor: String,
    bgImagePreview: Bitmap?,
    timeColor: String,
    dateColor: String,
    secondsColor: String,
    batteryColor: String,
    secondsShow: Boolean,
    batteryShow: Boolean,
    timePosX: Float, timePosY: Float, onTimePos: (Float, Float) -> Unit,
    datePosX: Float, datePosY: Float, onDatePos: (Float, Float) -> Unit,
    secPosX: Float, secPosY: Float, onSecPos: (Float, Float) -> Unit,
    infoPosX: Float, infoPosY: Float, onInfoPos: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val previewDp = 280.dp
    val previewPx = with(density) { previewDp.toPx() }
    val scale = previewPx / WATCH_SIZE

    // Use real current date/time for preview
    val cal = java.util.Calendar.getInstance()
    val days = arrayOf("Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam")
    val months = arrayOf("Jan", "Fev", "Mar", "Avr", "Mai", "Jun", "Jul", "Aou", "Sep", "Oct", "Nov", "Dec")
    val dateText = "${days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]} ${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
    val timeText = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    val secText = "%02d".format(cal.get(java.util.Calendar.SECOND))

    Box(
        modifier = Modifier
            .size(previewDp)
            .clip(RoundedCornerShape(4.dp))
            .background(parseHexColor(bgColor) ?: Color.Black)
            .border(2.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
    ) {
        if (bgImagePreview != null) {
            Image(
                bitmap = bgImagePreview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Date — CSS: 20px, uppercase, letter-spacing 2px
        DraggableLabel(
            text = dateText.uppercase(),
            color = parseHexColor(dateColor) ?: Color.Gray,
            fontSize = 20f,
            letterSpacing = 2f,
            posX = datePosX, posY = datePosY,
            scale = scale,
            onDrag = onDatePos,
        )

        // Time — CSS: 86px, font-weight 200 (Samsung Sans falls back to normal)
        DraggableLabel(
            text = timeText,
            color = parseHexColor(timeColor) ?: Color.White,
            fontSize = 86f,
            fontWeight = FontWeight.Light,
            letterSpacing = -2f,
            posX = timePosX, posY = timePosY,
            scale = scale,
            onDrag = onTimePos,
        )

        // Seconds — CSS: 28px, font-weight 300
        if (secondsShow) {
            DraggableLabel(
                text = secText,
                color = parseHexColor(secondsColor) ?: Color.Cyan,
                fontSize = 28f,
                fontWeight = FontWeight.Light,
                posX = secPosX, posY = secPosY,
                scale = scale,
                onDrag = onSecPos,
            )
        }

        // Battery — CSS: 16px
        if (batteryShow) {
            DraggableLabel(
                text = "85%",
                color = parseHexColor(batteryColor) ?: Color.Gray,
                fontSize = 16f,
                posX = infoPosX, posY = infoPosY,
                scale = scale,
                onDrag = onInfoPos,
            )
        }
    }
}

@Composable
private fun DraggableLabel(
    text: String,
    color: Color,
    fontSize: Float,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
    posX: Float, posY: Float,
    scale: Float,
    onDrag: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val scaledFontSize = with(density) { (fontSize * scale).toSp() }
    val scaledLetterSpacing = if (letterSpacing != 0f) with(density) { (letterSpacing * scale).toSp() } else androidx.compose.ui.unit.TextUnit.Unspecified
    // Track measured size for centering (translate -50%, -50%)
    var textWidth by remember { mutableFloatStateOf(0f) }
    var textHeight by remember { mutableFloatStateOf(0f) }
    // Use rememberUpdatedState so pointerInput doesn't restart on each drag
    val currentPosX by rememberUpdatedState(posX)
    val currentPosY by rememberUpdatedState(posY)
    val currentScale by rememberUpdatedState(scale)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Text(
        text = text,
        color = color,
        fontSize = scaledFontSize,
        fontWeight = fontWeight,
        letterSpacing = scaledLetterSpacing,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .onSizeChanged { size ->
                textWidth = size.width.toFloat()
                textHeight = size.height.toFloat()
            }
            .offset {
                IntOffset(
                    (posX * scale - textWidth / 2).roundToInt(),
                    (posY * scale - textHeight / 2).roundToInt(),
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newX = currentPosX + dragAmount.x / currentScale
                    val newY = currentPosY + dragAmount.y / currentScale
                    currentOnDrag(
                        newX.coerceIn(0f, WATCH_SIZE),
                        newY.coerceIn(0f, WATCH_SIZE),
                    )
                }
            },
    )
}

private const val CLOCK_SETTINGS_PREFS = "clock_settings"

private fun saveClockSettings(context: android.content.Context, bgImage: String?,
    timeColor: String, dateColor: String, secondsColor: String, batteryColor: String, bgColor: String,
    secondsShow: Boolean, batteryShow: Boolean, colonBlink: Boolean,
    timePosX: Float, timePosY: Float, datePosX: Float, datePosY: Float,
    secPosX: Float, secPosY: Float, infoPosX: Float, infoPosY: Float,
) {
    context.getSharedPreferences(CLOCK_SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit()
        .putString("bgImage", bgImage)
        .putString("timeColor", timeColor)
        .putString("dateColor", dateColor)
        .putString("secondsColor", secondsColor)
        .putString("batteryColor", batteryColor)
        .putString("bgColor", bgColor)
        .putBoolean("secondsShow", secondsShow)
        .putBoolean("batteryShow", batteryShow)
        .putBoolean("colonBlink", colonBlink)
        .putFloat("timePosX", timePosX).putFloat("timePosY", timePosY)
        .putFloat("datePosX", datePosX).putFloat("datePosY", datePosY)
        .putFloat("secPosX", secPosX).putFloat("secPosY", secPosY)
        .putFloat("infoPosX", infoPosX).putFloat("infoPosY", infoPosY)
        .apply()
}

@Composable
private fun OrganizeurSettingsCard(
    connected: Boolean,
    onApply: (Map<String, Any>) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(CLOCK_SETTINGS_PREFS, android.content.Context.MODE_PRIVATE) }
    var timeColor by remember { mutableStateOf(prefs.getString("timeColor", "#ffffff")!!) }
    var dateColor by remember { mutableStateOf(prefs.getString("dateColor", "#aaaaaa")!!) }
    var secondsColor by remember { mutableStateOf(prefs.getString("secondsColor", "#4fc3f7")!!) }
    var batteryColor by remember { mutableStateOf(prefs.getString("batteryColor", "#66bb6a")!!) }
    var bgColor by remember { mutableStateOf(prefs.getString("bgColor", "#000000")!!) }
    var secondsShow by remember { mutableStateOf(prefs.getBoolean("secondsShow", true)) }
    var batteryShow by remember { mutableStateOf(prefs.getBoolean("batteryShow", true)) }
    var colonBlink by remember { mutableStateOf(prefs.getBoolean("colonBlink", true)) }
    var bgImageBase64 by remember { mutableStateOf(prefs.getString("bgImage", null)) }
    var bgImagePreview by remember {
        mutableStateOf(
            prefs.getString("bgImage", null)?.let { dataUri ->
                try {
                    val b64 = dataUri.substringAfter("base64,")
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) { null }
            }
        )
    }
    // Element positions (watch coordinates 0-320)
    var timePosX by remember { mutableFloatStateOf(prefs.getFloat("timePosX", 160f)) }
    var timePosY by remember { mutableFloatStateOf(prefs.getFloat("timePosY", 155f)) }
    var datePosX by remember { mutableFloatStateOf(prefs.getFloat("datePosX", 160f)) }
    var datePosY by remember { mutableFloatStateOf(prefs.getFloat("datePosY", 100f)) }
    var secPosX by remember { mutableFloatStateOf(prefs.getFloat("secPosX", 160f)) }
    var secPosY by remember { mutableFloatStateOf(prefs.getFloat("secPosY", 200f)) }
    var infoPosX by remember { mutableFloatStateOf(prefs.getFloat("infoPosX", 160f)) }
    var infoPosY by remember { mutableFloatStateOf(prefs.getFloat("infoPosY", 240f)) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val original = BitmapFactory.decodeStream(input)
                input?.close()
                if (original != null) {
                    val size = minOf(original.width, original.height)
                    val x = (original.width - size) / 2
                    val y = (original.height - size) / 2
                    val cropped = Bitmap.createBitmap(original, x, y, size, size)
                    val scaled = Bitmap.createScaledBitmap(cropped, 320, 320, true)
                    if (cropped != original) original.recycle()
                    if (scaled != cropped) cropped.recycle()
                    bgImagePreview = scaled
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                    bgImageBase64 = "data:image/jpeg;base64," +
                        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }
            } catch (_: Exception) {
                bgImageBase64 = null
                bgImagePreview = null
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reglages cadran", style = MaterialTheme.typography.titleMedium)
                if (!connected) {
                    Text(
                        "SAP non connecte",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ColorRow("Heure", timeColor) { timeColor = it }
            ColorRow("Date", dateColor) { dateColor = it }
            ColorRow("Secondes", secondsColor) { secondsColor = it }
            ColorRow("Batterie", batteryColor) { batteryColor = it }
            ColorRow("Fond", bgColor) { bgColor = it; bgImageBase64 = null; bgImagePreview = null }

            // Background image
            Spacer(modifier = Modifier.height(8.dp))
            Text("Image de fond", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            if (bgImagePreview != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Image(
                        bitmap = bgImagePreview!!.asImageBitmap(),
                        contentDescription = "Apercu",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "320x320 JPEG",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${(bgImageBase64?.length ?: 0) / 1024} Ko",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        bgImageBase64 = null
                        bgImagePreview = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Supprimer")
                    }
                }
            } else {
                OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choisir une image")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingSwitch("Secondes", secondsShow) { secondsShow = it }
            SettingSwitch("Batterie", batteryShow) { batteryShow = it }
            SettingSwitch("Clignotement :", colonBlink) { colonBlink = it }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Position des elements", style = MaterialTheme.typography.labelMedium)
            Text(
                "Glissez les elements pour les repositionner",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                DraggableWatchPreview(
                    bgColor = bgColor,
                    bgImagePreview = bgImagePreview,
                    timeColor = timeColor,
                    dateColor = dateColor,
                    secondsColor = secondsColor,
                    batteryColor = batteryColor,
                    secondsShow = secondsShow,
                    batteryShow = batteryShow,
                    timePosX = timePosX, timePosY = timePosY,
                    onTimePos = { x, y -> timePosX = x; timePosY = y },
                    datePosX = datePosX, datePosY = datePosY,
                    onDatePos = { x, y -> datePosX = x; datePosY = y },
                    secPosX = secPosX, secPosY = secPosY,
                    onSecPos = { x, y -> secPosX = x; secPosY = y },
                    infoPosX = infoPosX, infoPosY = infoPosY,
                    onInfoPos = { x, y -> infoPosX = x; infoPosY = y },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val settings = mutableMapOf<String, Any>(
                        "timeColor" to timeColor,
                        "dateColor" to dateColor,
                        "secondsColor" to secondsColor,
                        "batteryColor" to batteryColor,
                        "bgColor" to bgColor,
                        "secondsShow" to secondsShow,
                        "batteryShow" to batteryShow,
                        "colonBlink" to colonBlink,
                        "bgImage" to (bgImageBase64 ?: ""),
                        "timePosX" to timePosX.roundToInt(),
                        "timePosY" to timePosY.roundToInt(),
                        "datePosX" to datePosX.roundToInt(),
                        "datePosY" to datePosY.roundToInt(),
                        "secPosX" to secPosX.roundToInt(),
                        "secPosY" to secPosY.roundToInt(),
                        "infoPosX" to infoPosX.roundToInt(),
                        "infoPosY" to infoPosY.roundToInt(),
                    )
                    onApply(settings)
                    saveClockSettings(context, bgImageBase64,
                        timeColor, dateColor, secondsColor, batteryColor, bgColor,
                        secondsShow, batteryShow, colonBlink,
                        timePosX, timePosY, datePosX, datePosY,
                        secPosX, secPosY, infoPosX, infoPosY)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = connected,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Appliquer sur la montre")
            }
        }
    }
}

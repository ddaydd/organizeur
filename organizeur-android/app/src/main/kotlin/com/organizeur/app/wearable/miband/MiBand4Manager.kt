package com.organizeur.app.wearable.miband

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.organizeur.app.wearable.model.BandData
import com.organizeur.app.wearable.model.BandState
import com.organizeur.app.wearable.model.DeviceInfo
import com.organizeur.app.wearable.model.MusicEvent
import com.organizeur.app.wearable.model.ScannedDevice
import com.organizeur.app.wearable.miband.watchface.WatchfaceUploader
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ConnectionFailedException
import com.welie.blessed.ConnectionState
import com.welie.blessed.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MiBand4Manager(context: Context) {

    companion object {
        private const val TAG = "MiBand4Manager"
        private const val PREFS_NAME = "miband4_prefs"
        private const val PREF_AUTH_KEY = "auth_key"
        private const val PREF_DEVICE_ADDRESS = "device_address"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<BandState>(BandState.Disconnected)
    val state: StateFlow<BandState> = _state.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _bandData = MutableStateFlow(BandData())
    val bandData: StateFlow<BandData> = _bandData.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var authKey: ByteArray? = null
    private var connectedPeripheral: BluetoothPeripheral? = null

    val savedAuthKey: String? get() = prefs.getString(PREF_AUTH_KEY, null)
    val savedDeviceAddress: String? get() = prefs.getString(PREF_DEVICE_ADDRESS, null)

    private val central = BluetoothCentralManager(context)

    init {
        central.observeConnectionState { peripheral, state ->
            when (state) {
                ConnectionState.CONNECTED -> {
                    log("Connected to ${peripheral.name}")
                    connectedPeripheral = peripheral
                    _state.value = BandState.Connected(peripheral.name)
                    _deviceInfo.value = DeviceInfo(
                        name = peripheral.name,
                        address = peripheral.address,
                    )
                    prefs.edit().putString(PREF_DEVICE_ADDRESS, peripheral.address).apply()
                    // Auto-authenticate if we have a key
                    if (authKey != null) {
                        authenticate()
                    }
                }
                ConnectionState.DISCONNECTED -> {
                    log("Disconnected from ${peripheral.name}")
                    connectedPeripheral = null
                    _state.value = BandState.Disconnected
                    _bandData.value = BandData()
                }
                else -> {}
            }
        }
    }

    fun startScan() {
        _scannedDevices.value = emptyList()
        _state.value = BandState.Scanning
        log("Starting BLE scan...")
        central.scanForPeripheralsWithServices(
            serviceUUIDs = arrayOf(MiBand4Constants.SERVICE_MIBAND),
            resultCallback = { peripheral, scanResult ->
                val name = peripheral.name.ifEmpty { "Unknown" }
                val device = ScannedDevice(name, peripheral.address, scanResult.rssi)
                val current = _scannedDevices.value.toMutableList()
                val existingIndex = current.indexOfFirst { it.address == device.address }
                if (existingIndex >= 0) {
                    current[existingIndex] = device
                } else {
                    current.add(device)
                }
                _scannedDevices.value = current
                log("Found: $name (${peripheral.address}) RSSI=${scanResult.rssi}")
            },
            scanError = { failure ->
                log("Scan failed: $failure")
                _state.value = BandState.Error("Scan failed: $failure")
            },
        )
    }

    fun stopScan() {
        central.stopScan()
        if (_state.value is BandState.Scanning) {
            _state.value = BandState.Disconnected
        }
        log("Scan stopped")
    }

    fun connect(address: String) {
        central.stopScan()
        val peripheral = central.getPeripheral(address)
        _state.value = BandState.Connecting(peripheral.name.ifEmpty { address })
        log("Connecting to $address...")
        scope.launch {
            try {
                central.connectPeripheral(peripheral)
            } catch (e: ConnectionFailedException) {
                log("Connection failed: ${e.message}")
                _state.value = BandState.Error("Connection failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        connectedPeripheral?.let { peripheral ->
            scope.launch {
                central.cancelConnection(peripheral)
            }
        }
    }

    fun setAuthKey(hexString: String): Boolean {
        val key = MiBand4Auth.parseAuthKey(hexString)
        if (key == null) {
            log("Invalid auth key format (need 32 hex chars)")
            return false
        }
        authKey = key
        prefs.edit().putString(PREF_AUTH_KEY, hexString).apply()
        log("Auth key set and saved")
        return true
    }

    fun autoConnect() {
        val address = savedDeviceAddress ?: return
        val keyHex = savedAuthKey ?: return
        val key = MiBand4Auth.parseAuthKey(keyHex) ?: return
        authKey = key
        log("Auto-connecting to $address...")
        connect(address)
    }

    fun authenticate() {
        val peripheral = connectedPeripheral ?: run {
            log("Not connected")
            return
        }
        val key = authKey ?: run {
            log("No auth key set")
            return
        }

        scope.launch {
            try {
                _state.value = BandState.Authenticating("Enabling notifications...")
                log("Starting authentication...")

                val authChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_MIBAND2,
                    MiBand4Constants.CHAR_AUTH,
                )
                if (authChar == null) {
                    log("Auth characteristic not found!")
                    _state.value = BandState.Error("Auth characteristic not found")
                    return@launch
                }

                // Observe auth notifications
                peripheral.observe(authChar) { value ->
                    handleAuthNotification(peripheral, value, key)
                }

                // Step 1: Send the auth key
                _state.value = BandState.Authenticating("Sending key...")
                val sendKeyCmd = MiBand4Auth.buildSendKeyCommand(key)
                peripheral.writeCharacteristic(authChar, sendKeyCmd, WriteType.WITHOUT_RESPONSE)
                log("Auth key sent, waiting for response...")
            } catch (e: Exception) {
                log("Auth error: ${e.message}")
                _state.value = BandState.Error("Auth failed: ${e.message}")
            }
        }
    }

    private fun handleAuthNotification(
        peripheral: BluetoothPeripheral,
        value: ByteArray,
        key: ByteArray,
    ) {
        log("Auth raw: ${value.toHex()} (${value.size} bytes)")
        val result = MiBand4Auth.parseAuthResponse(value)
        log("Auth response: $result")

        scope.launch {
            try {
                val authChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_MIBAND2,
                    MiBand4Constants.CHAR_AUTH,
                ) ?: return@launch

                when (result) {
                    is AuthResult.KeySent -> {
                        _state.value = BandState.Authenticating("Requesting random...")
                        val cmd = MiBand4Auth.buildRequestRandomCommand()
                        peripheral.writeCharacteristic(authChar, cmd, WriteType.WITHOUT_RESPONSE)
                        log("Random number requested")
                    }
                    is AuthResult.RandomReceived -> {
                        _state.value = BandState.Authenticating("Sending encrypted response...")
                        val cmd = MiBand4Auth.buildEncryptedResponse(result.randomData, key)
                        peripheral.writeCharacteristic(authChar, cmd, WriteType.WITHOUT_RESPONSE)
                        log("Encrypted response sent")
                    }
                    is AuthResult.Authenticated -> {
                        _state.value = BandState.Authenticated(peripheral.name)
                        log("Authentication successful!")
                        onAuthenticated(peripheral)
                    }
                    is AuthResult.Failed -> {
                        _state.value = BandState.Error("Auth failed: ${result.reason}")
                        log("Authentication FAILED: ${result.reason}")
                    }
                    is AuthResult.Unknown -> {
                        log("Unknown auth response: ${value.toHex()}")
                    }
                }
            } catch (e: Exception) {
                log("Auth step error: ${e.message}")
                _state.value = BandState.Error("Auth error: ${e.message}")
            }
        }
    }

    private suspend fun onAuthenticated(peripheral: BluetoothPeripheral) {
        try {
            readDeviceInfo(peripheral)
            readBattery(peripheral)

            // Enable step notifications
            val stepsChar = peripheral.getCharacteristic(
                MiBand4Constants.SERVICE_MIBAND,
                MiBand4Constants.CHAR_STEPS,
            )
            if (stepsChar != null) {
                peripheral.observe(stepsChar) { value -> handleStepsNotification(value) }
                log("Step notifications enabled")
            }
        } catch (e: Exception) {
            log("Post-auth setup error: ${e.message}")
        }
    }

    private suspend fun readDeviceInfo(peripheral: BluetoothPeripheral) {
        try {
            val serial = peripheral.readCharacteristic(
                MiBand4Constants.SERVICE_DEVICE_INFO,
                MiBand4Constants.CHAR_SERIAL_NUMBER,
            )
            val hw = peripheral.readCharacteristic(
                MiBand4Constants.SERVICE_DEVICE_INFO,
                MiBand4Constants.CHAR_HARDWARE_REV,
            )
            val sw = peripheral.readCharacteristic(
                MiBand4Constants.SERVICE_DEVICE_INFO,
                MiBand4Constants.CHAR_SOFTWARE_REV,
            )
            _deviceInfo.value = _deviceInfo.value.copy(
                serialNumber = String(serial),
                hardwareRevision = String(hw),
                softwareRevision = String(sw),
            )
            log("Device info: serial=${String(serial)}, hw=${String(hw)}, sw=${String(sw)}")
        } catch (e: Exception) {
            log("Failed to read device info: ${e.message}")
        }
    }

    // --- Features ---

    fun readBattery() {
        val peripheral = connectedPeripheral ?: return
        scope.launch { readBattery(peripheral) }
    }

    private suspend fun readBattery(peripheral: BluetoothPeripheral) {
        try {
            val value = peripheral.readCharacteristic(
                MiBand4Constants.SERVICE_MIBAND,
                MiBand4Constants.CHAR_BATTERY,
            )
            if (value.isNotEmpty()) {
                val level = if (value.size > 1) value[1].toInt() and 0xFF else value[0].toInt() and 0xFF
                _bandData.value = _bandData.value.copy(batteryLevel = level)
                log("Battery: $level%")
            }
        } catch (e: Exception) {
            log("Battery read error: ${e.message}")
        }
    }

    fun startHeartRate(continuous: Boolean = false) {
        val peripheral = connectedPeripheral ?: return
        scope.launch {
            try {
                val hrMeasureChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_HEART_RATE,
                    MiBand4Constants.CHAR_HEART_RATE_MEASURE,
                )
                if (hrMeasureChar != null) {
                    peripheral.observe(hrMeasureChar) { value -> handleHeartRateNotification(value) }
                }

                val cmd = if (continuous) MiBand4Constants.HR_START_CONTINUOUS else MiBand4Constants.HR_START_MANUAL
                peripheral.writeCharacteristic(
                    MiBand4Constants.SERVICE_HEART_RATE,
                    MiBand4Constants.CHAR_HEART_RATE_CONTROL,
                    cmd,
                    WriteType.WITH_RESPONSE,
                )
                _bandData.value = _bandData.value.copy(isHeartRateMonitoring = true)
                log("Heart rate ${if (continuous) "continuous" else "manual"} started")
            } catch (e: Exception) {
                log("HR start error: ${e.message}")
            }
        }
    }

    fun stopHeartRate() {
        val peripheral = connectedPeripheral ?: return
        scope.launch {
            try {
                peripheral.writeCharacteristic(
                    MiBand4Constants.SERVICE_HEART_RATE,
                    MiBand4Constants.CHAR_HEART_RATE_CONTROL,
                    MiBand4Constants.HR_STOP_CONTINUOUS,
                    WriteType.WITH_RESPONSE,
                )
                peripheral.writeCharacteristic(
                    MiBand4Constants.SERVICE_HEART_RATE,
                    MiBand4Constants.CHAR_HEART_RATE_CONTROL,
                    MiBand4Constants.HR_STOP_MANUAL,
                    WriteType.WITH_RESPONSE,
                )
                val hrMeasureChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_HEART_RATE,
                    MiBand4Constants.CHAR_HEART_RATE_MEASURE,
                )
                if (hrMeasureChar != null) {
                    peripheral.stopObserving(hrMeasureChar)
                }
                _bandData.value = _bandData.value.copy(isHeartRateMonitoring = false, heartRate = null)
                log("Heart rate stopped")
            } catch (e: Exception) {
                log("HR stop error: ${e.message}")
            }
        }
    }

    private fun handleHeartRateNotification(value: ByteArray) {
        if (value.size >= 2) {
            val hr = value[1].toInt() and 0xFF
            if (hr > 0) {
                _bandData.value = _bandData.value.copy(heartRate = hr)
                log("Heart rate: $hr bpm")
            }
        }
    }

    private fun handleStepsNotification(value: ByteArray) {
        if (value.size >= 4) {
            val steps = (value[1].toInt() and 0xFF) or
                    ((value[2].toInt() and 0xFF) shl 8) or
                    ((value[3].toInt() and 0xFF) shl 16)
            _bandData.value = _bandData.value.copy(steps = steps)
            log("Steps: $steps")
        }
    }

    fun sendNotification(type: ByteArray = MiBand4Constants.ALERT_VIBRATE) {
        val peripheral = connectedPeripheral ?: return
        scope.launch {
            try {
                peripheral.writeCharacteristic(
                    MiBand4Constants.SERVICE_ALERT,
                    MiBand4Constants.CHAR_ALERT,
                    type,
                    WriteType.WITHOUT_RESPONSE,
                )
                log("Notification sent (type=${type[0]})")
            } catch (e: Exception) {
                log("Notification error: ${e.message}")
            }
        }
    }

    fun enableMusicControls() {
        val peripheral = connectedPeripheral ?: return
        scope.launch {
            try {
                val musicChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_MIBAND,
                    MiBand4Constants.CHAR_MUSIC_EVENT,
                )
                if (musicChar != null) {
                    peripheral.observe(musicChar) { value -> handleMusicEvent(value) }
                }

                // Send chunked music info (max 20 bytes per BLE write)
                val payload = buildMusicPayload("MB4", "OK")
                val chunkedChar = peripheral.getCharacteristic(
                    MiBand4Constants.SERVICE_MIBAND,
                    MiBand4Constants.CHAR_CHUNKED_TRANSFER,
                ) ?: return@launch

                var offset = 0
                while (offset < payload.size) {
                    val end = minOf(offset + 20, payload.size)
                    val chunk = payload.copyOfRange(offset, end)
                    peripheral.writeCharacteristic(
                        chunkedChar,
                        chunk,
                        WriteType.WITHOUT_RESPONSE,
                    )
                    offset = end
                }
                log("Music controls enabled")
            } catch (e: Exception) {
                log("Music enable error: ${e.message}")
            }
        }
    }

    private fun buildMusicPayload(artist: String, track: String): ByteArray {
        val flag: Byte = 0x03
        val artistBytes = artist.toByteArray(Charsets.UTF_8)
        val trackBytes = track.toByteArray(Charsets.UTF_8)
        return byteArrayOf(flag) + artistBytes + byteArrayOf(0x00) + trackBytes + byteArrayOf(0x00)
    }

    private fun handleMusicEvent(value: ByteArray) {
        if (value.isEmpty()) return
        val event = when (value[0]) {
            MiBand4Constants.MUSIC_PLAY -> MusicEvent.PLAY
            MiBand4Constants.MUSIC_PAUSE -> MusicEvent.PAUSE
            MiBand4Constants.MUSIC_NEXT -> MusicEvent.NEXT
            MiBand4Constants.MUSIC_PREV -> MusicEvent.PREV
            MiBand4Constants.MUSIC_VOL_UP -> MusicEvent.VOL_UP
            MiBand4Constants.MUSIC_VOL_DOWN -> MusicEvent.VOL_DOWN
            else -> {
                log("Unknown music event: ${value.toHex()}")
                return
            }
        }
        _bandData.value = _bandData.value.copy(lastMusicEvent = event)
        log("Music event: $event")
    }

    fun uploadWatchface(data: ByteArray, onProgress: (WatchfaceUploader.UploadState) -> Unit) {
        val peripheral = connectedPeripheral ?: run {
            onProgress(WatchfaceUploader.UploadState.Error("Non connecte"))
            return
        }
        scope.launch {
            WatchfaceUploader.upload(peripheral, data, onProgress)
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val current = _logs.value.toMutableList()
        current.add(0, message)
        if (current.size > 200) current.removeAt(current.lastIndex)
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

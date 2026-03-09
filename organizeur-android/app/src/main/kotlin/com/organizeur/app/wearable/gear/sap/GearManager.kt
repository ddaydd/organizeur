package com.organizeur.app.wearable.gear.sap

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.sec.android.WSM.Client
import com.sec.android.WSM.Common
import com.sec.android.WSM.AuthPacket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.CRC32

class GearManager(private val context: Context) {

    companion object {
        private const val TAG = "GearManager"

        // Pre-auth message types (first byte of raw frame)
        private const val MSG_AGENT_AUTH_REQ = 5  // Actually Peer Description request
        private const val MSG_AGENT_AUTH_RESP = 6
        private const val MSG_ACCESSORY_AUTH = 0x10  // 16 - WSM challenge from watch
        private const val MSG_ACCESSORY_AUTH_RESP = 0x11  // 17 - server challenge (we send)
        private const val MSG_ACCESSORY_AUTH_VERIFY = 0x12  // 18 - WSM client response from watch
    }

    sealed class GearState {
        data object Disconnected : GearState()
        data class Connecting(val step: String) : GearState()
        data class Connected(val deviceName: String) : GearState()
        data class Error(val message: String) : GearState()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<GearState>(GearState.Disconnected)
    val state: StateFlow<GearState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Device info (from mgr_watch_info_res)
    data class DeviceInfo(
        val model: String = "",
        val firmware: String = "",
        val btMac: String = "",
        val serial: String = "",
        val modelName: String = "",
        val salesCode: String = "",
    )

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    // Clock/Watchface
    data class ClockInfo(
        val packageName: String,
        val name: String = "",
        val clockType: String = "",
        val isActive: Boolean = false,
    )

    private val _clocks = MutableStateFlow<List<ClockInfo>>(emptyList())
    val clocks: StateFlow<List<ClockInfo>> = _clocks.asStateFlow()

    private val _activeClock = MutableStateFlow<String>("")
    val activeClock: StateFlow<String> = _activeClock.asStateFlow()

    // File Transfer
    val fileTransfer = GearFileTransfer(
        sendOnSession = { sessionId, _, appData -> sendRawOnSession(sessionId, appData) },
        log = { msg -> log(msg) },
    )
    val fileTransferState: StateFlow<GearFileTransfer.TransferState> = fileTransfer.state
    private var ftCommandSessionId: Int = 0
    private var ftCommandChannelId: Int = 0
    private var ftDataSessionId: Int = 0
    private var ftDataChannelId: Int = 0
    private var ftConnReceived: CompletableDeferred<Boolean>? = null

    private var dataSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    @Volatile private var crcEnabled = false
    private var wsmClient: Client? = null
    private var serverSocket: BluetoothServerSocket? = null

    // Synchronization between read loop and connect sequence
    private var wsmAuthComplete: CompletableDeferred<Boolean>? = null
    private var serviceConnReceived: CompletableDeferred<Boolean>? = null
    private var capexDataReceived: CompletableDeferred<Boolean>? = null

    // Host Manager session (set when watch initiates Service Connection)
    private var hostManagerSessionId: Int = 0
    private var hostManagerChannelId: Int = 0
    private var hmUsesFragmentation: Boolean = false  // QoS type=4 → 1B frag header on data frames

    // Remote agent IDs from watch's CAPEX response (profile -> componentId)
    private var remoteAgentIds: MutableMap<String, Int> = mutableMapOf()
    private var capexResponseReceived: CompletableDeferred<Boolean>? = null
    private var capexSessionId: Int = 1  // CAPEX session (from Service Connection)

    // Connected service sessions (sessionId -> profile) and channels (sessionId -> channelId)
    private val connectedSessions: MutableMap<Int, String> = mutableMapOf()
    private val connectedChannels: MutableMap<Int, Int> = mutableMapOf()
    private var sfotaHandled: CompletableDeferred<Boolean>? = null
    private var capexConnReceived: CompletableDeferred<Boolean>? = null
    private var hmConnReceived: CompletableDeferred<Boolean>? = null
    private var hmResponseReceived: CompletableDeferred<Boolean>? = null
    private var hmWallpaperResponse: CompletableDeferred<Boolean>? = null

    // Clock settings SAP channel (phone=provider → clock face=consumer)
    private var clockSettingsSessionId: Int = 0
    private var clockSettingsChannelId: Int = 0
    private var clockSettingsQosType: Int = 0
    private var clockSettingsSeqNum: Int = 0
    private val _clockSettingsConnected = MutableStateFlow(false)
    val clockSettingsConnected: StateFlow<Boolean> = _clockSettingsConnected.asStateFlow()

    private fun getPhoneBluetoothAddress(): String {
        try {
            val addr = Settings.Secure.getString(context.contentResolver, "bluetooth_address")
            if (addr != null && addr != "02:00:00:00:00:00") {
                log("BT addr (Settings): $addr")
                return addr
            }
        } catch (_: Exception) {}
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val addr = adapter?.address
            if (addr != null && addr != "02:00:00:00:00:00") {
                log("BT addr (adapter): $addr")
                return addr
            }
        } catch (_: Exception) {}
        val fallback = "04:C8:B0:CC:90:6F"
        log("BT addr (hardcoded): $fallback")
        return fallback
    }

    /**
     * Remove Bluetooth bond (unpair) using reflection on hidden API.
     * This forces a fresh pairing on next connection, clearing any stale SAP state.
     */
    fun unpairDevice(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as Boolean
            log("Unpair ${device.address}: $result")
            result
        } catch (e: Exception) {
            log("Unpair error: ${e.message}")
            false
        }
    }

    fun getBondedGearDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices
                ?.filter { it.name?.contains("Gear", ignoreCase = true) == true }
                ?: emptyList()
        } catch (e: SecurityException) {
            log("Permission error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Connect to a Samsung Gear 1 device.
     *
     * Samsung flow (from decompiled SABtServerListener + SABtRfConnection):
     * 1. Phone starts RFCOMM server on UUID_DATA
     * 2. Phone nudges watch on UUID_NUDGE (wakes it)
     * 3. Watch connects back to phone on UUID_DATA
     * 4. Protocol exchange (auth, service connection, data)
     */
    fun connect(device: BluetoothDevice) {
        scope.launch {
            try {
                _state.value = GearState.Connecting("Demarrage serveur...")

                wsmAuthComplete = CompletableDeferred()
                serviceConnReceived = CompletableDeferred()

                withContext(Dispatchers.IO) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()

                    // Step 1: Start RFCOMM server
                    log("Starting RFCOMM server on UUID_DATA...")
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                        "SAP", GearConstants.UUID_DATA,
                    )
                    log("Server listening")

                    // Step 2: Nudge
                    _state.value = GearState.Connecting("Reveil montre...")
                    log("Nudge...")
                    try {
                        val nudge = device.createRfcommSocketToServiceRecord(GearConstants.UUID_NUDGE)
                        nudge.connect()
                        nudge.close()
                        log("Nudge OK")
                    } catch (e: Exception) {
                        log("Nudge: ${e.message}")
                    }

                    // Step 3: Accept incoming connection
                    _state.value = GearState.Connecting("Attente connexion...")
                    log("Waiting for watch connection...")
                    val accepted = serverSocket?.accept(30000)
                        ?: throw Exception("Pas de connexion entrante")

                    val remoteName = try { accepted.remoteDevice?.name ?: "?" } catch (_: SecurityException) { "?" }
                    log("Accepted: $remoteName")
                    serverSocket?.close()
                    serverSocket = null

                    dataSocket = accepted
                    inputStream = accepted.inputStream
                    outputStream = accepted.outputStream

                    // Setup WSM client BEFORE read loop (so it's ready for challenge)
                    _state.value = GearState.Connecting("Auth WSM...")
                    Common.set_protocol_version(GearConstants.WSM_VERSION)
                    val phoneAddr = getPhoneBluetoothAddress()
                    wsmClient = Client(device.address, phoneAddr, GearConstants.WSM_VERSION)
                    log("WSM Client ready: watch=${device.address}, phone=$phoneAddr")

                    // Start read loop — it will handle all incoming messages
                    scope.launch(Dispatchers.IO) { readLoop() }

                    // Wait for WSM auth to complete (driven by read loop)
                    log("Waiting for WSM auth...")
                    val authOk = withTimeoutOrNull(15000) { wsmAuthComplete?.await() }
                    if (authOk != true) {
                        log("WSM Auth timeout")
                        throw Exception("WSM Auth failed")
                    }
                    log("WSM Auth OK!")

                    // CRC already enabled in read loop thread (handleWsmClientResponse)
                    // Peer Description already exchanged pre-auth (types 5/6).

                    capexConnReceived = CompletableDeferred()
                    sfotaHandled = CompletableDeferred()
                    hmConnReceived = CompletableDeferred()
                    serviceConnReceived = CompletableDeferred()
                    capexDataReceived = CompletableDeferred()
                    capexResponseReceived = CompletableDeferred()

                    // Control frame on default session 0x3FF (standard SAP initialization)
                    val ctrlFrame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_CONTROL, SapProtocol.DEFAULT_SESSION_ID, ByteArray(0))
                    sendFrame(ctrlFrame)
                    log("Control frame sent on 0x3FF")

                    // --- Phase 1: Brief passive wait, then active CAPEX ---
                    _state.value = GearState.Connecting("CAPEX...")
                    log("=== Phase 1: Attente CAPEX passif (5s) ===")
                    val watchCapex = withTimeoutOrNull(5000) { capexConnReceived?.await() }

                    if (watchCapex == true) {
                        log("CAPEX initié par la montre (session=$capexSessionId)!")
                        // Wait for watch's query and our response exchange
                        val queryOk = withTimeoutOrNull(5000) { capexDataReceived?.await() }
                        if (queryOk == true) log("CAPEX query traitée")

                        // Send our CAPEX query to get watch's capabilities
                        log("Envoi CAPEX query...")
                        val query = buildCapexQuery()
                        val queryFrame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, capexSessionId, query)
                        sendFrame(queryFrame)

                        val responseOk = withTimeoutOrNull(10000) { capexResponseReceived?.await() }
                        if (responseOk == true) log("CAPEX response reçue — agents: $remoteAgentIds")
                        else log("CAPEX response timeout")
                    } else {
                        // Fallback: initiate CAPEX ourselves (like before)
                        log("CAPEX passif timeout — fallback actif")
                        _state.value = GearState.Connecting("CAPEX (actif)...")
                        serviceConnReceived = CompletableDeferred()
                        val capexPayload = SapProtocol.buildServiceConnectionRequest(
                            acceptorId = GearConstants.AGENT_ID_CAPEX,
                            initiatorId = GearConstants.PEER_ID_CAPEX,
                            profileName = GearConstants.PROFILE_CAPEX,
                            sessionId = 1,
                            channelId = GearConstants.CHANNEL_CAPEX,
                        )
                        val capexFrame = SapProtocol.wrapInDefaultFrame(capexPayload)
                        sendFrame(capexFrame)

                        val capexOk = withTimeoutOrNull(5000) { serviceConnReceived?.await() }
                        if (capexOk == true) log("CAPEX connected (actif)")
                        else log("CAPEX timeout")

                        // CAPEX exchange
                        log("Envoi CAPEX query...")
                        val query = buildCapexQuery()
                        val queryFrame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, capexSessionId, query)
                        sendFrame(queryFrame)

                        val queryOk = withTimeoutOrNull(5000) { capexDataReceived?.await() }
                        if (queryOk == true) log("Watch query reçue, réponse envoyée")
                        else log("CAPEX query timeout")

                        val responseOk = withTimeoutOrNull(10000) { capexResponseReceived?.await() }
                        if (responseOk == true) log("CAPEX response reçue — agents: $remoteAgentIds")
                        else log("CAPEX response timeout")
                    }

                    // --- Phase 1b: CAPEX incremental updates ---
                    // Send updates so the watch knows our filetransfer provider is active.
                    // This may trigger the watch to register its FT consumer and initiate SC.
                    log("=== CAPEX incremental updates ===")
                    sendCapexUpdates()
                    log("CAPEX updates envoyés")

                    // --- Phase 2: Wait for watch to initiate HM ---
                    // CAPEX updates alone trigger HM (observed: 360ms delay).
                    // Post-CAPEX nudge removed — it interfered with freshly established HM session.
                    _state.value = GearState.Connecting("Host Manager...")
                    log("=== Phase 2: Attente HM (5s) ===")

                    val hmOk = withTimeoutOrNull(5000) { hmConnReceived?.await() }
                    if (hmOk == true) {
                        log("Host Manager connecté (watch-initiated)! session=$hostManagerSessionId, channel=$hostManagerChannelId")
                        hmResponseReceived = CompletableDeferred()

                        // Wait 1s for watch to send first (or for its handler to initialize)
                        log("=== Attente 1s (watch handler init / watch-first msg) ===")
                        val earlyResp = withTimeoutOrNull(1000) { hmResponseReceived?.await() }
                        if (earlyResp == true) {
                            log("HM: Watch envoyé en premier! Réponse reçue!")
                        } else {
                            // Now send mgr_watch_info_req (after delay for handler init)
                            log("=== Envoi mgr_watch_info_req (après 1s délai) ===")
                            sendWatchInfoRequestQtOrder()
                            val resp1 = withTimeoutOrNull(5000) { hmResponseReceived?.await() }
                            if (resp1 == true) {
                                log("HM réponse reçue!")
                            } else {
                                log("Pas de réponse — test multi-format...")
                                tryAllHmFormats()
                                log("=== Attente finale 5s ===")
                                val resp2 = withTimeoutOrNull(5000) { hmResponseReceived?.await() }
                                if (resp2 == true) log("HM démarré!")
                                else log("HM timeout total — aucune réponse")
                            }
                        }
                    } else {
                        log("HM timeout — connexion manuelle...")
                        connectHostManagerSelf()
                    }
                }

                _state.value = GearState.Connected(device.name ?: "Gear")
                log("Connected!")

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                log("Error: ${e.message}")
                _state.value = GearState.Error("Echec: ${e.message}")
                disconnect()
            }
        }
    }

    // --- Frame I/O ---

    private val writeLock = Any()

    private fun sendFrame(payload: ByteArray) {
        val out = outputStream ?: return
        synchronized(writeLock) {
            val frame = SapFraming.wrapFrame(payload, crcEnabled)
            log("TX (${frame.size}B, crc=$crcEnabled): ${frame.joinToString("") { "%02x".format(it) }}")
            out.write(frame)
            out.flush()
        }
    }

    private fun readLoop() {
        val input = inputStream ?: return
        log("Read loop started (crc=$crcEnabled)")
        try {
            while (dataSocket?.isConnected == true) {
                val b0 = input.read()
                if (b0 < 0) { log("EOF"); break }
                val b1 = input.read()
                if (b1 < 0) { log("EOF 2"); break }
                val payloadLen = (b0 shl 8) or b1

                if (payloadLen <= 0 || payloadLen > 65535) {
                    // Raw dump first 20 bytes to understand what watch sent
                    val raw = mutableListOf(b0.toByte(), b1.toByte())
                    repeat(minOf(18, input.available())) { raw.add(input.read().toByte()) }
                    log("Bad length $payloadLen — raw: ${raw.joinToString("") { "%02x".format(it) }}")
                    break
                }

                if (crcEnabled) {
                    val crcHi = input.read(); if (crcHi < 0) { log("EOF lenCRC"); break }
                    val crcLo = input.read(); if (crcLo < 0) { log("EOF lenCRC2"); break }
                    val actualCrc = (crcHi shl 8) or crcLo
                    val expectedCrc = SapFraming.crc16(byteArrayOf(b0.toByte(), b1.toByte()))
                    if (actualCrc != expectedCrc) {
                        log("Length CRC mismatch: got=${"%04x".format(actualCrc)} expected=${"%04x".format(expectedCrc)} len=$payloadLen")
                        // Try reading WITHOUT CRC (maybe watch sent without CRC)
                        log("Falling back to no-CRC read...")
                        val payload = ByteArray(payloadLen)
                        // The 2 "CRC" bytes are actually first 2 bytes of payload
                        payload[0] = crcHi.toByte()
                        payload[1] = crcLo.toByte()
                        var off = 2
                        while (off < payloadLen) {
                            val n = input.read(payload, off, payloadLen - off)
                            if (n < 0) break
                            off += n
                        }
                        if (off >= payloadLen) {
                            val hex = payload.joinToString("") { "%02x".format(it) }
                            log("RX-noCRC (${payloadLen}B): ${hex.take(200)}${if (hex.length > 200) "..." else ""}")
                            handleMessage(payload)
                            continue
                        }
                        break
                    }
                }

                val payload = ByteArray(payloadLen)
                var off = 0
                while (off < payloadLen) {
                    val n = input.read(payload, off, payloadLen - off)
                    if (n < 0) break
                    off += n
                }
                if (off < payloadLen) { log("EOF payload $off/$payloadLen"); break }

                if (crcEnabled) {
                    val crcHi = input.read(); if (crcHi < 0) { log("EOF dataCRC"); break }
                    val crcLo = input.read(); if (crcLo < 0) { log("EOF dataCRC2"); break }
                    val actualCrc = (crcHi shl 8) or crcLo
                    val expectedCrc = SapFraming.crc16(payload)
                    if (actualCrc != expectedCrc) {
                        log("Payload CRC mismatch: got=${"%04x".format(actualCrc)} expected=${"%04x".format(expectedCrc)}")
                        // Still deliver the message (Samsung logs error but continues)
                        val hex = payload.joinToString("") { "%02x".format(it) }
                        log("RX-badCRC (${payloadLen}B): ${hex.take(200)}")
                        handleMessage(payload)
                        continue
                    }
                }

                val hex = payload.joinToString("") { "%02x".format(it) }
                log("RX (${payloadLen}B, crc=$crcEnabled): ${hex.take(200)}${if (hex.length > 200) "..." else ""}")

                handleMessage(payload)
            }
        } catch (e: Exception) {
            log("Read error: ${e.javaClass.simpleName}: ${e.message}")
        }
        log("Read loop ended")
    }

    // --- Message handling ---

    /**
     * Handle an incoming message.
     *
     * Pre-auth: messages use raw 1-byte type (5/6=PeerDesc, 0x10/0x11/0x12=WSM).
     * Post-auth (CRC enabled): messages use SAP 2-byte header (Data/Control frames).
     */
    private fun handleMessage(data: ByteArray) {
        if (data.isEmpty()) return

        if (crcEnabled) {
            // Post-auth: parse as SAP frame (2-byte header)
            if (data.size >= 2) {
                val sapHeader = SapProtocol.decodeHeader(data)
                val sapPayload = if (data.size > 2) data.copyOfRange(2, data.size) else ByteArray(0)
                log("SAP: v=${sapHeader.version} ft=${sapHeader.frameType} sid=${sapHeader.sessionId} (${sapPayload.size}B): ${sapPayload.joinToString("") { "%02x".format(it) }.take(200)}")
                handleSapMessage(sapHeader, sapPayload)
                return
            }
            log("Post-auth msg too short (${data.size}B): ${data.joinToString("") { "%02x".format(it) }}")
            return
        }

        // Pre-auth: first byte is message type
        val msgType = data[0].toInt() and 0xFF
        when (msgType) {
            MSG_AGENT_AUTH_REQ -> handlePeerDescriptionRequest(data)
            MSG_AGENT_AUTH_RESP -> log("Peer Description Response (unexpected)")
            MSG_ACCESSORY_AUTH -> handleAccessoryAuth(data)
            MSG_ACCESSORY_AUTH_RESP -> handleAccessoryAuthResp(data)
            MSG_ACCESSORY_AUTH_VERIFY -> handleAccessoryAuthVerify(data)
            else -> {
                log("Unknown pre-auth msgType=0x${"%02x".format(msgType)} (${data.size}B): ${data.joinToString("") { "%02x".format(it) }.take(200)}")
                tryExtractWsmPacket(data)
            }
        }
    }

    /**
     * Handle Peer Description request (messageType=5) from the watch.
     *
     * Binary format (from sapd saprotocol.cc):
     * [1B messageType=5][2B protocolVersion BE][2B softwareVersion BE]
     * [1B status][4B apcuSize BE][2B ssduSize BE][2B sessions BE]
     * [2B timeout BE][1B unk1][2B unk2 BE][1B unk3]
     * [device info strings: "product;manufacturer;name;profile;"]
     *
     * Previously misidentified as "Agent Auth Request".
     */
    private fun handlePeerDescriptionRequest(data: ByteArray) {
        val text = extractReadableText(data)
        log("Peer Description Request (${data.size}B): $text")

        if (data.size < 20) {
            log("Peer Description too short (${data.size}B)")
            return
        }

        // Parse ALL fields from watch's Peer Description
        val protocolVersion = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
        val softwareVersion = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        val statusOrFeatures = data[5].toInt() and 0xFF
        val apcuSize = ((data[6].toInt() and 0xFF) shl 24) or ((data[7].toInt() and 0xFF) shl 16) or
                ((data[8].toInt() and 0xFF) shl 8) or (data[9].toInt() and 0xFF)
        val ssduSize = ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
        val sessions = ((data[12].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF)
        val timeout = ((data[14].toInt() and 0xFF) shl 8) or (data[15].toInt() and 0xFF)
        val unk1 = data[16].toInt() and 0xFF
        val unk2 = ((data[17].toInt() and 0xFF) shl 8) or (data[18].toInt() and 0xFF)
        val unk3 = data[19].toInt() and 0xFF
        log("Watch PeerDesc: proto=$protocolVersion sw=$softwareVersion status=$statusOrFeatures " +
                "apcu=$apcuSize ssdu=$ssduSize sessions=$sessions timeout=$timeout " +
                "unk1=$unk1 unk2=0x${"%04x".format(unk2)} unk3=$unk3")

        // Build proper Peer Description response
        val response = buildPeerDescriptionResponse(data)
        scope.launch(Dispatchers.IO) {
            try {
                sendFrame(response)
                log("Peer Description Response sent (${response.size}B)")
            } catch (e: Exception) {
                log("Peer Description response error: ${e.message}")
            }
        }
    }

    /**
     * Build a proper Peer Description response (messageType=6).
     *
     * Critical: we must send OUR OWN parameters (not echo the watch's).
     * Each side advertises its capabilities; the lower value is used at runtime.
     *
     * From sapd saprotocol.cc:
     * [1B type=6][2B protocolVersion BE][2B softwareVersion BE]
     * [1B status=0][4B apcuSize BE][2B ssduSize BE][2B sessions BE]
     * [2B timeout BE][1B numTransports][per transport: 1B type + 1B subtype]
     * [1B authMethod][device info: "product;manufacturer;name;profile;"]
     */
    private fun buildPeerDescriptionResponse(request: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()

        // messageType = 6 (response)
        out.add(0x06.toByte())

        // Protocol version: echo the watch's (negotiated — both sides must agree)
        out.add(request[1])  // protocolVersion high
        out.add(request[2])  // protocolVersion low

        // Software version: match watch (513 = 0x0201)
        // Using 770 caused the watch to potentially expect v2 features
        out.add(0x02.toByte())  // 513 >> 8 = 2
        out.add(0x01.toByte())  // 513 & 0xFF = 1

        // status = 0 (OK) — CRITICAL: must be 0
        out.add(0x00.toByte())

        // --- OUR network parameters — use Samsung defaults (same as watch) ---

        // apcuSize = 0x000FFAAA (4B BE) — SA_DEFAULT_APCU_SIZE
        out.add(0x00.toByte())
        out.add(0x0F.toByte())
        out.add(0xFA.toByte())
        out.add(0xAA.toByte())

        // ssduSize = 0xF0AA (2B BE) — SA_DEFAULT_SSDU_SIZE
        out.add(0xF0.toByte())
        out.add(0xAA.toByte())

        // sessions = 0x03FE (2B BE) — SA_MAX_SESSION_NUM (1022)
        out.add(0x03.toByte())
        out.add(0xFE.toByte())

        // timeout = 0xFFFF (2B BE) — SA_DEFAULT_TIMEOUT (no timeout)
        out.add(0xFF.toByte())
        out.add(0xFF.toByte())

        // Transport/auth section — echo watch's values: 02 00 0A 01
        // Exact match avoids device info parsing offset issues
        out.add(0x02.toByte())
        out.add(0x00.toByte())
        out.add(0x0A.toByte())
        out.add(0x01.toByte())

        // Our device info strings (semicolon-separated: product;manufacturer;name;profile;)
        // Profile MUST be "SWatch" — matches Gear Manager (from sapd)
        val deviceInfo = "GT-I9500;Samsung;Galaxy S4;SWatch;"
        for (b in deviceInfo.toByteArray(Charsets.UTF_8)) {
            out.add(b)
        }

        log("PeerDesc Response: sw=513, apcu=0x000FFAAA, ssdu=0xF0AA, sessions=1022, timeout=0xFFFF, transport=02 00 0A 01")
        return out.toByteArray()
    }

    /**
     * Handle Accessory Auth message (type 0x10 = 16).
     * Contains WSM client challenge starting at offset 2.
     */
    private fun handleAccessoryAuth(data: ByteArray) {
        log("Accessory Auth (${data.size}B) — WSM challenge")

        // WSM auth packet starts at offset 2 (skip 2-byte message header)
        if (data.size > 5) {
            val wsmData = data.copyOfRange(2, data.size)
            val wsmLen = wsmData[2].toInt() and 0xFF
            if (wsmLen == wsmData.size) {
                log("WSM challenge: v=${wsmData[0]}, t=${wsmData[1]}, len=$wsmLen")
                // Response uses type 0x11 (AccessoryAuthResp), not 0x10
                processWsmChallenge(wsmData, byteArrayOf(MSG_ACCESSORY_AUTH_RESP.toByte(), 0x00))
                return
            }
        }
        tryExtractWsmPacket(data)
    }

    /**
     * Handle Accessory Auth Response (type 0x11 = 17).
     * Contains WSM client response (verification) or auth status.
     */
    private fun handleAccessoryAuthResp(data: ByteArray) {
        log("Accessory Auth Resp (${data.size}B)")

        // Try to find WSM client response at various offsets
        for (offset in 2..minOf(4, data.size - 4)) {
            val remaining = data.size - offset
            if (remaining > 3) {
                val wsmData = data.copyOfRange(offset, data.size)
                val wsmLen = wsmData[2].toInt() and 0xFF
                if (wsmLen == remaining && wsmLen > 3) {
                    log("WSM response at offset $offset: v=${wsmData[0]}, t=${wsmData[1]}, len=$wsmLen")
                    handleWsmClientResponse(wsmData)
                    return
                }
            }
        }

        // Check if this is a status-only message (e.g., auth success/failure)
        if (data.size >= 2) {
            val status = data[1].toInt() and 0xFF
            log("Auth status byte: 0x${"%02x".format(status)} (0=success?)")
            if (status == 0) {
                log("Accessory Auth appears successful (status=0)")
                wsmAuthComplete?.complete(true)
                return
            }
        }

        log("Could not parse auth response")
    }

    /**
     * Handle Accessory Auth Verify (type 0x12 = 18).
     * Contains the WSM client response (35 bytes at offset 2).
     */
    private fun handleAccessoryAuthVerify(data: ByteArray) {
        log("Accessory Auth Verify (${data.size}B) — WSM client response")

        // WSM client response at offset 2 (skip 2-byte message header)
        if (data.size > 5) {
            val wsmData = data.copyOfRange(2, data.size)
            val wsmLen = wsmData[2].toInt() and 0xFF
            if (wsmLen == wsmData.size) {
                log("WSM client response: v=${wsmData[0]}, t=${wsmData[1]}, len=$wsmLen")
                handleWsmClientResponse(wsmData)
                return
            }
        }
        log("Could not extract WSM client response")
    }

    /**
     * Try to find a WSM auth packet at various offsets in the data.
     */
    private fun tryExtractWsmPacket(data: ByteArray) {
        for (offset in 0..minOf(4, data.size - 4)) {
            val remaining = data.size - offset
            if (remaining > 3) {
                val length = data[offset + 2].toInt() and 0xFF
                if (length == remaining && length > 3) {
                    val wsmData = data.copyOfRange(offset, data.size)
                    val prefix = if (offset > 0) data.copyOf(offset) else byteArrayOf()
                    log("WSM packet at offset $offset: v=${wsmData[0]}, t=${wsmData[1]}, len=$length")
                    processWsmChallenge(wsmData, prefix)
                    return
                }
            }
        }
        log("No WSM packet found in data")
    }

    /**
     * Process a WSM challenge and send server response.
     * @param wsmData The raw WSM AuthPacket bytes
     * @param prefix Bytes to prepend to the response (message type header)
     */
    private fun processWsmChallenge(wsmData: ByteArray, prefix: ByteArray) {
        val client = wsmClient
        if (client == null) {
            log("WSM Client not initialized")
            return
        }

        try {
            val authPacket = AuthPacket(wsmData)
            val type = authPacket.type.toInt() and 0xFF
            log("WSM challenge: version=${authPacket.version}, type=$type, len=${authPacket.length}")

            val serverChallenge = client.checkAndGenerateServerChallenge(authPacket)
            val responsePayload = serverChallenge.payload
            log("WSM server challenge generated (${responsePayload.size}B)")

            // Send response with same prefix header
            val response = prefix + responsePayload
            scope.launch(Dispatchers.IO) {
                try {
                    sendFrame(response)
                    log("WSM server challenge sent (${response.size}B)")
                } catch (e: Exception) {
                    log("WSM send error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            log("WSM challenge error: ${e.message}")
            // Maybe this wasn't really a WSM challenge
        }
    }

    /**
     * Handle a potential WSM client response (type 3).
     * This arrives after we send the server challenge.
     */
    private fun handleWsmClientResponse(wsmData: ByteArray) {
        val client = wsmClient ?: return
        try {
            val authPacket = AuthPacket(wsmData)
            log("WSM response: type=${authPacket.type}, len=${authPacket.length}")
            client.checkClientResponse(authPacket)
            log("WSM Auth verified!")
            // Enable CRC in the read loop thread BEFORE completing,
            // so the next read() already uses CRC (avoids race condition)
            crcEnabled = true
            log("CRC enabled (from read loop)")
            wsmAuthComplete?.complete(true)
        } catch (e: Exception) {
            log("WSM verify error: ${e.message}")
            wsmAuthComplete?.complete(false)
        }
    }

    /**
     * Handle post-auth SAP messages.
     *
     * SAP header: [3b version][1b frameType: 0=Data, 1=Control][10b sessionId][2b unused]
     * Service management messages (session 0x3FF) contain:
     *   [1B msgType][2B agentId][2B peerId][...payload...]
     */
    private fun handleSapMessage(header: SapProtocol.SapHeader, payload: ByteArray) {
        when (header.frameType) {
            SapProtocol.FRAME_TYPE_DATA -> {
                if (header.sessionId == SapProtocol.DEFAULT_SESSION_ID) {
                    // Service management message on default session (0x3FF)
                    handleServiceManagementMessage(payload)
                } else if (header.sessionId == capexSessionId) {
                    // CAPEX capability data — uses its own message format (not type=3 wrapped)
                    val text = extractReadableText(payload)
                    log("SAP Data (session=${header.sessionId}, ${payload.size}B): $text")
                    handleCapexData(header.sessionId, payload)
                } else {
                    // Connected session data — strip type=3 Data wrapping if present
                    val parsed = SapProtocol.parseDataPayload(payload)
                    val appData: ByteArray
                    val channelId: Int
                    if (parsed != null) {
                        channelId = parsed.first
                        appData = parsed.second
                        log("SAP Data msg (session=${header.sessionId}, channel=$channelId, ${appData.size}B)")
                    } else {
                        // Raw data without type=3 wrapping (fallback)
                        appData = payload
                        channelId = -1
                        log("SAP Data raw (session=${header.sessionId}, ${payload.size}B): ${payload.joinToString("") { "%02x".format(it) }.take(100)}")
                    }

                    if (header.sessionId == hostManagerSessionId && hostManagerSessionId != 0) {
                        handleHostManagerData(appData)
                    } else if (connectedSessions.containsKey(header.sessionId)) {
                        val profile = connectedSessions[header.sessionId]
                        handleServiceData(header.sessionId, profile ?: "", appData)
                    } else {
                        val text = extractReadableText(appData)
                        log("  Unknown session data: $text")
                        log("  hex: ${appData.joinToString("") { "%02x".format(it) }.take(200)}")
                    }
                }
            }
            SapProtocol.FRAME_TYPE_CONTROL -> {
                log("SAP Control (session=${header.sessionId}, ${payload.size}B): ${payload.joinToString("") { "%02x".format(it) }.take(200)}")
            }
            else -> {
                log("SAP unknown frameType=${header.frameType} session=${header.sessionId}")
            }
        }
    }

    /**
     * Handle service management messages on the default session (0x3FF).
     */
    private fun handleServiceManagementMessage(payload: ByteArray) {
        if (payload.isEmpty()) return
        val msgType = payload[0].toInt() and 0xFF
        when (msgType) {
            SapProtocol.MSG_SERVICE_CONN_REQUEST -> {
                log("Service Conn Request: ${payload.joinToString("") { "%02x".format(it) }.take(200)}")
                handleIncomingServiceConnectionRequest(payload)
            }
            SapProtocol.MSG_SERVICE_CONN_RESPONSE -> {
                log("Service Conn Response: ${payload.joinToString("") { "%02x".format(it) }.take(200)}")
                handleIncomingServiceConnectionResponse(payload)
            }
            SapProtocol.MSG_DATA -> {
                // Data message on default session — route by channelId
                if (payload.size >= 3) {
                    val channelId = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
                    val appData = payload.copyOfRange(3, payload.size)
                    log("Service Data on default session: channelId=$channelId (${appData.size}B)")
                    when (channelId) {
                        hostManagerChannelId -> handleHostManagerData(appData)
                        else -> log("  hex: ${appData.joinToString("") { "%02x".format(it) }.take(200)}")
                    }
                } else {
                    log("Service Data too short on default session (${payload.size}B)")
                }
            }
            SapProtocol.MSG_DISCONNECT -> {
                // Disconnect/Close — extract profile name
                val text = extractReadableText(payload)
                log("Service Disconnect: $text")
            }
            else -> {
                log("Service mgmt msgType=$msgType (${payload.size}B): ${payload.joinToString("") { "%02x".format(it) }.take(200)}")
            }
        }
    }

    /**
     * Handle an incoming Service Connection Request from the watch.
     *
     * Parse format (from sapd saprotocol.cc):
     * [1B type=1][2B acceptorId BE][2B initiatorId BE]
     * [profile + ';'][2B nSessions BE]
     * [2B*n sessionIds][2B*n channelIds][3B*n QoS][1B*n payloadType]
     *
     * acceptorId = our componentId (target of the connection)
     * initiatorId = watch's componentId (they're connecting to us)
     *
     * Respond with type=2, status=0 (OK), echoing session IDs.
     */
    private fun handleIncomingServiceConnectionRequest(payload: ByteArray) {
        if (payload.size < 6) return

        // acceptorId = our componentId (we're being connected to)
        val ourAgentId = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        // initiatorId = watch's componentId (they're connecting)
        val watchAgentId = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)

        // Extract profile name (terminated by ';')
        var offset = 5
        val profileStart = offset
        while (offset < payload.size && payload[offset] != 0x3B.toByte()) offset++
        if (offset >= payload.size) return
        val profile = String(payload, profileStart, offset - profileStart, Charsets.UTF_8)
        offset++ // skip ';'

        // Read nSessions
        if (offset + 2 > payload.size) return
        val nSessions = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        offset += 2

        // Read session IDs
        val sessionIds = mutableListOf<Int>()
        for (i in 0 until nSessions) {
            if (offset + 2 > payload.size) return
            val sid = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
            sessionIds.add(sid)
            offset += 2
        }

        // Read channel IDs
        val channelIds = mutableListOf<Int>()
        for (i in 0 until nSessions) {
            if (offset + 2 > payload.size) break
            val cid = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
            channelIds.add(cid)
            offset += 2
        }

        // Read QoS (3 bytes per session: type, dataRate, priority)
        val qosBytes = mutableListOf<Byte>()
        for (i in 0 until nSessions) {
            if (offset + 3 > payload.size) break
            qosBytes.add(payload[offset]); qosBytes.add(payload[offset + 1]); qosBytes.add(payload[offset + 2])
            offset += 3
        }

        // Read payload types (1 byte per session)
        val payloadTypes = mutableListOf<Byte>()
        for (i in 0 until nSessions) {
            if (offset >= payload.size) break
            payloadTypes.add(payload[offset])
            offset++
        }

        log("Service Conn Request: profile=$profile, ourAgent=$ourAgentId, watchAgent=$watchAgentId, sessions=$sessionIds, channels=$channelIds, qos=${qosBytes.joinToString("") { "%02x".format(it) }}, payloadType=${payloadTypes.map { it.toInt() and 0xFF }}")

        // Build 16: Echo watch's QoS + payloadType EXACTLY.
        // Key insight: QoS type=4 (ReliabilityDisable) → supportsFragmentation=true in sapd.
        // Data frames need a 1-byte fragmentation header (0x00 = FragmentNone).
        // Previous builds either overrode QoS to type=0 or echoed type=4
        // but NEVER added the fragmentation byte to data frames.
        val resp = buildServiceConnectionResponse(ourAgentId, watchAgentId, profile, sessionIds, channelIds, qosBytes, payloadTypes)
        val frame = SapProtocol.wrapInDefaultFrame(resp)
        sendFrame(frame)
        log("Service Conn Response sent (accepted, ${resp.size}B, qos=${qosBytes.joinToString("") { "%02x".format(it) }}, payloadType=${payloadTypes.map { it.toInt() and 0xFF }})")

        // Track connected services
        val sid = sessionIds.firstOrNull() ?: 0
        val cid = channelIds.firstOrNull() ?: 0
        connectedSessions[sid] = profile
        connectedChannels[sid] = cid

        if (profile.contains("CapabilityDiscovery", ignoreCase = true)) {
            // CAPEX Service Connection initiated by watch!
            capexSessionId = sid
            log("CAPEX connected on session=$sid (watch-initiated!)")
            // Control frame on CAPEX session (may be needed for watch to process updates)
            val capexCtrl = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_CONTROL, sid, ByteArray(0))
            sendFrame(capexCtrl)
            log("Control frame sent on CAPEX session=$sid")
            capexConnReceived?.complete(true)
        } else if (profile == GearConstants.PROFILE_HOST_MANAGER) {
            hostManagerSessionId = sid
            hostManagerChannelId = channelIds.firstOrNull() ?: GearConstants.CHANNEL_HOST_MANAGER
            // Build 16: Check QoS type for fragmentation support
            val hmQosType = if (qosBytes.isNotEmpty()) qosBytes[0].toInt() and 0xFF else 0
            hmUsesFragmentation = (hmQosType == 4)  // type=4 (ReliabilityDisable) → frag header required
            val expectedId = ourComponentId(GearConstants.PROFILE_HOST_MANAGER)
            log("Host Manager connected on session=$hostManagerSessionId, channel=$hostManagerChannelId (watch-initiated!)")
            log("  acceptorId=$ourAgentId (expected=$expectedId), initiatorId=$watchAgentId")
            log("  QoS type=$hmQosType, fragmentation=$hmUsesFragmentation")

            // Don't send immediately — let the watch's handler initialize first.
            // Signal the connect coroutine to send after a delay.
            hmConnReceived?.complete(true)
            serviceConnReceived?.complete(true)
        } else if (profile == GearConstants.PROFILE_FILE_TRANSFER) {
            // Watch initiated FT SC to our provider!
            log("FT: Watch initiated filetransfer SC! session=$sid, channel=$cid")
            ftCommandSessionId = sid
            ftCommandChannelId = cid
            if (sessionIds.size > 1) {
                ftDataSessionId = sessionIds[1]
                ftDataChannelId = if (channelIds.size > 1) channelIds[1] else GearConstants.FT_CHANNEL_DATA
            }
            // Register all FT sessions
            for (i in sessionIds.indices) {
                connectedSessions[sessionIds[i]] = profile
                if (i < channelIds.size) connectedChannels[sessionIds[i]] = channelIds[i]
            }
            log("FT: cmd=$ftCommandSessionId ch=$ftCommandChannelId, data=$ftDataSessionId ch=$ftDataChannelId")
            ftConnReceived?.complete(true)
        } else if (profile == GearConstants.PROFILE_CLOCK_SETTINGS) {
            clockSettingsSessionId = sid
            clockSettingsChannelId = cid
            clockSettingsQosType = if (qosBytes.isNotEmpty()) qosBytes[0].toInt() and 0xFF else 0
            clockSettingsSeqNum = 0
            _clockSettingsConnected.value = true
            log("Clock settings connected on session=$clockSettingsSessionId, channel=$clockSettingsChannelId, qos=$clockSettingsQosType")
        } else {
            log("Service $profile accepted (session=$sid)")
            // sfota connection = watch is alive and proceeding with setup
            if (profile.contains("sfota")) {
                sfotaHandled?.complete(true)
            }
            // Note: /system/setting (dual_clock_consumer) does NOT respond to any
            // known message format on Tizen 2.2.1.1 — per-clock settings are not available
        }
    }

    /**
     * Build a Service Connection Response (type=2).
     *
     * [1B type=2][2B acceptorId BE][2B initiatorId BE][profile + ';']
     * [1B status=0][2B nSessions BE][2B*n sessionIds BE]
     * [2B*n channelIds BE][3B*n QoS][1B*n payloadType]
     *
     * NOTE: All fields must be echoed from request — watch validates response
     * completeness (tested: short response without channel/QoS/payloadType
     * causes watch to disconnect RFCOMM immediately).
     */
    private fun buildServiceConnectionResponse(
        ourAgentId: Int,
        watchAgentId: Int,
        profile: String,
        sessionIds: List<Int>,
        channelIds: List<Int> = emptyList(),
        qosBytes: List<Byte> = emptyList(),
        payloadTypes: List<Byte> = emptyList(),
    ): ByteArray {
        val out = mutableListOf<Byte>()

        // Type = 2 (response)
        out.add(0x02.toByte())

        // Our componentId (acceptor)
        out.add(((ourAgentId shr 8) and 0xFF).toByte())
        out.add((ourAgentId and 0xFF).toByte())

        // Watch's componentId (initiator)
        out.add(((watchAgentId shr 8) and 0xFF).toByte())
        out.add((watchAgentId and 0xFF).toByte())

        // Profile + ';'
        for (b in profile.toByteArray(Charsets.UTF_8)) out.add(b)
        out.add(0x3B.toByte())

        // Status = 0 (OK)
        out.add(0x00.toByte())

        // Number of sessions
        out.add(((sessionIds.size shr 8) and 0xFF).toByte())
        out.add((sessionIds.size and 0xFF).toByte())

        // Session IDs
        for (sid in sessionIds) {
            out.add(((sid shr 8) and 0xFF).toByte())
            out.add((sid and 0xFF).toByte())
        }

        // Channel IDs (echo from request)
        for (cid in channelIds) {
            out.add(((cid shr 8) and 0xFF).toByte())
            out.add((cid and 0xFF).toByte())
        }

        // QoS bytes (echo from request)
        out.addAll(qosBytes)

        // Payload types (echo from request)
        out.addAll(payloadTypes)

        return out.toByteArray()
    }

    /**
     * Handle an incoming Service Connection Response (type=2) from the watch.
     *
     * Format (from sapd saprotocol.cc):
     * [1B type=2][2B agentId BE][2B peerId BE][profile + ';']
     * [1B status][2B nSessions BE][2B*n sessionIds BE]
     */
    private fun handleIncomingServiceConnectionResponse(payload: ByteArray) {
        if (payload.size < 6) {
            serviceConnReceived?.complete(true)
            return
        }

        val agentId = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        val peerId = ((payload[3].toInt() and 0xFF) shl 8) or (payload[4].toInt() and 0xFF)

        // Extract profile name (terminated by ';')
        var offset = 5
        val profileStart = offset
        while (offset < payload.size && payload[offset] != 0x3B.toByte()) offset++
        val profile = if (offset > profileStart) {
            String(payload, profileStart, offset - profileStart, Charsets.UTF_8)
        } else "?"
        if (offset < payload.size) offset++ // skip ';'

        // Status byte
        val status = if (offset < payload.size) payload[offset].toInt() and 0xFF else -1
        offset++

        // Number of sessions
        val nSessions = if (offset + 2 <= payload.size) {
            ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        } else 0
        offset += 2

        // Session IDs
        val sessionIds = mutableListOf<Int>()
        for (i in 0 until nSessions) {
            if (offset + 2 > payload.size) break
            val sid = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
            sessionIds.add(sid)
            offset += 2
        }

        log("Service Conn Response: profile=$profile, status=$status, agent=$agentId, peer=$peerId, sessions=$sessionIds")

        if (profile == GearConstants.PROFILE_HOST_MANAGER && status == 0) {
            hostManagerSessionId = sessionIds.firstOrNull() ?: 2
            hostManagerChannelId = GearConstants.CHANNEL_HOST_MANAGER
            // Watch always sends frag byte on HM data, even for self-initiated SC
            hmUsesFragmentation = true
            log("Host Manager accepted by watch! session=$hostManagerSessionId (frag=true)")
            serviceConnReceived?.complete(true)
        } else if (profile.contains("CapabilityDiscovery", ignoreCase = true)) {
            // CAPEX response
            serviceConnReceived?.complete(true)
        } else if (profile == GearConstants.PROFILE_FILE_TRANSFER) {
            if (status == 0) {
                val sid = sessionIds.firstOrNull() ?: 0
                connectedSessions[sid] = profile
                // Also register second session if present
                if (sessionIds.size > 1) connectedSessions[sessionIds[1]] = profile
                log("FT: Service Connection ACCEPTED! sessions=$sessionIds, agent=$agentId, peer=$peerId")
                ftConnReceived?.complete(true)
            } else {
                log("FT: Service Connection REJECTED status=$status, agent=$agentId, peer=$peerId — an agent responded!")
                // A rejection still means we found a valid agentId
                ftConnReceived?.complete(false)
            }
        } else {
            log("SC Response: profile=$profile, status=$status (unhandled)")
            serviceConnReceived?.complete(true)
        }
    }

    /**
     * Handle CAPEX capability data on a CAPEX session.
     *
     * Type 1 = Query: flat list of profile names
     * Type 2 = Response: nested provider/service structure with componentIds
     */
    private fun handleCapexData(sessionId: Int, payload: ByteArray) {
        if (payload.size < 2) return
        val type = payload[0].toInt() and 0xFF
        capexSessionId = sessionId

        when (type) {
            1 -> {
                // Query from watch — respond with our capabilities
                val numProfiles = if (payload.size >= 7) payload[6].toInt() and 0xFF else 0
                log("CAPEX query from watch: $numProfiles profiles")
                val response = buildCapexResponse()
                val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, sessionId, response)
                sendFrame(frame)
                log("CAPEX response sent (our capabilities)")
                capexDataReceived?.complete(true)
            }
            2 -> {
                // Response from watch — parse to extract remote componentIds
                log("CAPEX response from watch (${payload.size}B): ${payload.joinToString("") { "%02x".format(it) }}")
                parseCapexResponse(payload)
                capexResponseReceived?.complete(true)
            }
            else -> {
                log("CAPEX unknown type=$type (${payload.size}B)")
            }
        }
    }

    /**
     * Parse a CAPEX Response (type=2) from the watch.
     *
     * Format (from sapd):
     * [1B type=2][1B queryType=3][4B checksum BE]
     * [2B numProviders BE]
     * Per provider:
     *   [2B uuid BE][name + ';'][2B numServices BE]
     *   Per service:
     *     [2B componentId BE][profile + ';'][2B aspVersion BE][1B role][2B connTimeout BE]
     */
    private fun parseCapexResponse(payload: ByteArray) {
        if (payload.size < 8) return
        var offset = 6 // skip type(1) + queryType(1) + checksum(4)

        val numProviders = ((payload[offset].toInt() and 0xFF) shl 8) or
                (payload[offset + 1].toInt() and 0xFF)
        offset += 2
        log("CAPEX response: $numProviders providers")

        for (p in 0 until numProviders) {
            if (offset + 2 > payload.size) break
            val uuid = ((payload[offset].toInt() and 0xFF) shl 8) or
                    (payload[offset + 1].toInt() and 0xFF)
            offset += 2

            // Provider name (terminated by ';')
            val nameStart = offset
            while (offset < payload.size && payload[offset] != 0x3B.toByte()) offset++
            val name = if (offset > nameStart) String(payload, nameStart, offset - nameStart, Charsets.UTF_8) else "?"
            if (offset < payload.size) offset++ // skip ';'

            if (offset + 2 > payload.size) break
            val numServices = ((payload[offset].toInt() and 0xFF) shl 8) or
                    (payload[offset + 1].toInt() and 0xFF)
            offset += 2

            log("  Provider: uuid=0x${"%04x".format(uuid)}, name=$name, services=$numServices")

            for (s in 0 until numServices) {
                if (offset + 2 > payload.size) break
                val componentId = ((payload[offset].toInt() and 0xFF) shl 8) or
                        (payload[offset + 1].toInt() and 0xFF)
                offset += 2

                // Profile (terminated by ';')
                val profStart = offset
                while (offset < payload.size && payload[offset] != 0x3B.toByte()) offset++
                val profile = if (offset > profStart) String(payload, profStart, offset - profStart, Charsets.UTF_8) else "?"
                if (offset < payload.size) offset++ // skip ';'

                if (offset + 5 > payload.size) break
                val aspVersion = ((payload[offset].toInt() and 0xFF) shl 8) or
                        (payload[offset + 1].toInt() and 0xFF)
                offset += 2
                val role = payload[offset].toInt() and 0xFF
                offset++
                val connTimeout = ((payload[offset].toInt() and 0xFF) shl 8) or
                        (payload[offset + 1].toInt() and 0xFF)
                offset += 2

                log("    Service: componentId=$componentId, profile=$profile, role=$role")
                remoteAgentIds[profile] = componentId
            }
        }
    }

    /**
     * Build a CAPEX CapabilityDiscoveryQuery (type 0x01).
     *
     * From sapd capabilitypeer.cc:
     * [1B type=1][1B queryType=2][4B checksum=1 BE][1B numRecords]
     * [profile1;][profile2;]...
     */
    private fun buildCapexQuery(): ByteArray {
        val profiles = listOf(
            "/system/hostmanager",
            "/system/setting",
            "/system/fmp",
            "/system/NotificationService",
            "/system/callhandler",
            "/system/weather",
            "/system/music",
            "/system/alarm",
            "/system/calendar",
        )
        val out = mutableListOf<Byte>()
        out.add(0x01.toByte())        // messageType = Query
        out.add(0x02.toByte())        // queryType = 2
        out.add(0x00.toByte())        // checksum (uint32 BE) = 0 (no cache)
        out.add(0x00.toByte())
        out.add(0x00.toByte())
        out.add(0x00.toByte())
        out.add(profiles.size.toByte()) // numRecords
        for (profile in profiles) {
            for (b in "$profile;".toByteArray(Charsets.UTF_8)) out.add(b)
        }
        return out.toByteArray()
    }

    /**
     * Send CAPEX incremental update messages (type 3) for our services.
     * This mimics what Samsung Accessory Service does when services register
     * after the initial CAPEX exchange. The watch expects these to know
     * when to initiate Service Connections.
     *
     * Format per message:
     * [1B type=3][1B queryType=0][2B numRecords=1 BE]
     * [1B updateType=1 (INSTALL)][2B uuid BE][name;][2B numServices=1 BE]
     * [2B componentId BE][profile;][2B aspVersion BE][1B role][2B connTimeout BE]
     */
    private fun sendCapexUpdates() {
        // Send INSTALL updates for ALL providers (not just a subset).
        // The watch may need to see all services as "active" before its
        // HostManager app activates and starts the setup conversation.
        for (prov in capexProviders) {
            val out = mutableListOf<Byte>()

            out.add(0x03.toByte())  // messageType = 3 (Incremental Update)
            out.add(0x00.toByte())  // queryType = 0 (no checksum)
            out.add(0x00.toByte()); out.add(0x01.toByte())  // numRecords = 1

            out.add(0x01.toByte())  // updateType = 1 (INSTALL)
            out.add(((prov.providerUuid shr 8) and 0xFF).toByte())
            out.add((prov.providerUuid and 0xFF).toByte())
            for (b in "${prov.providerName};".toByteArray(Charsets.UTF_8)) out.add(b)
            out.add(0x00.toByte()); out.add(0x01.toByte())  // numServices = 1
            out.add(((prov.componentId shr 8) and 0xFF).toByte())
            out.add((prov.componentId and 0xFF).toByte())
            for (b in "${prov.profile};".toByteArray(Charsets.UTF_8)) out.add(b)
            out.add(0x00.toByte()); out.add(0x01.toByte())  // aspVersion
            out.add(0x00.toByte())  // role = 0 (Provider)
            out.add(0x00.toByte()); out.add(0x00.toByte())  // connTimeout

            val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, capexSessionId, out.toByteArray())
            sendFrame(frame)
            log("CAPEX update sent: ${prov.profile} (componentId=${prov.componentId})")
        }
    }

    /**
     * Build a CAPEX CapabilityDiscoveryResponse (type 0x02).
     *
     * Advertise only the services we actually implement as Provider (role=0).
     * One provider per service with UUID=0 — matches sapd structure exactly.
     * sapd only registers ~4 agents, not all 22 the watch asks about.
     */
    /**
     * CAPEX service descriptors — 1 provider per service (like sapd).
     * Each provider has its own UUID and name. ComponentIds are hash-based (like watch's).
     */
    private data class CapexProvider(
        val profile: String,
        val providerName: String,
        val providerUuid: Int,
        val componentId: Int,
    )

    private val capexProviders: List<CapexProvider> = listOf(
        "/system/hostmanager"         to "HostManagerProvider",
        "/system/setting"             to "SettingProvider",
        "/system/fmp"                 to "FindMyPhoneProvider",
        "/system/NotificationService" to "NotiProvider",
        "/system/callhandler"         to "CallHandlerProvider",
        "/system/weather"             to "WeatherProvider",
        "/system/music"               to "MediaControllerProvider",
        "/system/alarm"               to "AlarmProvider",
        "/system/calendar"            to "CalendarProvider",
        "/app/minimessage"            to "MiniMessageProvider",
        "/system/svoice"              to "SVoiceProvider",
        "/system/context"             to "ContextProvider",
        "/app/camera"                 to "CameraProvider",
        "/system/emailnotification"   to "EmailNotiProvider",
        "/system/watch_pedometer"     to "PedometerProvider",
        "/system/bcmservice"          to "BCMServiceProvider",
        "/system/voicememo"           to "VoiceMemoProvider",
        "/samsung/sensor"             to "SensorProvider",
        "/system/musictransfer"       to "MusicTransferProvider",
        "/system/sfota"               to "SFOTAProvider",
        "/system/filetransfer"        to "FileTransferProvider",
        "/system/b2contacts"          to "B2ContactsProvider",
        "/organizeur/clocksettings"   to "ClockSettingsProvider",
    ).map { (profile, name) ->
        CapexProvider(
            profile = profile,
            providerName = name,
            providerUuid = capexHash(name),
            componentId = capexHash(profile),
        )
    }

    /** Deterministic 16-bit hash for CAPEX UUIDs and componentIds */
    private fun capexHash(s: String): Int {
        val h = s.hashCode() and 0xFFFF
        return if (h == 0) 1 else h
    }

    /** Get our componentId for a given profile (from CAPEX) */
    private fun ourComponentId(profile: String): Int {
        return capexProviders.find { it.profile == profile }?.componentId ?: capexHash(profile)
    }

    /**
     * Build a CAPEX CapabilityDiscoveryResponse (type 0x02).
     * 1 provider per service (like sapd), each with unique UUID and componentId.
     * CRC32 checksum computed over the capability data section.
     */
    private fun buildCapexResponse(): ByteArray {
        // Build capability data first (providers section)
        val capData = mutableListOf<Byte>()

        // numProviders (2B BE)
        capData.add(((capexProviders.size shr 8) and 0xFF).toByte())
        capData.add((capexProviders.size and 0xFF).toByte())

        for (prov in capexProviders) {
            // Provider UUID (2B BE)
            capData.add(((prov.providerUuid shr 8) and 0xFF).toByte())
            capData.add((prov.providerUuid and 0xFF).toByte())
            // Provider name + ';'
            for (b in "${prov.providerName};".toByteArray(Charsets.UTF_8)) capData.add(b)
            // numServices = 1 (2B BE)
            capData.add(0x00.toByte())
            capData.add(0x01.toByte())
            // Service: componentId (2B BE)
            capData.add(((prov.componentId shr 8) and 0xFF).toByte())
            capData.add((prov.componentId and 0xFF).toByte())
            // profile + ';'
            for (b in "${prov.profile};".toByteArray(Charsets.UTF_8)) capData.add(b)
            // aspVersion = 1.0 (2B BE)
            capData.add(0x00.toByte()); capData.add(0x01.toByte())
            // role = 0 (Provider)
            capData.add(0x00.toByte())
            // connTimeout = 0 (2B BE)
            capData.add(0x00.toByte()); capData.add(0x00.toByte())
        }

        // Compute CRC32 checksum of capability data
        val crc32 = CRC32()
        crc32.update(capData.toByteArray())
        val checksum = crc32.value.toInt()

        // Build final response: header + capData
        val out = mutableListOf<Byte>()
        out.add(0x02.toByte())        // messageType = Response
        out.add(0x03.toByte())        // queryType = 3 (REQUIRED — queryType=2 breaks HM SC trigger)
        // checksum (uint32 BE)
        out.add(((checksum shr 24) and 0xFF).toByte())
        out.add(((checksum shr 16) and 0xFF).toByte())
        out.add(((checksum shr 8) and 0xFF).toByte())
        out.add((checksum and 0xFF).toByte())
        // Append capability data
        out.addAll(capData)

        return out.toByteArray()
    }

    // --- Connected service data handling ---

    /**
     * Handle data from a connected service session.
     * Dispatches by profile, with special handling for sfota.
     */
    private fun handleServiceData(sessionId: Int, profile: String, payload: ByteArray) {
        // Strip 1-byte null prefix (observed on sfota messages)
        val jsonStart = if (payload.isNotEmpty() && payload[0] == 0x00.toByte()) 1 else 0
        val dataBytes = payload.copyOfRange(jsonStart, payload.size)
        val text = String(dataBytes, Charsets.UTF_8)
        log("Service[$profile] RX (session=$sessionId): $text")

        when {
            profile == "/system/sfota" -> handleSfotaData(sessionId, text)
            profile == GearConstants.PROFILE_FILE_TRANSFER -> handleFileTransferData(sessionId, dataBytes)
            profile == "/system/setting" -> {
                // Log all setting messages with hex prefix for protocol discovery
                val hexPrefix = dataBytes.take(16).joinToString(" ") { "%02x".format(it) }
                try {
                    val json = JSONObject(text)
                    val msgId = json.optString("msgId", "?")
                    log("Setting[$msgId]: $text")
                } catch (_: Exception) {
                    log("Setting (non-JSON, hex=$hexPrefix, ${dataBytes.size}B): ${text.take(500)}")
                }
            }
            profile == GearConstants.PROFILE_CLOCK_SETTINGS -> {
                log("Clock settings RX: $text")
            }
            else -> log("Service[$profile] unhandled data (${payload.size}B)")
        }
    }

    /**
     * Initiate Host Manager Service Connection ourselves (fallback).
     * Then try multiple message formats since the watch accepts but doesn't respond.
     */
    private suspend fun connectHostManagerSelf() {
        val watchHmId = remoteAgentIds[GearConstants.PROFILE_HOST_MANAGER] ?: 1
        serviceConnReceived = CompletableDeferred()
        _state.value = GearState.Connecting("Host Manager...")
        val ourHmComponentId = ourComponentId(GearConstants.PROFILE_HOST_MANAGER)
        log("Sending Host Manager ServiceConnection (acceptor=$watchHmId, initiator=$ourHmComponentId)...")
        val hmPayload = SapProtocol.buildServiceConnectionRequest(
            acceptorId = watchHmId,
            initiatorId = ourHmComponentId,
            profileName = GearConstants.PROFILE_HOST_MANAGER,
            sessionId = 2,
            channelId = GearConstants.CHANNEL_HOST_MANAGER,
            qosPriority = SapProtocol.QOS_PRIORITY_HIGH,
            payloadType = SapProtocol.PAYLOAD_JSON,
        )
        val hmFrame = SapProtocol.wrapInDefaultFrame(hmPayload)
        sendFrame(hmFrame)

        val ok = withTimeoutOrNull(15000) { serviceConnReceived?.await() }
        if (ok == true) {
            log("Host Manager accepted (self-initiated)! session=$hostManagerSessionId")
            // Try shotgun approach: send critical messages in multiple formats
            delay(200)
            tryShotgunUnlock()
            // Wait 10s for any response
            delay(10000)
            log("Attente 10s écoulée — vérification réponse...")
        } else {
            log("Host Manager timeout")
        }
    }

    /**
     * Handle sfota (firmware update) messages.
     * Respond to fota-sysscopestatus-req so the watch knows we're alive.
     */
    private fun handleSfotaData(sessionId: Int, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val msgId = json.optString("msgId", "")
            log("sfota msgId=$msgId")

            when (msgId) {
                "fota-sysscopestatus-req" -> {
                    // Respond with sysscope status (no update available)
                    val reply = JSONObject()
                    reply.put("msgId", "fota-sysscopestatus-res")
                    reply.put("status", 0)
                    reply.put("sysscopeStatus", 0) // 0 = no update
                    val replyBytes = reply.toString().toByteArray(Charsets.UTF_8)
                    val channelId = connectedChannels[sessionId] ?: sessionId
                    val appData = byteArrayOf(0x00) + replyBytes  // 1-byte null prefix
                    sendServiceData(sessionId, channelId, appData)
                    log("sfota response sent: no update (type=3, channel=$channelId)")
                    sfotaHandled?.complete(true)
                }
                else -> log("sfota unknown msgId=$msgId")
            }
        } catch (e: Exception) {
            log("sfota JSON parse error: ${e.message}")
        }
    }

    /**
     * Handle file transfer command data from the watch.
     * Command channel (100): JSON responses.
     */
    private fun handleFileTransferData(sessionId: Int, data: ByteArray) {
        val text = String(data, Charsets.UTF_8)
        log("FT RX (session=$sessionId): $text")
        try {
            val json = JSONObject(text)
            fileTransfer.handleResponse(json)
        } catch (e: Exception) {
            log("FT: JSON parse error: ${e.message}")
        }
    }

    // --- Host Manager conversation protocol ---

    // HM multi-fragment reassembly buffer
    private val hmFragmentBuffer = java.io.ByteArrayOutputStream()

    /**
     * Handle incoming Host Manager data.
     *
     * HM protocol has a 2-byte header before the JSON payload (sapd hostmanagerconn.cc).
     * For simple messages: [00 00][JSON]
     * For multi-fragment large messages (observed from watch):
     *   [05 00][JSON start...]   — first fragment
     *   [09 00][data cont...]    — middle fragment(s)
     *   [0f 00][data end...]     — last fragment
     * After reassembly, the complete buffer is valid JSON.
     */
    private fun handleHostManagerData(payload: ByteArray) {
        var data = payload
        // Strip fragmentation byte if QoS type=4
        if (hmUsesFragmentation && data.isNotEmpty()) {
            data = data.copyOfRange(1, data.size)
        }
        if (data.size < 2) {
            log("HostMgr RX too short (${data.size}B)")
            return
        }

        // 2-byte HM header (sapd: always strip first 2 bytes)
        val headerByte = data[0].toInt() and 0xFF
        val hmData = data.copyOfRange(2, data.size)

        when (headerByte) {
            0x00 -> {
                // Simple complete message
                processHmMessage(hmData)
            }
            0x05 -> {
                // First fragment of multi-part message
                hmFragmentBuffer.reset()
                hmFragmentBuffer.write(hmData)
                log("HostMgr multi-frag: START (${hmData.size}B)")
            }
            0x09 -> {
                // Middle fragment
                hmFragmentBuffer.write(hmData)
                log("HostMgr multi-frag: CONT (+${hmData.size}B, total=${hmFragmentBuffer.size()})")
            }
            0x0f -> {
                // Last fragment — reassemble and process
                hmFragmentBuffer.write(hmData)
                val complete = hmFragmentBuffer.toByteArray()
                log("HostMgr multi-frag: END (+${hmData.size}B, total=${complete.size}B)")
                hmFragmentBuffer.reset()
                processHmMessage(complete)
            }
            else -> {
                // Unknown header — try stripping 2 bytes anyway (like sapd)
                log("HostMgr unknown header=0x${"%02x".format(headerByte)} (${hmData.size}B)")
                processHmMessage(hmData)
            }
        }
    }

    private fun processHmMessage(jsonBytes: ByteArray) {
        val jsonStr = String(jsonBytes, Charsets.UTF_8)
        // Log first 300 chars only (avoid flooding with base64 data)
        log("HostMgr RX: ${jsonStr.take(300)}${if (jsonStr.length > 300) "...[${jsonStr.length}B total]" else ""}")

        try {
            val json = JSONObject(jsonStr)
            val msgId = json.optString("msgId", "")
            hmResponseReceived?.complete(true)
            handleHostManagerMessage(msgId, json)
        } catch (e: Exception) {
            log("HostMgr JSON parse error: ${e.message}")
            log("HostMgr raw: ${jsonStr.take(200)}")
        }
    }

    /**
     * Host Manager conversation protocol (from sapd hostmanagerconn.cc).
     *
     * Sequence:
     * 1. Phone → mgr_watch_info_req (btMac, hmVer)
     * 2. Watch → mgr_watch_info_res
     * 3. Phone → mgr_wearable_status_req (timestamp, type)
     * 4. Watch → mgr_wearable_status_res (ignored)
     * 5. Watch → mgr_host_status_req
     * 6. Phone → mgr_host_status_res (preinstalled, XML data)
     * 7. Watch → mgr_status_exchange_done
     * 8. Phone → mgr_sync_init_setting_req (time sync)
     * 9. Phone → mgr_setupwizard_eula_finished_req (unlocks watch!)
     */
    private fun handleHostManagerMessage(msgId: String, json: JSONObject) {
        log("HostMgr msgId=$msgId")

        when (msgId) {
            "mgr_watch_info_res" -> {
                // Extract device info
                _deviceInfo.value = DeviceInfo(
                    model = json.optString("model_number", ""),
                    firmware = json.optString("software_version", ""),
                    btMac = json.optString("bt_mac_address", ""),
                    serial = json.optString("serial", ""),
                    modelName = json.optString("model_name", ""),
                    salesCode = json.optString("sales_code", ""),
                )
                // Log all fields for protocol exploration
                val keys = json.keys()
                val fields = mutableListOf<String>()
                while (keys.hasNext()) { val k = keys.next(); fields.add("$k=${json.opt(k)}") }
                log("Watch info: ${fields.joinToString(", ")}")

                // Step 2→3: send wearable status request
                val reply = JSONObject()
                val buildTimestamp = System.currentTimeMillis() / 1000
                val btName = getPhoneBluetoothAddress()
                reply.put("timestamp", "${buildTimestamp}_${btName.takeLast(2)}")
                reply.put("type", "connect")
                reply.put("msgId", "mgr_wearable_status_req")
                sendHostManagerMessage(reply)
            }

            "mgr_wearable_status_res" -> {
                // Step 4: Parse WearableStatus XML for installed apps (including clocks)
                val xmlData = json.optString("data", "")
                if (xmlData.isNotEmpty()) {
                    parseWearableStatusForClocks(xmlData)
                    // Save XML to debug file
                    try {
                        val file = java.io.File(context.filesDir, "wearable_status.xml")
                        file.writeText(xmlData)
                        log("WearableStatus saved to ${file.absolutePath} (${xmlData.length} chars)")
                    } catch (_: Exception) {}
                }
                log("Wearable status acknowledged, waiting for host status request...")
            }

            "mgr_host_status_req" -> {
                // Step 5→6: Watch asks for host status — send XML device info
                log("Host status requested, sending device info...")
                val reply = JSONObject()
                reply.put("type", "connect")
                reply.put("msgId", "mgr_host_status_res")
                reply.put("preinstalled", "true")
                reply.put("data", generateHostXml())
                sendHostManagerMessage(reply)
            }

            "mgr_status_exchange_done" -> {
                // Step 7→8→9: Exchange done — send time sync then EULA finished
                log("Status exchange done! Sending time sync + EULA (isfrominitial=true)...")
                sendFullTimeSync()
                sendEulaFinished()
                log("Setup wizard complete — watch should unlock and register FT consumer!")
                // Connect other services after setup
                connectOtherServices(3)
            }

            "mgr_setupwizard_eula_finished_res" -> {
                log("EULA finished acknowledged! Watch initialization complete.")
            }

            "mgr_setting_voice_control_res" -> {
                log("Voice control settings received (post-init)")
            }

            "mgr_sync_init_setting_res" -> {
                // Log all keys (except huge base64 data) for protocol discovery
                val keys = json.keys()
                val fields = mutableListOf<String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.opt(key)
                    val valueStr = value?.toString() ?: "null"
                    if (valueStr.length > 200) {
                        fields.add("$key=[${valueStr.length} chars]")
                    } else {
                        fields.add("$key=$valueStr")
                    }
                }
                log("Init setting res fields: ${fields.joinToString(", ")}")

                // Extract active idle clock
                val idleClock = json.optString("idle_clock", "")
                if (idleClock.isNotEmpty()) {
                    _activeClock.value = idleClock
                    log("Active idle clock from init: $idleClock")
                    // Mark active in clock list if already populated
                    _clocks.value = _clocks.value.map { it.copy(isActive = it.packageName == idleClock) }
                }
            }

            "mgr_home_bg_res" -> {
                // Watch sends current wallpaper: {"msgId":"mgr_home_bg_res","mode":2,"data":"<base64 JPEG>"}
                val mode = json.optInt("mode", -1)
                val dataLen = json.optString("data", "").length
                log("Watch wallpaper received! mode=$mode, base64Len=$dataLen")
                hmWallpaperResponse?.complete(true)
            }

            "mgr_home_bg_req" -> {
                // Watch echoes back our wallpaper request — confirms it was applied!
                val dataLen = json.optString("data", "").length
                log("Watch echoed mgr_home_bg_req (base64Len=$dataLen) — wallpaper applied!")
                hmWallpaperResponse?.complete(true)
            }

            "mgr_clocks_list_res" -> {
                // Watch returns list of installed clock faces
                log("Clock list received!")
                parseClocksListResponse(json)
            }

            "mgr_clocks_setting_res" -> {
                val pkg = json.optString("clockPkgName", "")
                val type = json.optString("clocktype", "")
                log("Clock setting response: pkg=$pkg, type=$type")
            }

            "mgr_clock_set_idle_req" -> {
                // Watch echoes back — confirms idle clock was set
                val idleClock = json.optString("idle_clock", "")
                log("Idle clock set confirmed: $idleClock")
                if (idleClock.isNotEmpty()) _activeClock.value = idleClock
            }

            "mgr_clock_set_widget_req" -> {
                // Watch notifies phone that clock face changed (user changed on watch)
                val pkgName = json.optString("pkgName", "")
                log("Watch changed clock to: $pkgName")
                if (pkgName.isNotEmpty()) _activeClock.value = pkgName
            }

            "mgr_clock_set_widget_res" -> {
                val pkgName = json.optString("pkgName", "")
                log("Clock set widget response: $pkgName")
                if (pkgName.isNotEmpty()) _activeClock.value = pkgName
            }

            "mgr_clock_preview_capture_res" -> {
                val dataLen = json.optString("data", "").length
                log("Clock preview received (base64Len=$dataLen)")
            }

            else -> {
                log("HostMgr UNKNOWN msgId=$msgId: ${json.toString().take(200)}")
            }
        }
    }

    /**
     * Send a JSON message on Host Manager session.
     *
     * Connected sessions use RAW app data as SAP frame payload (no type=3 wrapping).
     * This matches CAPEX behavior (also sent raw on its connected session).
     * Type=3 wrapping is only for routing on the default session (0x3FF).
     *
     * Wire format (from sapd hostmanagerconn.cc):
     *   HM protocol adds 2-byte null header before JSON: [00 00][JSON]
     *   SAP data frame (QoS type=4): [frag=00][HM data]
     *   Total: [SAP header 2B][frag=00][00 00][JSON]
     */
    private fun sendHostManagerMessage(json: JSONObject) {
        val jsonStr = json.toString()
        log("HostMgr TX (frag=$hmUsesFragmentation): $jsonStr")
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        // sapd hostmanagerconn.cc: _socket->send(QByteArray(2, '\0') + data)
        // HM protocol header: 2 null bytes before JSON
        val hmData = ByteArray(2) + jsonBytes  // [00 00][JSON]
        // SAP data frame: QoS type=4 → [frag=00][hmData]
        val appData = if (hmUsesFragmentation) {
            byteArrayOf(0x00) + hmData  // [frag=00][00 00][JSON]
        } else {
            hmData  // [00 00][JSON]
        }
        val frame = SapProtocol.buildFrame(
            SapProtocol.FRAME_TYPE_DATA, hostManagerSessionId, appData
        )
        sendFrame(frame)
    }

    /**
     * Send mgr_watch_info_req — the FIRST message after Host Manager connects.
     * Initiates the setup conversation.
     */
    private fun sendWatchInfoRequest() {
        val btName = getPhoneBluetoothAddress()
        val msg = JSONObject()
        msg.put("btMac", btName)
        msg.put("msgId", "mgr_watch_info_req")
        msg.put("hmVer", "2.0.14041404")
        sendHostManagerMessage(msg)
        log("Sent mgr_watch_info_req (hmVer=2.0.14041404)")
    }

    /**
     * Send mgr_watch_info_req with Qt-alphabetical key order.
     * Raw on HM session (no type=3 wrapping — connected sessions use raw app data).
     */
    private fun sendWatchInfoRequestQtOrder() {
        val btMac = getPhoneBluetoothAddress()
        val jsonStr = """{"btMac":"$btMac","hmVer":"2.0.14041404","msgId":"mgr_watch_info_req"}"""
        log("HostMgr TX (Qt order, frag=$hmUsesFragmentation): $jsonStr")
        // sapd hostmanagerconn.cc: 2-byte null HM header + JSON
        val hmData = ByteArray(2) + jsonStr.toByteArray(Charsets.UTF_8)
        val appData = if (hmUsesFragmentation) {
            byteArrayOf(0x00) + hmData  // [frag=00][00 00][JSON]
        } else {
            hmData  // [00 00][JSON]
        }
        val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, hostManagerSessionId, appData)
        sendFrame(frame)
    }

    /**
     * Send mgr_watch_info_req with type=3 wrapping on HM session (sapd format).
     * Wire: [SAP header for HM session][03][channelId 2B][00 00][JSON]
     */
    private fun sendWatchInfoRequestType3() {
        val btMac = getPhoneBluetoothAddress()
        val jsonStr = """{"btMac":"$btMac","hmVer":"2.0.14041404","msgId":"mgr_watch_info_req"}"""
        log("HostMgr TX (type=3, channel=$hostManagerChannelId): $jsonStr")
        val hmData = ByteArray(2) + jsonStr.toByteArray(Charsets.UTF_8)
        val dataPayload = SapProtocol.buildDataPayload(hostManagerChannelId, hmData)
        val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, hostManagerSessionId, dataPayload)
        sendFrame(frame)
    }

    /**
     * Send mgr_watch_info_req on the DEFAULT session (0x3FF) with type=3 routing.
     * In some SAP implementations, data is routed via the default session by channelId.
     */
    private fun sendWatchInfoOnDefaultSession() {
        val btMac = getPhoneBluetoothAddress()
        val jsonStr = """{"btMac":"$btMac","hmVer":"2.0.14041404","msgId":"mgr_watch_info_req"}"""
        log("HostMgr TX (DEFAULT session, type=3): $jsonStr")
        val hmData = ByteArray(2) + jsonStr.toByteArray(Charsets.UTF_8)
        val dataPayload = SapProtocol.buildDataPayload(hostManagerChannelId, hmData)
        val frame = SapProtocol.buildFrame(
            SapProtocol.FRAME_TYPE_DATA, SapProtocol.DEFAULT_SESSION_ID, dataPayload
        )
        sendFrame(frame)
    }

    /**
     * Send mgr_watch_info_req WITHOUT type=3 wrapping (raw on session, fallback test).
     */
    private fun sendWatchInfoRequestRawQtOrder() {
        val btMac = getPhoneBluetoothAddress()
        val jsonStr = """{"btMac":"$btMac","hmVer":"2.0.14041404","msgId":"mgr_watch_info_req"}"""
        log("HostMgr TX (raw HM data, no frag): $jsonStr")
        // HM header + JSON, no frag byte (fallback test)
        val hmData = ByteArray(2) + jsonStr.toByteArray(Charsets.UTF_8)
        val frame = SapProtocol.buildFrame(
            SapProtocol.FRAME_TYPE_DATA, hostManagerSessionId, hmData
        )
        sendFrame(frame)
    }

    /**
     * Build 12: Systematic test of ALL possible HM data formats.
     * Send mgr_watch_info_req in every known format variant, with delays between each.
     * If ANY format gets a response, we'll know which one is correct.
     */
    private suspend fun tryAllHmFormats() {
        val btMac = getPhoneBluetoothAddress()
        val sid = hostManagerSessionId
        val cid = hostManagerChannelId
        if (sid == 0) { log("HM session not set"); return }

        val watchInfoJson = """{"btMac":"$btMac","hmVer":"2.0.14041404","msgId":"mgr_watch_info_req"}"""
        val jsonBytes = watchInfoJson.toByteArray(Charsets.UTF_8)
        val eulaJson = """{"btMac":"$btMac","isOld":1,"msgId":"mgr_setupwizard_eula_finished_req"}"""
        val eulaBytes = eulaJson.toByteArray(Charsets.UTF_8)
        val frag = byteArrayOf(0x00)  // FragmentNone

        // sapd hostmanagerconn.cc: QByteArray(2, '\0') + jsonData
        // HM protocol: [00 00] 2B null header before JSON
        val hmData = ByteArray(2) + jsonBytes
        val hmEula = ByteArray(2) + eulaBytes

        // Format A: frag + HM header + JSON (sapd confirmed format)
        log("=== Format A: frag + [00 00] + JSON (sapd format) ===")
        sendRawOnSession(sid, frag + hmData)
        if (checkHmResponse(2000)) return

        // Format B: frag + raw JSON (no HM header)
        log("=== Format B: frag + raw JSON ===")
        sendRawOnSession(sid, frag + jsonBytes)
        if (checkHmResponse(2000)) return

        // Format C: raw HM data (no frag — in case QoS not applied)
        log("=== Format C: [00 00] + JSON (no frag) ===")
        sendRawOnSession(sid, hmData)
        if (checkHmResponse(2000)) return

        // Format D: EULA frag + HM header
        log("=== Format D: EULA frag + [00 00] ===")
        sendRawOnSession(sid, frag + hmEula)
        if (checkHmResponse(2000)) return

        // Format E: EULA frag + raw
        log("=== Format E: EULA frag + raw ===")
        sendRawOnSession(sid, frag + eulaBytes)

        log("=== Tous les formats testés ===")
    }

    /** Check if watch responded within timeout. Returns true if response received. */
    private suspend fun checkHmResponse(timeoutMs: Long): Boolean {
        val resp = withTimeoutOrNull(timeoutMs) { hmResponseReceived?.await() }
        if (resp == true) {
            log("RÉPONSE REÇUE! Format correct trouvé!")
            return true
        }
        return false
    }

    /**
     * Shotgun approach: try sending critical messages in multiple formats.
     * Now includes type=3 Data wrapping (the most likely correct format).
     */
    private fun tryShotgunUnlock() {
        val btMac = getPhoneBluetoothAddress()
        val sid = hostManagerSessionId
        val cid = hostManagerChannelId
        if (sid == 0) { log("HM session not set"); return }

        val eulaJson = """{"btMac":"$btMac","msgId":"mgr_setupwizard_eula_finished_req","isOld":1}"""
        val watchInfoJson = """{"btMac":"$btMac","msgId":"mgr_watch_info_req","hmVer":"2.0.14041404"}"""
        val timeSyncJson = buildTimeSyncJson()

        log("=== Shotgun unlock: essai multi-format sur session=$sid, channel=$cid ===")
        val frag = byteArrayOf(0x00)

        // Format 1: frag + HM header + JSON (sapd confirmed format)
        log("Format 1: frag + [00 00] + JSON (sapd format)")
        sendRawOnSession(sid, frag + ByteArray(2) + watchInfoJson.toByteArray(Charsets.UTF_8))
        sendRawOnSession(sid, frag + ByteArray(2) + eulaJson.toByteArray(Charsets.UTF_8))

        // Format 2: frag + raw JSON (no HM header)
        log("Format 2: frag + raw JSON")
        sendRawOnSession(sid, frag + watchInfoJson.toByteArray(Charsets.UTF_8))
        sendRawOnSession(sid, frag + eulaJson.toByteArray(Charsets.UTF_8))

        // Format 3: frag + HM header + time sync + EULA
        log("Format 3: frag + [00 00] + time sync + EULA")
        sendRawOnSession(sid, frag + ByteArray(2) + timeSyncJson.toByteArray(Charsets.UTF_8))
        sendRawOnSession(sid, frag + ByteArray(2) + eulaJson.toByteArray(Charsets.UTF_8))

        // Format 4: raw HM data (no frag)
        log("Format 4: [00 00] + JSON (no frag)")
        sendRawOnSession(sid, ByteArray(2) + watchInfoJson.toByteArray(Charsets.UTF_8))
        sendRawOnSession(sid, ByteArray(2) + eulaJson.toByteArray(Charsets.UTF_8))

        log("=== Shotgun unlock: 8 messages envoyés, attente réponse... ===")
    }

    /** Send application data on a connected session with type=3 + channelId wrapping */
    private fun sendServiceData(sessionId: Int, channelId: Int, appData: ByteArray) {
        val dataPayload = SapProtocol.buildDataPayload(channelId, appData)
        val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, sessionId, dataPayload)
        sendFrame(frame)
    }

    /** Send raw data on a session (no type=3 wrapping) */
    private fun sendRawOnSession(sessionId: Int, payload: ByteArray) {
        val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, sessionId, payload)
        sendFrame(frame)
    }

    private fun buildTimeSyncJson(): String {
        val msg = JSONObject()
        msg.put("msgId", "mgr_sync_init_setting_req")
        msg.put("isfrominitial", true)  // Critical: tells watch this is initial setup
        msg.put("safety_declared", "0")
        msg.put("safety_voice", "0")
        msg.put("safetyVersion", "0")
        msg.put("safety", "false")
        msg.put("tablet", "false")
        msg.put("incomingCall", "false")
        msg.put("usingCamera", "false")
        msg.put("safety_cam", "0")
        val locale = Locale.getDefault()
        msg.put("locale", "${locale.language}_${locale.country}")
        msg.put("date1224", if (android.text.format.DateFormat.is24HourFormat(context)) "24" else "12")
        msg.put("dateformat", "dd-MM-yyyy")
        msg.put("timezone", TimeZone.getDefault().id)
        msg.put("datetimeepoch", System.currentTimeMillis().toString())
        val sdf = SimpleDateFormat("yyyy MM dd HH mm ss", Locale.US)
        msg.put("datetime", sdf.format(Date()))
        return msg.toString()
    }

    /**
     * Send full time sync (from sapd performTimeSync).
     * Flat JSON with msgId at root level, many settings fields.
     */
    private fun sendFullTimeSync() {
        val msg = JSONObject()
        msg.put("msgId", "mgr_sync_init_setting_req")
        msg.put("isfrominitial", true)  // Critical: tells watch this is initial setup

        // Safety settings (hardcoded from sapd)
        msg.put("safety_declared", "0")
        msg.put("safety_voice", "0")
        msg.put("safetyVersion", "0")
        msg.put("safety", "false")
        msg.put("tablet", "false")
        msg.put("incomingCall", "false")
        msg.put("usingCamera", "false")
        msg.put("safety_cam", "0")

        // Locale and date settings
        val locale = Locale.getDefault()
        msg.put("locale", "${locale.language}_${locale.country}")
        msg.put("date1224", if (android.text.format.DateFormat.is24HourFormat(context)) "24" else "12")
        msg.put("dateformat", "dd-MM-yyyy")

        // Timezone
        val tz = TimeZone.getDefault()
        msg.put("timezone", tz.id)

        // Current time
        val now = Date()
        msg.put("datetimeepoch", System.currentTimeMillis().toString())
        val sdf = SimpleDateFormat("yyyy MM dd HH mm ss", Locale.US)
        msg.put("datetime", sdf.format(now))

        sendHostManagerMessage(msg)
        log("Full time sync sent (isfrominitial=true)")
    }

    /**
     * Send mgr_setupwizard_eula_finished_req — unlocks the watch from setup screen.
     */
    private fun sendEulaFinished() {
        val btName = getPhoneBluetoothAddress()
        val msg = JSONObject()
        msg.put("btMac", btName)
        msg.put("msgId", "mgr_setupwizard_eula_finished_req")
        msg.put("isOld", 1)
        sendHostManagerMessage(msg)
        log("Sent mgr_setupwizard_eula_finished_req")
    }

    /**
     * Generate XML device description (from sapd generateHostXml).
     * Pretend to be a Samsung GT-I9500 with Gear Manager installed.
     */
    private fun generateHostXml(): String {
        val btName = getPhoneBluetoothAddress()
        return """<?xml version="1.0" encoding="UTF-8"?>
<DeviceStatus>
<device>
<deviceID>$btName</deviceID>
<deviceName>none</deviceName>
<devicePlatform>android</devicePlatform>
<devicePlatformVersion>4.4.2</devicePlatformVersion>
<deviceType>Host</deviceType>
<modelNumber>GT-I9500</modelNumber>
<swVersion>android 4.4.2</swVersion>
<connectivity/>
<apps>
<app><name>Gear Manager</name><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>ConnectionManager</name><packagename>com.sec.android.service.connectionmanager</packagename><version>1004</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>goproviders</name><packagename>com.samsung.accessory.goproviders</packagename><version>61</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>SAFileTransferCore</name><packagename>com.samsung.accessory.safiletransfer</packagename><version>1</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>SANotiProvider</name><packagename>com.samsung.accessory.sanotiprovider</packagename><version>1</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>TextTemplateProvider</name><packagename>com.samsung.accessory.texttemplateprovider</packagename><version>1300</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><name>sfota</name><packagename>com.sec.android.fotaprovider</packagename><version>2</version><preloaded>false</preloaded><isAppWidget>false</isAppWidget><features/></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.w-calendar2</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.goproviders</packagename><version>61</version><features><RequiringPackage>com.samsung.wfmd</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.goproviders</packagename><version>61</version><features><RequiringPackage>com.samsung.w-contacts2</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.w-media-controller</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.alarm</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.message</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.w-logs2</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.idle-clock-event</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.idle-clock-dual</RequiringPackage><Installed>true</Installed></features></app>
<app><packagename>com.samsung.accessory.saproviders</packagename><version>64</version><features><RequiringPackage>com.samsung.svoice-w</RequiringPackage><Installed>true</Installed></features></app>
</apps>
<deviceFeature>
<telephony>true</telephony>
<messaging>true</messaging>
<tablet>false</tablet>
<autolock>true</autolock>
<smartrelay>true</smartrelay>
<safetyassistance>false</safetyassistance>
<vendor>Samsung</vendor>
</deviceFeature>
<security/>
<notification/>
<settings/>
</device>
</DeviceStatus>"""
    }

    /**
     * Connect other critical services after Host Manager.
     * The watch is Consumer for these — we (Provider) initiate.
     */
    private fun connectOtherServices(startSessionId: Int) {
        val profiles = listOf(
            "/system/setting",
            "/system/fmp",
            "/system/NotificationService",
            "/system/callhandler",
            "/system/weather",
            "/system/music",
            "/system/alarm",
            "/system/calendar",
        )

        var sessionId = startSessionId
        for (profile in profiles) {
            val watchComponentId = remoteAgentIds[profile]
            if (watchComponentId == null) {
                log("No remote agentId for $profile, skipping")
                continue
            }
            val ourCid = ourComponentId(profile)
            val channelId = sessionId + 100
            log("Connecting $profile (acceptor=$watchComponentId, initiator=$ourCid, session=$sessionId)...")
            val payload = SapProtocol.buildServiceConnectionRequest(
                acceptorId = watchComponentId,
                initiatorId = ourCid,
                profileName = profile,
                sessionId = sessionId,
                channelId = channelId,
            )
            val frame = SapProtocol.wrapInDefaultFrame(payload)
            sendFrame(frame)
            sessionId++
        }
    }

    /**
     * Initiate Service Connection for /system/filetransfer.
     * Creates 2 sessions: command (channel 100) and data (channel 101).
     */
    /**
     * Connect /system/filetransfer.
     *
     * Discovery: sapd routes SC by acceptorId. The watch's filetransfer
     * is handled by the hostmanager agent (confirmed by brute-force scan).
     * Use the hostmanager agentId from CAPEX as acceptorId.
     */
    private suspend fun connectFileTransfer() {
        val ourFtId = ourComponentId(GearConstants.PROFILE_FILE_TRANSFER)

        // Use hostmanager agentId — watch routes filetransfer through it
        val hmAgentId = remoteAgentIds[GearConstants.PROFILE_HOST_MANAGER]
            ?: throw Exception("FT: No hostmanager agentId from CAPEX")
        log("FT: Using hostmanager agentId=$hmAgentId for filetransfer SC")

        // Find free session IDs
        val usedSessions = connectedSessions.keys + setOf(hostManagerSessionId, capexSessionId)
        var nextSession = 20
        while (nextSession in usedSessions) nextSession++

        ftConnReceived = CompletableDeferred()
        ftCommandSessionId = nextSession
        ftCommandChannelId = GearConstants.FT_CHANNEL_COMMAND
        ftDataSessionId = nextSession + 1
        ftDataChannelId = GearConstants.FT_CHANNEL_DATA

        log("FT: Connecting (cmd=$ftCommandSessionId ch=$ftCommandChannelId, data=$ftDataSessionId ch=$ftDataChannelId)...")
        val payload = SapProtocol.buildMultiSessionConnectionRequest(
            acceptorId = hmAgentId,
            initiatorId = ourFtId,
            profileName = GearConstants.PROFILE_FILE_TRANSFER,
            // QoS from Samsung SADefaultServices: ch100=(2,0,5,2) ch101=(0,0,5,0)
            sessions = listOf(
                SapProtocol.SessionSpec(
                    sessionId = ftCommandSessionId,
                    channelId = ftCommandChannelId,
                    qosType = 2,
                    qosDataRate = 0,
                    qosPriority = 5,
                    payloadType = SapProtocol.PAYLOAD_JSON,
                ),
                SapProtocol.SessionSpec(
                    sessionId = ftDataSessionId,
                    channelId = ftDataChannelId,
                    qosType = 0,
                    qosDataRate = 0,
                    qosPriority = 5,
                    payloadType = SapProtocol.PAYLOAD_NONE,
                ),
            ),
        )
        sendFrame(SapProtocol.wrapInDefaultFrame(payload))

        val ok = withTimeoutOrNull(10000) { ftConnReceived?.await() }
        if (ok != true) throw Exception("FT: Service connection timeout/rejected")
        log("FT: Service connected!")
    }

    /**
     * Send wallpaper to the watch via Host Manager mgr_home_bg_req.
     *
     * Discovery: the watch sends its wallpaper as mgr_home_bg_res with base64 JPEG.
     * To SET wallpaper, we send mgr_home_bg_req. Watch echoes it back as confirmation.
     *
     * For large payloads, uses HM multi-fragment protocol:
     *   [05 00][first chunk]  — first fragment
     *   [09 00][chunk]        — middle fragments
     *   [0f 00][last chunk]   — last fragment
     */
    fun sendWallpaper(imageData: ByteArray, fileName: String) {
        scope.launch {
            try {
                fileTransfer.reset()
                _state.value // keep state
                withContext(Dispatchers.IO) {
                    if (hostManagerSessionId == 0) {
                        fileTransfer.reportError("Host Manager not connected")
                        return@withContext
                    }

                    fileTransfer._state.value = GearFileTransfer.TransferState.Sending(0f)

                    // Convert image to base64
                    val base64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
                    log("WP: Sending wallpaper via mgr_home_bg_req (${imageData.size}B raw, ${base64.length}B base64)")

                    // Build the JSON payload
                    val json = """{"msgId":"mgr_home_bg_req","mode":2,"data":"$base64"}"""
                    val jsonBytes = json.toByteArray(Charsets.UTF_8)
                    log("WP: Total JSON payload: ${jsonBytes.size}B")

                    hmWallpaperResponse = CompletableDeferred()

                    // Send via HM — use multi-fragment for large messages
                    val maxChunkSize = 4096  // Conservative chunk size for RFCOMM
                    if (jsonBytes.size <= maxChunkSize) {
                        // Small enough for single message
                        sendHostManagerMessage(JSONObject(json))
                    } else {
                        // Multi-fragment: split into chunks with HM fragment headers
                        val chunks = mutableListOf<Pair<Int, ByteArray>>()
                        var offset = 0
                        while (offset < jsonBytes.size) {
                            val remaining = jsonBytes.size - offset
                            val chunkSize = minOf(remaining, maxChunkSize)
                            val chunk = jsonBytes.copyOfRange(offset, offset + chunkSize)
                            val headerByte = when {
                                offset == 0 -> 0x05  // First fragment
                                offset + chunkSize >= jsonBytes.size -> 0x0f  // Last fragment
                                else -> 0x09  // Middle fragment
                            }
                            chunks.add(headerByte to chunk)
                            offset += chunkSize
                        }
                        log("WP: Sending ${chunks.size} HM fragments...")

                        for ((i, pair) in chunks.withIndex()) {
                            val (headerByte, chunk) = pair
                            // Build: [frag=00][headerByte][00][chunk]
                            val hmPayload = ByteArray(3 + chunk.size)
                            hmPayload[0] = 0x00  // frag byte
                            hmPayload[1] = headerByte.toByte()
                            hmPayload[2] = 0x00  // second header byte
                            System.arraycopy(chunk, 0, hmPayload, 3, chunk.size)

                            val frame = SapProtocol.buildFrame(
                                SapProtocol.FRAME_TYPE_DATA, hostManagerSessionId, hmPayload
                            )
                            sendFrame(frame)

                            val progress = (i + 1).toFloat() / chunks.size
                            fileTransfer._state.value = GearFileTransfer.TransferState.Sending(progress)
                            log("WP: Fragment ${i + 1}/${chunks.size} sent (header=0x${"%02x".format(headerByte)}, ${chunk.size}B)")

                            if (i < chunks.size - 1) {
                                delay(100)  // Small delay between fragments
                            }
                        }
                    }

                    log("WP: All fragments sent, waiting 15s for mgr_home_bg_res...")
                    val ok = withTimeoutOrNull(15000) { hmWallpaperResponse?.await() }
                    if (ok == true) {
                        fileTransfer._state.value = GearFileTransfer.TransferState.Success
                        log("WP: Wallpaper set successfully!")
                    } else {
                        fileTransfer.reportError("No response from watch (timeout)")
                        log("WP: No mgr_home_bg_res received")
                    }
                }
            } catch (e: Exception) {
                log("WP: sendWallpaper error: ${e.message}")
                fileTransfer.reportError(e.message ?: "Unknown error")
            }
        }
    }

    fun syncTime() {
        if (hostManagerSessionId == 0) { log("Host Manager not connected"); return }
        scope.launch(Dispatchers.IO) {
            try {
                sendFullTimeSync()
            } catch (e: Exception) {
                log("Time sync error: ${e.message}")
            }
        }
    }

    // --- Utility ---

    private fun extractReadableText(data: ByteArray): String {
        val sb = StringBuilder()
        for (b in data) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) sb.append(c.toChar())
        }
        return sb.toString()
    }

    fun disconnect() {
        try { wsmClient?.destroy() } catch (_: Exception) {}
        wsmClient = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        try { dataSocket?.close() } catch (_: Exception) {}
        dataSocket = null
        inputStream = null
        outputStream = null
        crcEnabled = false
        hostManagerSessionId = 0
        hostManagerChannelId = 0
        hmUsesFragmentation = false
        remoteAgentIds.clear()
        capexSessionId = 1
        connectedSessions.clear()
        connectedChannels.clear()
        sfotaHandled = null
        capexConnReceived = null
        hmConnReceived = null
        hmResponseReceived = null
        hmWallpaperResponse = null
        ftCommandSessionId = 0
        ftDataSessionId = 0
        ftConnReceived = null
        fileTransfer.reset()
        clockSettingsSessionId = 0
        clockSettingsChannelId = 0
        clockSettingsQosType = 0
        clockSettingsSeqNum = 0
        _clockSettingsConnected.value = false
        _state.value = GearState.Disconnected
        log("Disconnected")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val current = _logs.value.toMutableList()
        current.add(0, message)
        if (current.size > 200) current.removeAt(current.lastIndex)
        _logs.value = current
    }

    // --- Clock/Watchface ---

    /**
     * Request the list of installed clock faces from the watch.
     */
    fun requestClocksList() {
        if (hostManagerSessionId == 0) { log("Host Manager not connected"); return }
        scope.launch(Dispatchers.IO) {
            try {
                val msg = JSONObject()
                msg.put("msgId", "mgr_clocks_list_req")

                // Try on Host Manager first
                log("Requesting clocks list (HM)...")
                sendHostManagerMessage(msg)

                // Also try on /system/setting session (dual_clock_consumer)
                val settingEntry = connectedSessions.entries.find { it.value == "/system/setting" }
                if (settingEntry != null) {
                    val sid = settingEntry.key
                    val cid = connectedChannels[sid] ?: 0
                    log("Also trying clocks list on /system/setting (session=$sid, ch=$cid)...")
                    val jsonBytes = msg.toString().toByteArray(Charsets.UTF_8)
                    sendServiceData(sid, cid, jsonBytes)
                }
            } catch (e: Exception) {
                log("Clocks list error: ${e.message}")
            }
        }
    }

    /**
     * Set the active idle clock face on the watch.
     */
    fun setIdleClock(clockPkgName: String) {
        if (hostManagerSessionId == 0) { log("Host Manager not connected"); return }
        scope.launch(Dispatchers.IO) {
            try {
                log("Setting idle clock: $clockPkgName")

                // All 4 approaches — this combination worked on 2026-03-03 13:03
                val msg1 = JSONObject()
                msg1.put("msgId", "mgr_clock_set_widget_req")
                msg1.put("pkgName", clockPkgName)
                log("Step 1: clock_set_widget_req")
                sendHostManagerMessage(msg1)
                delay(1500)

                val msg2 = JSONObject()
                msg2.put("msgId", "mgr_clock_set_widget_res")
                msg2.put("pkgName", clockPkgName)
                log("Step 2: clock_set_widget_res")
                sendHostManagerMessage(msg2)
                delay(1500)

                val msg3 = JSONObject()
                msg3.put("msgId", "mgr_clock_set_idle_req")
                msg3.put("pkgName", clockPkgName)
                log("Step 3: clock_set_idle_req pkgName")
                sendHostManagerMessage(msg3)
                delay(1500)

                val msg4 = JSONObject()
                msg4.put("msgId", "mgr_sync_init_setting_req")
                msg4.put("isfrominitial", true)
                msg4.put("idle_clock", clockPkgName)
                val locale = Locale.getDefault()
                msg4.put("locale", "${locale.language}_${locale.country}")
                msg4.put("date1224", if (android.text.format.DateFormat.is24HourFormat(context)) "24" else "12")
                msg4.put("dateformat", "dd-MM-yyyy")
                msg4.put("timezone", TimeZone.getDefault().id)
                msg4.put("datetimeepoch", System.currentTimeMillis().toString())
                val sdf = SimpleDateFormat("yyyy MM dd HH mm ss", Locale.US)
                msg4.put("datetime", sdf.format(Date()))
                log("Step 4: init_setting with idle_clock")
                sendHostManagerMessage(msg4)

                _activeClock.value = clockPkgName
            } catch (e: Exception) {
                log("Set idle clock error: ${e.message}")
            }
        }
    }

    /**
     * Request clock settings for a specific clock face.
     */
    fun requestClockSettings(clockPkgName: String) {
        if (hostManagerSessionId == 0) { log("Host Manager not connected"); return }
        scope.launch(Dispatchers.IO) {
            try {
                val msg = JSONObject()
                msg.put("msgId", "mgr_clocks_setting_req")
                msg.put("clockPkgName", clockPkgName)
                log("Requesting settings for clock: $clockPkgName")
                sendHostManagerMessage(msg)
            } catch (e: Exception) {
                log("Clock settings error: ${e.message}")
            }
        }
    }

    /**
     * Probe /system/setting service to discover dual_clock_consumer protocol.
     * Sends multiple message formats and logs any responses.
     */
    private suspend fun probeSettingService(sessionId: Int) {
        log("=== Probing /system/setting (session=$sessionId) ===")

        // Helper to send raw data on the setting session (QoS type=4: [frag=00][data])
        fun sendOnSetting(data: ByteArray) {
            val appData = byteArrayOf(0x00) + data
            val frame = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, sessionId, appData)
            sendFrame(frame)
        }

        // Probe 1: JSON — ask for clock settings (like other services use)
        val activePkg = _activeClock.value.ifEmpty { "com.samsung.idle-clock-digital" }
        val probe1 = """{"msgId":"settings-req","pkgName":"$activePkg"}"""
        log("Probe 1 (settings-req): $probe1")
        sendOnSetting(probe1.toByteArray(Charsets.UTF_8))
        delay(1500)

        // Probe 2: Simple string command (like b2contacts uses "Initial_Sync_Logs")
        val probe2 = "get_settings"
        log("Probe 2 (string): $probe2")
        sendOnSetting(probe2.toByteArray(Charsets.UTF_8))
        delay(1500)

        // Probe 3: XML format settings request
        val probe3 = """<?xml version="1.0" encoding="UTF-8"?><settings><get pkgName="$activePkg"/></settings>"""
        log("Probe 3 (XML): $probe3")
        sendOnSetting(probe3.toByteArray(Charsets.UTF_8))
        delay(1500)

        // Probe 4: Try the updatedsettings format with a dummy checkbox
        val probe4 = """<?xml version="1.0" encoding="utf-8"?><Application type="application" version="001"><packageName>$activePkg</packageName><Settings><Item id="showdate" setting_type="checkbox"><CheckBox id="showdate" checked="yes"><CheckBoxItem>On</CheckBoxItem><CheckBoxItem>Off</CheckBoxItem></CheckBox></Item></Settings></Application>"""
        log("Probe 4 (updatedsettings XML): ${probe4.take(120)}...")
        sendOnSetting(probe4.toByteArray(Charsets.UTF_8))
        delay(1500)

        // Probe 5: JSON with clockPkgName (matching HM format)
        val probe5 = """{"msgId":"mgr_clocks_setting_req","clockPkgName":"$activePkg"}"""
        log("Probe 5 (mgr_clocks_setting_req): $probe5")
        sendOnSetting(probe5.toByteArray(Charsets.UTF_8))
        delay(1500)

        // Probe 6: Try without frag byte (raw JSON directly)
        val probe6 = """{"msgId":"settings-req","pkgName":"$activePkg"}"""
        log("Probe 6 (no frag, raw JSON): $probe6")
        val frame6 = SapProtocol.buildFrame(SapProtocol.FRAME_TYPE_DATA, sessionId, probe6.toByteArray(Charsets.UTF_8))
        sendFrame(frame6)
        delay(1500)

        log("=== Setting probes done — check for responses above ===")
    }

    /**
     * Request a preview capture of a specific clock face.
     */
    fun requestClockPreview(clockPkgName: String) {
        if (hostManagerSessionId == 0) { log("Host Manager not connected"); return }
        scope.launch(Dispatchers.IO) {
            try {
                val msg = JSONObject()
                msg.put("msgId", "mgr_clock_preview_capture_req")
                msg.put("clockPkgName", clockPkgName)
                log("Requesting preview for clock: $clockPkgName")
                sendHostManagerMessage(msg)
            } catch (e: Exception) {
                log("Clock preview error: ${e.message}")
            }
        }
    }

    /**
     * Parse the mgr_clocks_list_res response.
     * Expected format varies — could be XML clocklist or JSON array.
     * We try both and log everything for protocol exploration.
     */
    private fun parseClocksListResponse(json: JSONObject) {
        val clockList = mutableListOf<ClockInfo>()

        // Try "clocklist" field (XML or string list)
        val clocklistStr = json.optString("clocklist", "")
        if (clocklistStr.isNotEmpty()) {
            log("clocklist field (${clocklistStr.length} chars): ${clocklistStr.take(500)}")
        }

        // Try JSON array fields
        val clocklistname = json.optJSONArray("clocklistname")
        if (clocklistname != null) {
            for (i in 0 until clocklistname.length()) {
                val name = clocklistname.optString(i, "")
                log("  clock[$i]: $name")
                clockList.add(ClockInfo(packageName = name, name = name))
            }
        }

        // Try iterating all keys to discover format
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "msgId") {
                val value = json.opt(key)
                val valueStr = value?.toString() ?: "null"
                log("  clocks_list key=$key: ${valueStr.take(300)}")
            }
        }

        // If we got a clocklist XML, try to parse package names from it
        if (clocklistStr.contains("<") && clocklistStr.contains("package")) {
            // Simple regex extraction of package names from XML
            val pkgRegex = Regex("""<package[^>]*>([^<]+)</package>""")
            pkgRegex.findAll(clocklistStr).forEach { match ->
                val pkg = match.groupValues[1]
                clockList.add(ClockInfo(packageName = pkg, name = pkg.substringAfterLast(".")))
            }
            // Also try packagename tag
            val pkgNameRegex = Regex("""<packagename[^>]*>([^<]+)</packagename>""")
            pkgNameRegex.findAll(clocklistStr).forEach { match ->
                val pkg = match.groupValues[1]
                if (clockList.none { it.packageName == pkg }) {
                    clockList.add(ClockInfo(packageName = pkg, name = pkg.substringAfterLast(".")))
                }
            }
        }

        // Check idle_clock field
        val idleClock = json.optString("idle_clock", "")
        if (idleClock.isNotEmpty()) {
            _activeClock.value = idleClock
            log("  Active idle clock: $idleClock")
        }

        if (clockList.isNotEmpty()) {
            _clocks.value = clockList.map { it.copy(isActive = it.packageName == _activeClock.value) }
            log("Parsed ${clockList.size} clock faces")
        } else {
            log("No clocks parsed — check raw response above for format discovery")
        }
    }

    /**
     * Parse WearableStatus XML to discover installed clock packages.
     * The XML contains <app> entries with <packagename> and <isclock> tags.
     * Also look for idle-clock patterns in package names.
     */
    private fun parseWearableStatusForClocks(xml: String) {
        val clockList = mutableListOf<ClockInfo>()

        // Extract all <packagename> entries
        val pkgRegex = Regex("""<packagename>([^<]+)</packagename>""")
        val allPackages = pkgRegex.findAll(xml).map { it.groupValues[1] }.toList().distinct()
        log("WearableStatus: ${allPackages.size} unique packages found")

        // Find clock-related packages (idle-clock, w-idle-clock, clock in name)
        val clockPackages = allPackages.filter {
            it.contains("idle-clock", ignoreCase = true) ||
            it.contains("idle_clock", ignoreCase = true) ||
            it.contains("w-clock", ignoreCase = true)
        }

        for (pkg in clockPackages) {
            val name = pkg.substringAfterLast(".").replace("-", " ")
            clockList.add(ClockInfo(packageName = pkg, name = name))
            log("  Clock package: $pkg")
        }

        // Also parse <app> blocks that have <isclock>true</isclock>
        val isClockRegex = Regex("""<app>.*?<packagename>([^<]+)</packagename>.*?<isclock>true</isclock>.*?</app>""", RegexOption.DOT_MATCHES_ALL)
        isClockRegex.findAll(xml).forEach { match ->
            val pkg = match.groupValues[1]
            if (clockList.none { it.packageName == pkg }) {
                clockList.add(ClockInfo(packageName = pkg, name = pkg.substringAfterLast(".")))
                log("  Clock (isclock=true): $pkg")
            }
        }

        // Add custom clock faces (installed via SDB, not in WearableStatus)
        // Note: watch uses package name (not app id) in mgr_clock_set_widget_req
        val customClocks = listOf(
            ClockInfo(packageName = "organizeur", name = "Organizeur"),
        )
        for (clock in customClocks) {
            if (clockList.none { it.packageName == clock.packageName }) {
                clockList.add(clock)
                log("  Custom clock: ${clock.packageName}")
            }
        }

        if (clockList.isNotEmpty()) {
            val active = _activeClock.value
            _clocks.value = clockList.map { it.copy(isActive = it.packageName == active) }
            log("Found ${clockList.size} clock packages (${customClocks.size} custom)")
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * Get list of connected service profiles (for UI selector).
     */
    fun getConnectedServices(): List<String> {
        val services = mutableListOf<String>()
        if (hostManagerSessionId != 0) services.add("/system/hostmanager")
        services.addAll(connectedSessions.values.distinct().filter { it != GearConstants.PROFILE_FILE_TRANSFER })
        return services
    }

    /**
     * Send a raw JSON message on a connected service session.
     * Used for protocol exploration — send test messages and observe watch response.
     */
    fun sendTestMessage(profile: String, jsonText: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(jsonText) // validate JSON
                if (profile == "/system/hostmanager") {
                    if (hostManagerSessionId == 0) { log("Host Manager not connected"); return@launch }
                    log("TEST TX [HM]: $jsonText")
                    sendHostManagerMessage(json)
                } else {
                    // Find session for this profile
                    val entry = connectedSessions.entries.find { it.value == profile }
                    if (entry == null) { log("No session for $profile"); return@launch }
                    val sessionId = entry.key
                    log("TEST TX [$profile] (session=$sessionId): $jsonText")
                    // Connected sessions use raw data (no type=3 wrapping)
                    // QoS type=4 requires frag byte prefix: [frag=00][data]
                    val appData = byteArrayOf(0x00) + jsonText.toByteArray(Charsets.UTF_8)
                    val frame = SapProtocol.buildFrame(
                        SapProtocol.FRAME_TYPE_DATA, sessionId, appData
                    )
                    sendFrame(frame)
                }
            } catch (e: Exception) {
                log("TEST error: ${e.message}")
            }
        }
    }

    // --- Clock Settings SAP ---

    /**
     * Send clock face settings to the watch via the /organizeur/clocksettings SAP channel.
     * The clock face (SAP consumer) receives this JSON, saves to localStorage, and applies.
     */
    fun sendClockSettings(settings: JSONObject) {
        scope.launch(Dispatchers.IO) {
            if (clockSettingsSessionId == 0) {
                log("Clock settings: pas de session SAP (cadran Organizeur actif ?)")
                return@launch
            }
            try {
                val bgImage = settings.optString("bgImage", "")

                // Send text settings without bgImage
                val textSettings = JSONObject(settings.toString())
                textSettings.remove("bgImage")
                log("Clock settings TX text: $textSettings")
                sendClockSettingsFrame(textSettings)

                // Send bgImage in chunks (SAP can't handle large messages)
                if (bgImage.isNotEmpty()) {
                    val chunkSize = 2000
                    val totalChunks = (bgImage.length + chunkSize - 1) / chunkSize
                    log("Clock settings TX bgImage: ${bgImage.length} chars in $totalChunks chunks")
                    for (i in 0 until totalChunks) {
                        val start = i * chunkSize
                        val end = minOf(start + chunkSize, bgImage.length)
                        val chunk = bgImage.substring(start, end)
                        val chunkMsg = JSONObject()
                        chunkMsg.put("_bgC", i)
                        chunkMsg.put("_bgT", totalChunks)
                        chunkMsg.put("_bgD", chunk)
                        kotlinx.coroutines.delay(150)
                        sendClockSettingsFrame(chunkMsg)
                    }
                    log("Clock settings TX bgImage done")
                } else {
                    // Clear bgImage on watch
                    val clearMsg = JSONObject()
                    clearMsg.put("bgImage", "")
                    sendClockSettingsFrame(clearMsg)
                }
            } catch (e: Exception) {
                log("Clock settings error: ${e.message}")
            }
        }
    }

    private fun sendClockSettingsFrame(json: JSONObject) {
        val jsonStr = json.toString()
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)
        val appData = when (clockSettingsQosType) {
            5 -> {
                // ReliabilityEnable: [seqNum 2B BE][frag=0x00][data]
                val seq = clockSettingsSeqNum++
                byteArrayOf(
                    (seq shr 8).toByte(),
                    (seq and 0xFF).toByte(),
                    0x00
                ) + jsonBytes
            }
            4 -> {
                // ReliabilityDisable: [frag=0x00][data]
                byteArrayOf(0x00) + jsonBytes
            }
            else -> jsonBytes
        }
        val frame = SapProtocol.buildFrame(
            SapProtocol.FRAME_TYPE_DATA, clockSettingsSessionId, appData
        )
        sendFrame(frame)
    }
}

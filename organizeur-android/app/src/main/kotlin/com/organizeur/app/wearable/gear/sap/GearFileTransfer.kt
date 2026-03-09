package com.organizeur.app.wearable.gear.sap

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import kotlin.math.ceil

/**
 * SAP File Transfer protocol for Samsung Gear 1.
 *
 * Uses 2 SAP channels on /system/filetransfer:
 * - Channel 100 (command): JSON messages (setup-req/rsp, complete-req/rsp, cancel)
 * - Channel 101 (data): binary chunks [0x01][raw bytes], max 32767B, 180ms delay
 *
 * Sequence:
 * 1. Phone -> filetransfer-setup-req (fileName, fileSize, direction=1)
 * 2. Watch -> filetransfer-setup-rsp (result=0 OK)
 * 3. Phone -> data chunks on channel 101
 * 4. Phone -> filetransfer-complete-req
 * 5. Watch -> filetransfer-complete-rsp
 */
class GearFileTransfer(
    private val sendOnSession: (sessionId: Int, channelId: Int, appData: ByteArray) -> Unit,
    private val log: (String) -> Unit,
) {
    companion object {
        private const val TAG = "GearFileTransfer"
        const val CHUNK_SIZE = 32767
        const val CHUNK_DELAY_MS = 180L
        const val CHUNK_MSG_ID: Byte = 0x01
    }

    sealed class TransferState {
        data object Idle : TransferState()
        data class Sending(val progress: Float) : TransferState()
        data object Success : TransferState()
        data class Error(val message: String) : TransferState()
    }

    internal val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    // Deferred for setup-rsp and complete-rsp (exposed for GearManager multi-format probing)
    var setupResult: kotlinx.coroutines.CompletableDeferred<Boolean>? = null
    private var completeResult: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    /**
     * Send a file over SAP file transfer protocol.
     *
     * @param fileName destination file name on watch
     * @param data file bytes to send
     * @param commandSessionId SAP session ID for command channel
     * @param commandChannelId channel ID for commands (100)
     * @param dataSessionId SAP session ID for data channel
     * @param dataChannelId channel ID for data (101)
     */
    suspend fun sendFile(
        fileName: String,
        data: ByteArray,
        commandSessionId: Int,
        commandChannelId: Int,
        dataSessionId: Int,
        dataChannelId: Int,
    ) {
        try {
            _state.value = TransferState.Sending(0f)
            log("FT: Sending $fileName (${data.size} bytes)")

            // Step 1: Send setup request (format from Samsung decompiled SetupRequest.toJson())
            setupResult = kotlinx.coroutines.CompletableDeferred()
            val transId = (System.currentTimeMillis() % 100000).toInt()
            val setupReq = JSONObject().apply {
                put("msgId", "filetransfer-setup-req")
                put("transId", transId)
                put("fileName", fileName)
                put("fileSize", data.size)
                put("fileLastModified", System.currentTimeMillis())
                put("fileAuthor", "")
                put("filePath", "")
                put("peerId", "")
                put("containerId", "")
                put("accId", 0)
            }
            sendCommand(commandSessionId, commandChannelId, setupReq)
            log("FT: setup-req sent, waiting for setup-rsp...")

            // Wait for setup-rsp
            val setupOk = kotlinx.coroutines.withTimeoutOrNull(10000) { setupResult?.await() }
            if (setupOk != true) {
                _state.value = TransferState.Error("Setup timeout")
                log("FT: setup-rsp timeout")
                return
            }
            log("FT: setup-rsp OK, sending chunks...")

            // Step 2: Send data chunks on data channel
            val totalChunks = ceil(data.size.toDouble() / CHUNK_SIZE).toInt().coerceAtLeast(1)
            var offset = 0
            var chunkIndex = 0

            while (offset < data.size) {
                val remaining = data.size - offset
                val chunkSize = minOf(remaining, CHUNK_SIZE)
                val chunk = ByteArray(1 + chunkSize)
                chunk[0] = CHUNK_MSG_ID
                System.arraycopy(data, offset, chunk, 1, chunkSize)

                sendDataChunk(dataSessionId, dataChannelId, chunk)
                offset += chunkSize
                chunkIndex++

                val progress = offset.toFloat() / data.size
                _state.value = TransferState.Sending(progress)
                Log.d(TAG, "FT: chunk $chunkIndex/$totalChunks (${(progress * 100).toInt()}%)")

                if (offset < data.size) {
                    delay(CHUNK_DELAY_MS)
                }
            }
            log("FT: All $totalChunks chunks sent")

            // Step 3: Send complete request
            completeResult = kotlinx.coroutines.CompletableDeferred()
            val completeReq = JSONObject().apply {
                put("msgId", "filetransfer-complete-req")
                put("fileName", fileName)
            }
            sendCommand(commandSessionId, commandChannelId, completeReq)
            log("FT: complete-req sent, waiting for complete-rsp...")

            // Wait for complete-rsp
            val completeOk = kotlinx.coroutines.withTimeoutOrNull(10000) { completeResult?.await() }
            if (completeOk != true) {
                _state.value = TransferState.Error("Complete timeout")
                log("FT: complete-rsp timeout")
                return
            }

            _state.value = TransferState.Success
            log("FT: Transfer complete!")

        } catch (e: Exception) {
            Log.e(TAG, "FT: Transfer error", e)
            _state.value = TransferState.Error(e.message ?: "Unknown error")
            log("FT: Error: ${e.message}")
        }
    }

    /**
     * Handle a response message from the watch on the command channel.
     */
    fun handleResponse(json: JSONObject) {
        val msgId = json.optString("msgId", "")
        log("FT: RX msgId=$msgId")

        when (msgId) {
            "filetransfer-setup-rsp" -> {
                val result = json.optInt("result", -1)
                log("FT: setup-rsp result=$result")
                if (result == 0) {
                    setupResult?.complete(true)
                } else {
                    _state.value = TransferState.Error("Setup rejected: result=$result")
                    setupResult?.complete(false)
                }
            }
            "filetransfer-complete-rsp" -> {
                val result = json.optInt("result", -1)
                log("FT: complete-rsp result=$result")
                completeResult?.complete(result == 0)
                if (result != 0) {
                    _state.value = TransferState.Error("Complete failed: result=$result")
                }
            }
            "filetransfer-cancel-rsp", "filetransfer-cancel-req" -> {
                log("FT: Transfer cancelled by watch")
                _state.value = TransferState.Error("Cancelled by watch")
                setupResult?.complete(false)
                completeResult?.complete(false)
            }
            else -> {
                log("FT: Unknown msgId=$msgId: $json")
            }
        }
    }

    /**
     * Send file data chunks + complete-req (setup-req already handled externally).
     * Called by GearManager after setup-rsp is received.
     */
    suspend fun sendFileData(
        data: ByteArray,
        fileName: String,
        commandSessionId: Int,
        commandChannelId: Int,
        dataSessionId: Int,
        dataChannelId: Int,
    ) {
        try {
            _state.value = TransferState.Sending(0f)
            log("FT: Sending ${data.size} bytes in chunks...")

            val totalChunks = ceil(data.size.toDouble() / CHUNK_SIZE).toInt().coerceAtLeast(1)
            var offset = 0
            var chunkIndex = 0

            while (offset < data.size) {
                val remaining = data.size - offset
                val chunkSize = minOf(remaining, CHUNK_SIZE)
                val chunk = ByteArray(1 + chunkSize)
                chunk[0] = CHUNK_MSG_ID
                System.arraycopy(data, offset, chunk, 1, chunkSize)

                sendDataChunk(dataSessionId, dataChannelId, chunk)
                offset += chunkSize
                chunkIndex++

                val progress = offset.toFloat() / data.size
                _state.value = TransferState.Sending(progress)
                Log.d(TAG, "FT: chunk $chunkIndex/$totalChunks (${(progress * 100).toInt()}%)")

                if (offset < data.size) {
                    delay(CHUNK_DELAY_MS)
                }
            }
            log("FT: All $totalChunks chunks sent")

            // Send complete request
            completeResult = kotlinx.coroutines.CompletableDeferred()
            val completeReq = JSONObject().apply {
                put("msgId", "filetransfer-complete-req")
                put("fileName", fileName)
            }
            sendCommand(commandSessionId, commandChannelId, completeReq)
            log("FT: complete-req sent, waiting for complete-rsp...")

            val completeOk = kotlinx.coroutines.withTimeoutOrNull(10000) { completeResult?.await() }
            if (completeOk != true) {
                _state.value = TransferState.Error("Complete timeout")
                log("FT: complete-rsp timeout")
                return
            }

            _state.value = TransferState.Success
            log("FT: Transfer complete!")

        } catch (e: Exception) {
            Log.e(TAG, "FT: sendFileData error", e)
            _state.value = TransferState.Error(e.message ?: "Unknown error")
            log("FT: Error: ${e.message}")
        }
    }

    fun reportError(message: String) {
        _state.value = TransferState.Error(message)
        log("FT: $message")
    }

    fun reset() {
        _state.value = TransferState.Idle
        setupResult = null
        completeResult = null
    }

    private fun sendCommand(sessionId: Int, channelId: Int, json: JSONObject) {
        val jsonBytes = json.toString().toByteArray(Charsets.UTF_8)
        // Raw JSON — no frag byte (QoS type=0 for our SC = no fragmentation)
        sendOnSession(sessionId, channelId, jsonBytes)
    }

    private fun sendDataChunk(sessionId: Int, channelId: Int, chunk: ByteArray) {
        sendOnSession(sessionId, channelId, chunk)
    }
}

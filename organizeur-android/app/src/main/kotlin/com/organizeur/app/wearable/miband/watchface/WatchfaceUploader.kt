package com.organizeur.app.wearable.miband.watchface

import android.util.Log
import com.organizeur.app.wearable.miband.MiBand4Constants
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ConnectionPriority
import com.welie.blessed.WriteType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.zip.CRC32

object WatchfaceUploader {

    private const val TAG = "WatchfaceUploader"
    private const val DEFAULT_CHUNK_SIZE = 20
    private const val SYNC_INTERVAL = 100

    sealed class UploadState {
        data object Idle : UploadState()
        data class Preparing(val message: String) : UploadState()
        data class Uploading(val progress: Float, val bytesSent: Int, val totalBytes: Int) : UploadState()
        data object Verifying : UploadState()
        data object Success : UploadState()
        data class Error(val message: String) : UploadState()
    }

    private fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    suspend fun upload(
        peripheral: BluetoothPeripheral,
        watchfaceData: ByteArray,
        onProgress: (UploadState) -> Unit,
    ) {
        try {
            onProgress(UploadState.Preparing("Initialisation DFU..."))
            Log.d(TAG, "Starting watchface upload: ${watchfaceData.size} bytes")

            val negotiatedMtu = try {
                peripheral.requestMtu(512)
            } catch (e: Exception) { 23 }
            val chunkSize = maxOf(DEFAULT_CHUNK_SIZE, negotiatedMtu - 3)
            Log.d(TAG, "MTU: $negotiatedMtu, chunk size: $chunkSize")

            try {
                peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
            } catch (_: Exception) {}
            delay(500)

            val dfuControl = peripheral.getCharacteristic(
                MiBand4Constants.SERVICE_DFU, MiBand4Constants.CHAR_DFU_CONTROL,
            )
            val dfuData = peripheral.getCharacteristic(
                MiBand4Constants.SERVICE_DFU, MiBand4Constants.CHAR_DFU_DATA,
            )
            if (dfuControl == null || dfuData == null) {
                onProgress(UploadState.Error("DFU characteristics non trouvees"))
                return
            }

            var lastNotification = CompletableDeferred<ByteArray>()
            peripheral.observe(dfuControl) { value ->
                val hex = value.joinToString("") { "%02x".format(it) }
                Log.d(TAG, "DFU notify: $hex")
                lastNotification.complete(value)
            }
            delay(500)

            suspend fun controlWrite(
                cmd: ByteArray,
                description: String,
                timeoutMs: Long = 15000,
            ): ByteArray? {
                lastNotification = CompletableDeferred()
                Log.d(TAG, ">> $description [${cmd.joinToString("") { "%02x".format(it) }}]")
                peripheral.writeCharacteristic(dfuControl, cmd, WriteType.WITH_RESPONSE)
                val resp = withTimeoutOrNull(timeoutMs) { lastNotification.await() }
                Log.d(TAG, "<< $description: ${resp?.joinToString("") { "%02x".format(it) }}")
                return resp
            }

            onProgress(UploadState.Preparing("Envoi firmware info..."))
            val size = watchfaceData.size
            val crc = crc32(watchfaceData)
            Log.d(TAG, "File size=$size, CRC32=0x${"%08x".format(crc)}")
            val fwInfoCmd = byteArrayOf(
                0x01, 0x08,
                (size and 0xFF).toByte(),
                ((size shr 8) and 0xFF).toByte(),
                ((size shr 16) and 0xFF).toByte(),
                ((size shr 24) and 0xFF).toByte(),
                (crc.toInt() and 0xFF).toByte(),
                ((crc.toInt() shr 8) and 0xFF).toByte(),
                ((crc.toInt() shr 16) and 0xFF).toByte(),
                ((crc.toInt() shr 24) and 0xFF).toByte(),
            )
            val resp1 = controlWrite(fwInfoCmd, "Step1 FwInfo")
            if (resp1 == null || (resp1.size >= 3 && resp1[2] != 0x01.toByte())) {
                val status = resp1?.getOrNull(2)?.toInt()?.and(0xFF)
                onProgress(UploadState.Error("FwInfo echoue (status=$status)"))
                return
            }

            onProgress(UploadState.Preparing("Demarrage transfert..."))
            lastNotification = CompletableDeferred()
            val startCmd = byteArrayOf(0x03, 0x01)
            Log.d(TAG, ">> Step2 StartTransfer [${startCmd.joinToString("") { "%02x".format(it) }}]")
            peripheral.writeCharacteristic(dfuControl, startCmd, WriteType.WITH_RESPONSE)
            delay(500)

            onProgress(UploadState.Uploading(0f, 0, size))
            val totalChunks = (size + chunkSize - 1) / chunkSize
            Log.d(TAG, "Sending $totalChunks chunks (sync every $SYNC_INTERVAL)")
            var offset = 0
            var chunkNum = 0

            while (offset < watchfaceData.size) {
                val end = minOf(offset + chunkSize, watchfaceData.size)
                val chunk = watchfaceData.copyOfRange(offset, end)
                peripheral.writeCharacteristic(dfuData, chunk, WriteType.WITHOUT_RESPONSE)
                offset = end
                chunkNum++

                if (chunkNum % SYNC_INTERVAL == 0) {
                    Log.d(TAG, "Sync at chunk $chunkNum/$totalChunks")
                    peripheral.writeCharacteristic(
                        dfuControl, byteArrayOf(0x00), WriteType.WITH_RESPONSE,
                    )
                }

                if (chunkNum % 50 == 0) {
                    Log.d(TAG, "Chunk $chunkNum/$totalChunks")
                }
                onProgress(UploadState.Uploading(offset.toFloat() / size, offset, size))
            }
            Log.d(TAG, "All chunks sent: $offset bytes")

            Log.d(TAG, "Final sync [00]")
            peripheral.writeCharacteristic(
                dfuControl, byteArrayOf(0x00), WriteType.WITH_RESPONSE,
            )

            Log.d(TAG, "Waiting for transfer complete notification...")
            val respTransfer = withTimeoutOrNull(30000) { lastNotification.await() }
            Log.d(TAG, "Transfer notification: ${respTransfer?.joinToString("") { "%02x".format(it) }}")
            if (respTransfer == null) {
                onProgress(UploadState.Error("Timeout notification transfert"))
                return
            }

            onProgress(UploadState.Verifying)
            delay(1000)
            val respChecksum = controlWrite(byteArrayOf(0x04), "Step4 Checksum")
            Log.d(TAG, "Checksum status=${respChecksum?.getOrNull(2)?.toInt()?.and(0xFF)}")
            if (respChecksum == null) {
                onProgress(UploadState.Error("Checksum echoue"))
                return
            }
            if (respChecksum.size >= 3 && respChecksum[2] != 0x01.toByte()) {
                val status = respChecksum[2].toInt() and 0xFF
                Log.e(TAG, "Checksum rejected: status=$status")
                onProgress(UploadState.Error("Checksum rejete (status=$status)"))
                return
            }

            Log.d(TAG, "Watchface upload complete!")
            onProgress(UploadState.Success)

        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}", e)
            onProgress(UploadState.Error("Erreur: ${e.message}"))
        }
    }
}

package com.organizeur.app.wearable.gear.sap

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SAP frame encoding/decoding over RFCOMM.
 *
 * Frame format:
 * - CRC disabled: [2B payloadLength][payload]
 * - CRC enabled:  [2B payloadLength][2B lengthCRC][payload][2B payloadCRC]
 *
 * CRC is CRC-16/ARC (init=0, poly=0x8005 reflected as 0xA001).
 * From decompiled SAFrameworkUtils.computeCrc() — NOT CRC-16/CCITT.
 * CRC is disabled during initial handshake, enabled after WSM auth.
 */
object SapFraming {

    /**
     * CRC-16/ARC calculation (init=0, poly=0xA001 reflected).
     * Matches Samsung's SAFrameworkUtils.computeCrc() with CHECKSUM_TABLE.
     */
    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc
    }

    /**
     * Wrap a payload into a SAP frame.
     *
     * Samsung frame format (from decompiled SABtRfConnection + SAConnection):
     * - CRC disabled: [2B payloadLength][payload]
     * - CRC enabled:  [2B payloadLength][2B lengthCRC][payload][2B payloadCRC]
     *
     * The length field is ALWAYS the payload size (not including CRC bytes).
     * When CRC is disabled, the CRC fields are completely absent (not zeroed).
     */
    fun wrapFrame(payload: ByteArray, crcEnabled: Boolean = false): ByteArray {
        val payloadLength = payload.size

        if (!crcEnabled) {
            // No CRC: just length + payload
            val buf = ByteBuffer.allocate(2 + payloadLength).order(ByteOrder.BIG_ENDIAN)
            buf.putShort(payloadLength.toShort())
            buf.put(payload)
            return buf.array()
        }

        // CRC enabled: length + lengthCRC + payload + payloadCRC
        val buf = ByteBuffer.allocate(2 + 2 + payloadLength + 2).order(ByteOrder.BIG_ENDIAN)

        val lengthBytes = byteArrayOf(
            ((payloadLength shr 8) and 0xFF).toByte(),
            (payloadLength and 0xFF).toByte(),
        )
        buf.putShort(payloadLength.toShort())
        buf.putShort(crc16(lengthBytes).toShort())
        buf.put(payload)
        buf.putShort(crc16(payload).toShort())

        return buf.array()
    }

    /**
     * Read a complete frame from an input stream.
     * Blocks until a full frame is read.
     */
    fun readFrame(input: InputStream, crcEnabled: Boolean = false): ByteArray? {
        // Read 2-byte payload length (big-endian)
        val lengthBytes = readExact(input, 2) ?: return null
        val payloadLength = ((lengthBytes[0].toInt() and 0xFF) shl 8) or
                (lengthBytes[1].toInt() and 0xFF)

        if (payloadLength <= 0 || payloadLength > 65535) return null

        if (crcEnabled) {
            // Read length CRC (2 bytes)
            val headerCrcBytes = readExact(input, 2) ?: return null
            val expectedHeaderCrc = crc16(lengthBytes)
            val actualHeaderCrc = ((headerCrcBytes[0].toInt() and 0xFF) shl 8) or
                    (headerCrcBytes[1].toInt() and 0xFF)
            if (expectedHeaderCrc != actualHeaderCrc) {
                return null  // Header CRC mismatch
            }
        }

        // Read payload
        val payload = readExact(input, payloadLength) ?: return null

        if (crcEnabled) {
            // Read payload CRC (2 bytes)
            val trailerCrcBytes = readExact(input, 2) ?: return null
            val expectedTrailerCrc = crc16(payload)
            val actualTrailerCrc = ((trailerCrcBytes[0].toInt() and 0xFF) shl 8) or
                    (trailerCrcBytes[1].toInt() and 0xFF)
            if (expectedTrailerCrc != actualTrailerCrc) {
                return null  // Trailer CRC mismatch
            }
        }

        return payload
    }

    /**
     * Write a frame to an output stream.
     */
    fun writeFrame(output: OutputStream, payload: ByteArray, crcEnabled: Boolean = false) {
        output.write(wrapFrame(payload, crcEnabled))
        output.flush()
    }

    /**
     * Read exactly n bytes from an input stream.
     */
    private fun readExact(input: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read < 0) return null
            offset += read
        }
        return buf
    }
}

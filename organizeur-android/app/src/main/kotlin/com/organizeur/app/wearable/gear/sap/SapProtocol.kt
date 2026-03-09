package com.organizeur.app.wearable.gear.sap

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * SAP (Samsung Accessory Protocol) message encoding/decoding.
 *
 * SAP header (2 bytes) — from sapd saprotocol.cc:
 * - bits 15-13: protocolVersion (3 bits) — always 0 for Gear 1
 * - bit 12: frameType (1 bit) — 0=Data, 1=Control
 * - bits 11-2: sessionId (10 bits)
 * - bits 1-0: unused (0)
 *
 * Message types (inside SAP Data frame payload):
 * 1 = Service Connection request
 * 2 = Service Connection response
 * 3 = Data
 * 4 = Disconnect/Close
 *
 * Pre-auth message types (raw frame, no SAP header):
 * 5 = Peer Description request
 * 6 = Peer Description response
 * 0x10/0x11/0x12 = WSM Auth
 */
object SapProtocol {

    // SAP frame types (1-bit: 0=Data, 1=Control)
    const val FRAME_TYPE_DATA = 0
    const val FRAME_TYPE_CONTROL = 1

    // Default session for service management messages
    const val DEFAULT_SESSION_ID = 0x3FF

    // SAP message types (inside SAP Data frame payload)
    const val MSG_SERVICE_CONN_REQUEST = 1
    const val MSG_SERVICE_CONN_RESPONSE = 2
    const val MSG_DATA = 3           // Data (on connected sessions): [03][channelId 2B][appData]
    const val MSG_DISCONNECT = 4     // Disconnect/Close

    data class SapHeader(
        val version: Int,
        val frameType: Int,
        val sessionId: Int,
    )

    data class SapMessage(
        val header: SapHeader,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?) = other is SapMessage &&
                header == other.header && payload.contentEquals(other.payload)
        override fun hashCode() = header.hashCode() * 31 + payload.contentHashCode()
    }

    /**
     * Encode a SAP header into 2 bytes (big-endian).
     *
     * From sapd saprotocol.cc packFrame():
     *   header = (version & 0x7) << 13 | (frameType & 0x1) << 12 | (sessionId & 0x3FF) << 2
     */
    fun encodeHeader(header: SapHeader): ByteArray {
        val word = ((header.version and 0x07) shl 13) or
                ((header.frameType and 0x01) shl 12) or
                ((header.sessionId and 0x3FF) shl 2)
        return byteArrayOf(
            ((word shr 8) and 0xFF).toByte(),
            (word and 0xFF).toByte(),
        )
    }

    /**
     * Decode a SAP header from 2 bytes.
     */
    fun decodeHeader(data: ByteArray, offset: Int = 0): SapHeader {
        val word = ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
        return SapHeader(
            version = (word shr 13) and 0x07,
            frameType = (word shr 12) and 0x01,
            sessionId = (word shr 2) and 0x3FF,
        )
    }

    /**
     * Build a complete SAP frame (header + payload).
     */
    fun buildFrame(frameType: Int, sessionId: Int, payload: ByteArray, version: Int = 0): ByteArray {
        val header = encodeHeader(SapHeader(version, frameType, sessionId))
        return header + payload
    }

    /**
     * Parse a SAP frame from raw data.
     */
    fun parseFrame(data: ByteArray): SapMessage? {
        if (data.size < 2) return null
        val header = decodeHeader(data)
        val payload = if (data.size > 2) data.copyOfRange(2, data.size) else ByteArray(0)
        return SapMessage(header, payload)
    }

    // --- Service Connection ---

    /**
     * Build a Service Connection request payload (goes inside a SAP Data frame).
     *
     * From sapd saprotocol.cc packServiceConnectionRequest():
     * [1B type=1][2B acceptorId BE][2B initiatorId BE]
     * [profile bytes + ';'][2B nSessions BE]
     * [2B*n sessionIds][2B*n channelIds][3B*n QoS][1B*n payloadType]
     *
     * acceptorId = remote peer's agentId (FIRST)
     * initiatorId = our agentId (SECOND)
     */
    // QoS types (from sapd SAPChannelInfo)
    const val QOS_UNRESTRICTED_IN_ORDER = 0
    const val QOS_DATA_RATE_LOW = 0
    const val QOS_PRIORITY_LOW = 0
    const val QOS_PRIORITY_HIGH = 2

    // Payload types
    const val PAYLOAD_NONE = 0
    const val PAYLOAD_BINARY = 1
    const val PAYLOAD_JSON = 2

    fun buildServiceConnectionRequest(
        acceptorId: Int,
        initiatorId: Int,
        profileName: String,
        sessionId: Int,
        channelId: Int,
        qosPriority: Int = QOS_PRIORITY_LOW,
        payloadType: Int = PAYLOAD_NONE,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // Message type (1B)
        out.write(MSG_SERVICE_CONN_REQUEST)

        // Acceptor ID — remote peer (2B BE) — FIRST
        out.write((acceptorId shr 8) and 0xFF)
        out.write(acceptorId and 0xFF)

        // Initiator ID — us (2B BE) — SECOND
        out.write((initiatorId shr 8) and 0xFF)
        out.write(initiatorId and 0xFF)

        // Profile name + ';' terminator
        out.write(profileName.toByteArray(Charsets.UTF_8))
        out.write(0x3B) // ';'

        // Number of sessions (2B BE)
        out.write(0)
        out.write(1)

        // Session ID (2B BE)
        out.write((sessionId shr 8) and 0xFF)
        out.write(sessionId and 0xFF)

        // Channel ID (2B BE)
        out.write((channelId shr 8) and 0xFF)
        out.write(channelId and 0xFF)

        // QoS: type(1B), dataRate(1B), priority(1B)
        out.write(QOS_UNRESTRICTED_IN_ORDER)
        out.write(QOS_DATA_RATE_LOW)
        out.write(qosPriority)

        // Payload type (1B)
        out.write(payloadType)

        return out.toByteArray()
    }

    data class SessionSpec(
        val sessionId: Int,
        val channelId: Int,
        val qosType: Int = QOS_UNRESTRICTED_IN_ORDER,
        val qosDataRate: Int = QOS_DATA_RATE_LOW,
        val qosPriority: Int = QOS_PRIORITY_LOW,
        val payloadType: Int = PAYLOAD_NONE,
    )

    fun buildMultiSessionConnectionRequest(
        acceptorId: Int,
        initiatorId: Int,
        profileName: String,
        sessions: List<SessionSpec>,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(MSG_SERVICE_CONN_REQUEST)
        out.write((acceptorId shr 8) and 0xFF)
        out.write(acceptorId and 0xFF)
        out.write((initiatorId shr 8) and 0xFF)
        out.write(initiatorId and 0xFF)
        out.write(profileName.toByteArray(Charsets.UTF_8))
        out.write(0x3B)
        out.write((sessions.size shr 8) and 0xFF)
        out.write(sessions.size and 0xFF)
        for (s in sessions) {
            out.write((s.sessionId shr 8) and 0xFF)
            out.write(s.sessionId and 0xFF)
        }
        for (s in sessions) {
            out.write((s.channelId shr 8) and 0xFF)
            out.write(s.channelId and 0xFF)
        }
        for (s in sessions) {
            out.write(s.qosType)
            out.write(s.qosDataRate)
            out.write(s.qosPriority)
        }
        for (s in sessions) {
            out.write(s.payloadType)
        }
        return out.toByteArray()
    }

    /**
     * Build a SAP Data message payload for a connected session.
     *
     * From sapd saprotocol.cc packData():
     * [1B type=3][2B channelId BE][application data]
     *
     * This wrapping is REQUIRED for application data on connected sessions.
     * The channelId routes the data to the correct service handler.
     */
    fun buildDataPayload(channelId: Int, appData: ByteArray): ByteArray {
        val out = ByteArray(3 + appData.size)
        out[0] = MSG_DATA.toByte()
        out[1] = ((channelId shr 8) and 0xFF).toByte()
        out[2] = (channelId and 0xFF).toByte()
        System.arraycopy(appData, 0, out, 3, appData.size)
        return out
    }

    /**
     * Parse a SAP Data message payload from a connected session.
     * Returns (channelId, appData) or null if not a type=3 message.
     */
    fun parseDataPayload(payload: ByteArray): Pair<Int, ByteArray>? {
        if (payload.size < 3 || (payload[0].toInt() and 0xFF) != MSG_DATA) return null
        val channelId = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        val appData = payload.copyOfRange(3, payload.size)
        return Pair(channelId, appData)
    }

    /**
     * Wrap a service management message in a SAP Data frame on the default session.
     */
    fun wrapInDefaultFrame(payload: ByteArray): ByteArray {
        return buildFrame(FRAME_TYPE_DATA, DEFAULT_SESSION_ID, payload)
    }

    /**
     * Build a time sync JSON payload for Host Manager.
     */
    fun buildTimeSyncPayload(
        timeMillis: Long,
        timezoneOffsetMinutes: Int,
        timezoneName: String,
    ): ByteArray {
        val json = """{"mgr_sync_init_setting_req":{"time":$timeMillis,"timezone":$timezoneOffsetMinutes,"timezone_name":"$timezoneName"}}"""
        return json.toByteArray(Charsets.UTF_8)
    }
}

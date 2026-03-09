package com.organizeur.app.wearable.miband

import java.util.UUID

object MiBand4Constants {
    // Mi Band 4 service UUIDs
    val SERVICE_MIBAND = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    val SERVICE_MIBAND2 = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
    val SERVICE_HEART_RATE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val SERVICE_ALERT = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
    val SERVICE_DEVICE_INFO = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // Auth characteristic (on service FEE1)
    val CHAR_AUTH = UUID.fromString("00000009-0000-3512-2118-0009af100700")

    // Mi Band service characteristics (on service FEE0)
    val CHAR_BATTERY = UUID.fromString("00000006-0000-3512-2118-0009af100700")
    val CHAR_STEPS = UUID.fromString("00000007-0000-3512-2118-0009af100700")
    val CHAR_CONFIGURATION = UUID.fromString("00000003-0000-3512-2118-0009af100700")
    val CHAR_MUSIC_NOTIFICATION = UUID.fromString("00000010-0000-3512-2118-0009af100700")
    val CHAR_MUSIC_EVENT = UUID.fromString("00000010-0000-3512-2118-0009af100700")
    val CHAR_CHUNKED_TRANSFER = UUID.fromString("00000020-0000-3512-2118-0009af100700")

    // Heart rate characteristics (on service 180D)
    val CHAR_HEART_RATE_MEASURE = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val CHAR_HEART_RATE_CONTROL = UUID.fromString("00002a39-0000-1000-8000-00805f9b34fb")

    // Alert characteristic (on service 1802)
    val CHAR_ALERT = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb")

    // Device info characteristics (on service 180A)
    val CHAR_SERIAL_NUMBER = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val CHAR_HARDWARE_REV = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val CHAR_SOFTWARE_REV = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    // DFU service and characteristics (for watchface upload)
    val SERVICE_DFU = UUID.fromString("00001530-0000-3512-2118-0009af100700")
    val CHAR_DFU_CONTROL = UUID.fromString("00001531-0000-3512-2118-0009af100700")
    val CHAR_DFU_DATA = UUID.fromString("00001532-0000-3512-2118-0009af100700")

    // CCC descriptor for notifications
    val DESC_CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Auth command prefixes
    val AUTH_SEND_KEY = byteArrayOf(0x01, 0x00)
    val AUTH_REQUEST_RANDOM = byteArrayOf(0x02, 0x00)
    val AUTH_SEND_ENCRYPTED = byteArrayOf(0x03, 0x00)

    // Auth response prefixes
    const val AUTH_RESPONSE = 0x10.toByte()
    const val AUTH_SUCCESS = 0x01.toByte()
    const val AUTH_FAIL = 0x04.toByte()
    const val AUTH_SEND_KEY_OK = 0x01.toByte()
    const val AUTH_REQUEST_RANDOM_OK = 0x02.toByte()
    const val AUTH_SEND_ENCRYPTED_OK = 0x03.toByte()

    // Heart rate control commands
    val HR_START_CONTINUOUS = byteArrayOf(0x15, 0x01, 0x01)
    val HR_STOP_CONTINUOUS = byteArrayOf(0x15, 0x01, 0x00)
    val HR_START_MANUAL = byteArrayOf(0x15, 0x02, 0x01)
    val HR_STOP_MANUAL = byteArrayOf(0x15, 0x02, 0x00)

    // Alert levels
    val ALERT_NONE = byteArrayOf(0x00)
    val ALERT_MESSAGE = byteArrayOf(0x01)
    val ALERT_CALL = byteArrayOf(0x02)
    val ALERT_VIBRATE = byteArrayOf(0x03)

    // Music event types
    const val MUSIC_PLAY = 0x00.toByte()
    const val MUSIC_PAUSE = 0x01.toByte()
    const val MUSIC_NEXT = 0x03.toByte()
    const val MUSIC_PREV = 0x04.toByte()
    const val MUSIC_VOL_UP = 0x05.toByte()
    const val MUSIC_VOL_DOWN = 0x06.toByte()

    // Music notification: enable music controls on the band
    val MUSIC_FLAG_ENABLE = byteArrayOf(0x01)
    val MUSIC_FLAG_DISABLE = byteArrayOf(0x00)
}

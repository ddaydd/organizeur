package com.organizeur.app.wearable.model

sealed class BandState {
    data object Disconnected : BandState()
    data object Scanning : BandState()
    data class Connecting(val deviceName: String) : BandState()
    data class Connected(val deviceName: String) : BandState()
    data class Authenticating(val step: String) : BandState()
    data class Authenticated(val deviceName: String) : BandState()
    data class Error(val message: String) : BandState()
}

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

data class BandData(
    val batteryLevel: Int? = null,
    val heartRate: Int? = null,
    val steps: Int? = null,
    val isHeartRateMonitoring: Boolean = false,
    val lastMusicEvent: MusicEvent? = null,
)

enum class MusicEvent {
    PLAY, PAUSE, NEXT, PREV, VOL_UP, VOL_DOWN
}

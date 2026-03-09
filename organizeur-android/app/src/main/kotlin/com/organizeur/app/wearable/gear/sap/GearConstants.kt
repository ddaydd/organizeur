package com.organizeur.app.wearable.gear.sap

import java.util.UUID

object GearConstants {
    // RFCOMM UUIDs for Samsung Gear 1 (SM-V700)
    val UUID_NUDGE = UUID.fromString("a49eb41e-cb06-495c-9f4f-bb80a90cdf00")
    val UUID_DATA = UUID.fromString("a49eb41e-cb06-495c-9f4f-aa80a90cdf4a")

    // WSM protocol version (Gear 1 firmware < 773 uses version 1)
    const val WSM_VERSION = 1

    // SAP profiles
    const val PROFILE_HOST_MANAGER = "/system/hostmanager"
    const val CHANNEL_HOST_MANAGER = 103

    // CAPEX (Service Capability Discovery)
    const val PROFILE_CAPEX = "/System/Reserved/ServiceCapabilityDiscovery"
    const val CHANNEL_CAPEX = 255
    const val AGENT_ID_CAPEX = 0xFFFF
    const val PEER_ID_CAPEX = 0xFFFF

    // RFCOMM channels to try (SDP lookup often fails on Gear 1)
    val RFCOMM_CHANNELS_TO_TRY = intArrayOf(2, 3, 4, 5, 6)

    // Pre-auth message types (first byte of raw frame, no SAP header)
    const val MSG_PEER_DESCRIPTION_REQ = 5
    const val MSG_PEER_DESCRIPTION_RESP = 6

    // Clock settings (custom SAP service: phone=provider, clock face=consumer)
    const val PROFILE_CLOCK_SETTINGS = "/organizeur/clocksettings"
    const val CHANNEL_CLOCK_SETTINGS = 200

    // File Transfer (SAFileTransfer)
    const val PROFILE_FILE_TRANSFER = "/system/filetransfer"
    const val FT_CHANNEL_COMMAND = 100
    const val FT_CHANNEL_DATA = 101

    // Connection status codes
    const val STATUS_SUCCESS = 0
    const val STATUS_ERROR = 1
}

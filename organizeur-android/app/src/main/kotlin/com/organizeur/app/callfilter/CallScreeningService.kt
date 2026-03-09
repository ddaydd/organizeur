package com.organizeur.app.callfilter

import android.telecom.Call
import android.telecom.CallScreeningService
import com.organizeur.app.settings.FilterAction
import com.organizeur.app.settings.SettingsManager

class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val settings = SettingsManager(this)

        if (!settings.isFilterEnabled) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart
        if (number.isNullOrBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val isKnown = ContactChecker.isNumberInContacts(this, number)

        if (isKnown) {
            respondToCall(callDetails, CallResponse.Builder().build())
        } else {
            val response = when (settings.filterAction) {
                FilterAction.SILENCE -> CallResponse.Builder()
                    .setSilenceCall(true)
                    .setSkipNotification(false)
                    .build()

                FilterAction.REJECT -> CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            }
            respondToCall(callDetails, response)
        }
    }
}

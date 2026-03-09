package com.organizeur.app.callfilter

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

object ContactChecker {

    fun isNumberInContacts(context: Context, phoneNumber: String): Boolean {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (e: SecurityException) {
            // Permission not granted — allow the call through
            true
        }
    }
}

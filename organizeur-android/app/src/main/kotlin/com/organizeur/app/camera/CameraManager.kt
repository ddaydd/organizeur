package com.organizeur.app.camera

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.core.CameraSelector

class CameraManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit().putInt(KEY_PORT, value).apply()

    var jpegQuality: Int
        get() = prefs.getInt(KEY_QUALITY, DEFAULT_QUALITY)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    var resolutionIndex: Int
        get() = prefs.getInt(KEY_RESOLUTION, DEFAULT_RESOLUTION_INDEX)
        set(value) = prefs.edit().putInt(KEY_RESOLUTION, value).apply()

    var lensFacing: Int
        get() = prefs.getInt(KEY_LENS_FACING, CameraSelector.LENS_FACING_BACK)
        set(value) = prefs.edit().putInt(KEY_LENS_FACING, value).apply()

    companion object {
        private const val PREFS_NAME = "camera_prefs"
        private const val KEY_PORT = "port"
        private const val KEY_QUALITY = "jpeg_quality"
        private const val KEY_RESOLUTION = "resolution_index"
        private const val KEY_LENS_FACING = "lens_facing"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_QUALITY = 80
        const val DEFAULT_RESOLUTION_INDEX = 1 // 1280x720

        val RESOLUTIONS = listOf(
            Pair(640, 480),
            Pair(1280, 720),
            Pair(1920, 1080)
        )

        val RESOLUTION_LABELS = listOf("640x480", "1280x720", "1920x1080")
    }
}

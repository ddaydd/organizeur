package com.organizeur.app.wearable.miband.watchface

import android.graphics.Bitmap

sealed class WatchfaceElement(
    open var x: Int,
    open var y: Int,
    open var imageIndex: Int,
    open var imagesCount: Int,
) {
    data class Background(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 1,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class TimeHours(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class TimeMinutes(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class TimeColon(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 1,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class Date(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class WeekDay(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 21,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class Steps(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class HeartRate(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    data class Battery(
        override var x: Int = 0, override var y: Int = 0,
        override var imageIndex: Int = 0, override var imagesCount: Int = 10,
    ) : WatchfaceElement(x, y, imageIndex, imagesCount)

    val displayName: String get() = when (this) {
        is Background -> "Fond"
        is TimeHours -> "Heures"
        is TimeMinutes -> "Minutes"
        is TimeColon -> "Separateur"
        is Date -> "Date"
        is WeekDay -> "Jour"
        is Steps -> "Pas"
        is HeartRate -> "Rythme cardiaque"
        is Battery -> "Batterie"
    }

    fun withPosition(newX: Int, newY: Int): WatchfaceElement = when (this) {
        is Background -> copy(x = newX, y = newY)
        is TimeHours -> copy(x = newX, y = newY)
        is TimeMinutes -> copy(x = newX, y = newY)
        is TimeColon -> copy(x = newX, y = newY)
        is Date -> copy(x = newX, y = newY)
        is WeekDay -> copy(x = newX, y = newY)
        is Steps -> copy(x = newX, y = newY)
        is HeartRate -> copy(x = newX, y = newY)
        is Battery -> copy(x = newX, y = newY)
    }

    fun withImageIndex(newIndex: Int): WatchfaceElement = when (this) {
        is Background -> copy(imageIndex = newIndex)
        is TimeHours -> copy(imageIndex = newIndex)
        is TimeMinutes -> copy(imageIndex = newIndex)
        is TimeColon -> copy(imageIndex = newIndex)
        is Date -> copy(imageIndex = newIndex)
        is WeekDay -> copy(imageIndex = newIndex)
        is Steps -> copy(imageIndex = newIndex)
        is HeartRate -> copy(imageIndex = newIndex)
        is Battery -> copy(imageIndex = newIndex)
    }
}

data class WatchfaceProject(
    val elements: MutableList<WatchfaceElement> = mutableListOf(),
    val images: MutableList<Bitmap> = mutableListOf(),
    var deviceBytes: ByteArray = DEFAULT_DEVICE_BYTES.copyOf(),
    var unknownHeaderInt: Int = 0,
) {
    companion object {
        const val SCREEN_WIDTH = 120
        const val SCREEN_HEIGHT = 240
        val DEFAULT_DEVICE_BYTES = byteArrayOf(
            0x24, 0x00, 0xD0.toByte(), 0x03, 0x00, 0x00, 0x3D, 0x78
        )

        fun createDefault(): WatchfaceProject {
            val bg = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            bg.eraseColor(android.graphics.Color.BLACK)
            return WatchfaceProject(
                elements = mutableListOf(WatchfaceElement.Background(imageIndex = 0)),
                images = mutableListOf(bg),
            )
        }
    }
}

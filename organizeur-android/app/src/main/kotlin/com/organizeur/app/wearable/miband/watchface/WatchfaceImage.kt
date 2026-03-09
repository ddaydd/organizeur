package com.organizeur.app.wearable.miband.watchface

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles conversion between the Mi Band 4 custom BMP format and Android Bitmaps.
 *
 * Custom BMP format (16-byte header):
 *   Offset 0-1:   'BM' signature
 *   Offset 2-3:   uint16 LE reserved/ID
 *   Offset 4-5:   uint16 LE width
 *   Offset 6-7:   uint16 LE height
 *   Offset 8-9:   uint16 LE row stride (bytes per row)
 *   Offset 10-11: uint16 LE bits per pixel (8=palettized, 16=RGB565, 24=RGB888, 32=ARGB)
 *   Offset 12-13: uint16 LE palette entry count (0 = no palette)
 *   Offset 14-15: uint16 LE reserved (transparency key?)
 *
 * After header:
 *   [palette data: paletteCount * 4 bytes (RGBA)] if paletteCount > 0
 *   [pixel data: rowStride * height bytes]
 *
 * Pixel formats:
 *   - 8bpp palettized: each pixel is a 1-byte index into palette
 *   - 16bpp RGB565: 5R 6G 5B
 *   - 24bpp RGB888: R G B
 *   - 32bpp ARGB: R G B A (alpha inverted: 0xFF=transparent, 0x00=opaque)
 */
object WatchfaceImage {

    private const val TAG = "WatchfaceImage"
    private const val HEADER_SIZE = 16
    private const val BPP_PALETTE = 8
    private const val BPP_RGB565 = 16
    private const val BPP_RGB888 = 24
    private const val BPP_ARGB = 32

    fun decodeBmpCustom(data: ByteArray): Bitmap? {
        if (data.size < HEADER_SIZE) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Check signature
        val sig0 = buf.get().toInt() and 0xFF
        val sig1 = buf.get().toInt() and 0xFF
        if (sig0 != 'B'.code || sig1 != 'M'.code) return null

        val reserved = buf.getShort().toInt() and 0xFFFF  // offset 2-3
        val width = buf.getShort().toInt() and 0xFFFF      // offset 4-5
        val height = buf.getShort().toInt() and 0xFFFF     // offset 6-7
        val rowStride = buf.getShort().toInt() and 0xFFFF  // offset 8-9
        val bpp = buf.getShort().toInt() and 0xFFFF        // offset 10-11
        val paletteCount = buf.getShort().toInt() and 0xFFFF // offset 12-13
        // offset 14-15: reserved, skip

        if (width <= 0 || height <= 0 || width > 500 || height > 500) return null

        Log.d(TAG, "Decode: ${width}x${height} bpp=$bpp rowStride=$rowStride palette=$paletteCount")

        // Read palette if present
        val palette = if (paletteCount > 0) {
            val paletteOffset = HEADER_SIZE
            IntArray(paletteCount) { i ->
                val off = paletteOffset + i * 4
                if (off + 3 < data.size) {
                    val r = data[off].toInt() and 0xFF
                    val g = data[off + 1].toInt() and 0xFF
                    val b = data[off + 2].toInt() and 0xFF
                    val a = data[off + 3].toInt() and 0xFF
                    Color.argb(255 - a, r, g, b)
                } else {
                    Color.BLACK
                }
            }
        } else null

        val pixelDataOffset = HEADER_SIZE + paletteCount * 4
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        when (bpp) {
            BPP_PALETTE -> {
                if (palette != null) {
                    decodePalettized(data, pixelDataOffset, width, height, rowStride, palette, bitmap)
                }
            }
            BPP_RGB565 -> decodeRgb565(data, pixelDataOffset, width, height, rowStride, bitmap)
            BPP_RGB888 -> decodeRgb888(data, pixelDataOffset, width, height, rowStride, bitmap)
            BPP_ARGB -> decodeArgb(data, pixelDataOffset, width, height, rowStride, bitmap)
            else -> {
                // Try 4bpp palettized
                if (bpp in 1..8 && palette != null) {
                    decodePalettized(data, pixelDataOffset, width, height, rowStride, palette, bitmap)
                } else {
                    val fallbackRowStride = if (rowStride > 0) rowStride else width * 2
                    decodeRgb565(data, pixelDataOffset, width, height, fallbackRowStride, bitmap)
                }
            }
        }

        return bitmap
    }

    private fun decodePalettized(
        data: ByteArray, offset: Int, width: Int, height: Int,
        rowStride: Int, palette: IntArray, bitmap: Bitmap,
    ) {
        for (y in 0 until height) {
            val rowOffset = offset + y * rowStride
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x
                if (pixelOffset >= data.size) break
                val idx = data[pixelOffset].toInt() and 0xFF
                bitmap.setPixel(x, y, if (idx < palette.size) palette[idx] else Color.BLACK)
            }
        }
    }

    private fun decodeRgb565(
        data: ByteArray, offset: Int, width: Int, height: Int,
        rowStride: Int, bitmap: Bitmap,
    ) {
        val effectiveRowStride = if (rowStride > 0) rowStride else width * 2
        for (y in 0 until height) {
            val rowOffset = offset + y * effectiveRowStride
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * 2
                if (pixelOffset + 1 >= data.size) break
                val lo = data[pixelOffset].toInt() and 0xFF
                val hi = data[pixelOffset + 1].toInt() and 0xFF
                val pixel = lo or (hi shl 8)
                val r = ((pixel shr 11) and 0x1F) * 255 / 31
                val g = ((pixel shr 5) and 0x3F) * 255 / 63
                val b = (pixel and 0x1F) * 255 / 31
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
    }

    private fun decodeRgb888(
        data: ByteArray, offset: Int, width: Int, height: Int,
        rowStride: Int, bitmap: Bitmap,
    ) {
        val effectiveRowStride = if (rowStride > 0) rowStride else width * 3
        for (y in 0 until height) {
            val rowOffset = offset + y * effectiveRowStride
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * 3
                if (pixelOffset + 2 >= data.size) break
                val r = data[pixelOffset].toInt() and 0xFF
                val g = data[pixelOffset + 1].toInt() and 0xFF
                val b = data[pixelOffset + 2].toInt() and 0xFF
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
    }

    private fun decodeArgb(
        data: ByteArray, offset: Int, width: Int, height: Int,
        rowStride: Int, bitmap: Bitmap,
    ) {
        val effectiveRowStride = if (rowStride > 0) rowStride else width * 4
        for (y in 0 until height) {
            val rowOffset = offset + y * effectiveRowStride
            for (x in 0 until width) {
                val pixelOffset = rowOffset + x * 4
                if (pixelOffset + 3 >= data.size) break
                val r = data[pixelOffset].toInt() and 0xFF
                val g = data[pixelOffset + 1].toInt() and 0xFF
                val b = data[pixelOffset + 2].toInt() and 0xFF
                val a = data[pixelOffset + 3].toInt() and 0xFF
                bitmap.setPixel(x, y, Color.argb(255 - a, r, g, b))
            }
        }
    }

    /**
     * Encode a bitmap as palettized BMP (8bpp, max 256 colors).
     * Mi Band 4 only supports palettized images (2/4/8 bpp).
     */
    fun encodeBmpCustom(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height

        // Collect all unique colors (quantized to RGB565 to reduce count)
        val colorToIndex = LinkedHashMap<Int, Int>()
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val a = Color.alpha(color)
                // Quantize to reduce palette: keep 5-6-5 precision
                val rq = (r shr 3) shl 3
                val gq = (g shr 2) shl 2
                val bq = (b shr 3) shl 3
                val aq = if (a < 128) 0 else 255
                val key = Color.argb(aq, rq, gq, bq)
                pixels[y * width + x] = key
                if (key !in colorToIndex && colorToIndex.size < 255) {
                    colorToIndex[key] = colorToIndex.size
                }
            }
        }

        // If too many colors even after quantization, do median-cut style reduction
        // For now, just clamp — extra colors map to closest existing palette entry
        val palette = colorToIndex.keys.toList()
        val paletteCount = palette.size

        Log.d(TAG, "Encode: ${width}x${height}, palette=$paletteCount colors")

        val rowStride = width  // 1 byte per pixel at 8bpp
        val pixelDataSize = rowStride * height
        val paletteDataSize = paletteCount * 4
        val result = ByteArray(HEADER_SIZE + paletteDataSize + pixelDataSize)
        val buf = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buf.put('B'.code.toByte())     // 0
        buf.put('M'.code.toByte())     // 1
        buf.putShort(100)              // 2-3: reserved/ID (0x64)
        buf.putShort(width.toShort())  // 4-5: width
        buf.putShort(height.toShort()) // 6-7: height
        buf.putShort(rowStride.toShort()) // 8-9: row stride
        buf.putShort(BPP_PALETTE.toShort()) // 10-11: bpp = 8
        buf.putShort(paletteCount.toShort()) // 12-13: palette entries
        buf.putShort(0)                // 14-15: reserved

        // Palette data (RGBA, alpha=0 = opaque for all entries, matching reference format)
        for (color in palette) {
            buf.put(Color.red(color).toByte())
            buf.put(Color.green(color).toByte())
            buf.put(Color.blue(color).toByte())
            buf.put(0.toByte())
        }

        // Pixel data (palette indices)
        for (i in pixels.indices) {
            val color = pixels[i]
            val idx = colorToIndex[color] ?: findClosestPaletteIndex(color, palette)
            buf.put(idx.toByte())
        }

        return result
    }

    private fun findClosestPaletteIndex(color: Int, palette: List<Int>): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for ((i, pc) in palette.withIndex()) {
            val dr = r - Color.red(pc)
            val dg = g - Color.green(pc)
            val db = b - Color.blue(pc)
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    /**
     * Generate a small bitmap with a text label (digit, day name, colon, etc.).
     * White text on transparent background, suitable for watchface overlays.
     * If fixedWidth is null, width is auto-measured from the text.
     */
    fun generateTextBitmap(text: String, height: Int = 24, fixedWidth: Int? = null): Bitmap {
        val paint = android.graphics.Paint().apply {
            color = Color.WHITE
            textSize = height * 0.8f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val width = fixedWidth ?: (paint.measureText(text).toInt() + 2).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val x = width / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
        return bitmap
    }

    /** Measure the max width needed across digits 0-9 at the given font height. */
    private fun measureMaxDigitWidth(height: Int): Int {
        val paint = android.graphics.Paint().apply {
            textSize = height * 0.8f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        return (0..9).maxOf { paint.measureText("$it").toInt() } + 2
    }

    /** Measure the max width needed across a list of texts at the given font height. */
    private fun measureMaxTextWidth(texts: List<String>, height: Int): Int {
        val paint = android.graphics.Paint().apply {
            textSize = height * 0.8f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        return texts.maxOf { paint.measureText(it).toInt() } + 2
    }

    /**
     * Generate default images for an element type.
     * Returns the list of bitmaps to add to the project.
     * @param fontHeight pixel height for generated text images (default 24)
     */
    fun generateDefaultImages(element: WatchfaceElement, fontHeight: Int = 24): List<Bitmap> {
        return when (element) {
            is WatchfaceElement.TimeHours,
            is WatchfaceElement.TimeMinutes,
            is WatchfaceElement.Date,
            is WatchfaceElement.Steps,
            is WatchfaceElement.HeartRate,
            is WatchfaceElement.Battery -> {
                val digitWidth = measureMaxDigitWidth(fontHeight)
                (0..9).map { generateTextBitmap("$it", height = fontHeight, fixedWidth = digitWidth) }
            }
            is WatchfaceElement.WeekDay -> {
                // Mi Band 4 expects 21 images: 3 groups of 7 (for different languages)
                // Firmware selects: imageIndex + languageOffset*7 + dayOfWeek
                // First 14 = 1x1 dummy pixels, last 7 = actual day names
                val dummy = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.BLACK)
                }
                val dummies = (0 until 14).map { dummy }
                val days = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")
                val dayWidth = measureMaxTextWidth(days, fontHeight)
                val dayImages = days.map { generateTextBitmap(it, height = fontHeight, fixedWidth = dayWidth) }
                dummies + dayImages
            }
            is WatchfaceElement.TimeColon -> listOf(generateTextBitmap(":", height = fontHeight))
            is WatchfaceElement.Background -> emptyList()
        }
    }

    /**
     * Scale a bitmap to fit within maxWidth x maxHeight while preserving aspect ratio.
     */
    fun scaleBitmapToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratioW = maxWidth.toFloat() / bitmap.width
        val ratioH = maxHeight.toFloat() / bitmap.height
        val ratio = minOf(ratioW, ratioH)
        val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Scale and crop a bitmap to fill exactly targetWidth x targetHeight.
     * Scales up to cover the target, then center-crops the excess.
     */
    fun scaleBitmapToFill(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val ratioW = targetWidth.toFloat() / bitmap.width
        val ratioH = targetHeight.toFloat() / bitmap.height
        val ratio = maxOf(ratioW, ratioH)
        val scaledW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val scaledH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        val cropX = (scaledW - targetWidth) / 2
        val cropY = (scaledH - targetHeight) / 2
        return Bitmap.createBitmap(scaled, cropX, cropY, targetWidth, targetHeight)
    }
}

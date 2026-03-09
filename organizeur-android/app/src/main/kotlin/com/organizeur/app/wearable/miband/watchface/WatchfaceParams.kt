package com.organizeur.app.wearable.miband.watchface

import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf codec and watchface element serialization for Mi Band 4.
 *
 * The Mi Band 4 watchface params use Protocol Buffers encoding.
 * Wire types: 0 = varint, 2 = length-delimited (message/bytes).
 *
 * Protobuf field layout for the params table:
 * - field 2: Background → message { ImageElement }
 * - field 3: Time → message { Hours, Minutes, Delimiter }
 * - field 4: Activity → message { Steps(1) → NumberElement }
 * - field 5: Date → message { MonthAndDay(1) { Separate(1) { Month(1), Day(3) } }, WeekDay(2) }
 * - field 8: Status → message { field 4 (Battery) → field 2: NumberElement }
 *
 * NumberElement fields:
 *   1=TopLeftX, 2=TopLeftY, 3=BottomRightX, 4=BottomRightY,
 *   5=Alignment, 6=Spacing, 7=ImageIndex, 8=ImagesCount
 *
 * ImageElement fields:
 *   1=X, 2=Y, 3=ImageIndex
 */
object WatchfaceParams {

    private const val TAG = "WatchfaceParams"

    // ---- Protobuf wire types ----
    private const val WIRE_VARINT = 0
    private const val WIRE_LENGTH_DELIMITED = 2

    // ---- Protobuf codec ----

    fun encodeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (v > 0x7F) {
            out.write((v.toInt() and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v.toInt() and 0x7F)
        return out.toByteArray()
    }

    fun readVarint(data: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7F) shl shift)
            pos++
            if (b and 0x80 == 0) break
            shift += 7
            if (shift >= 64) throw IllegalStateException("Varint too long")
        }
        return result to pos
    }

    /** Encode a tag (fieldNumber + wireType) */
    private fun encodeTag(fieldNumber: Int, wireType: Int): ByteArray {
        return encodeVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    /** Encode a varint field */
    fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeTag(fieldNumber, WIRE_VARINT))
        out.write(encodeVarint(value))
        return out.toByteArray()
    }

    /** Encode a length-delimited (message) field */
    fun encodeMessageField(fieldNumber: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeTag(fieldNumber, WIRE_LENGTH_DELIMITED))
        out.write(encodeVarint(content.size.toLong()))
        out.write(content)
        return out.toByteArray()
    }

    /**
     * Parse protobuf fields from raw bytes.
     * Returns list of (fieldNumber, wireType, value) where value is:
     * - Long for varint fields
     * - ByteArray for length-delimited fields
     */
    data class ProtoField(val fieldNumber: Int, val wireType: Int, val value: Any)

    fun parseProtoFields(data: ByteArray, start: Int = 0, end: Int = data.size): List<ProtoField> {
        val fields = mutableListOf<ProtoField>()
        var pos = start
        while (pos < end) {
            val (tag, nextPos) = readVarint(data, pos)
            pos = nextPos
            val wireType = (tag.toInt() and 0x07)
            val fieldNumber = (tag.toInt() ushr 3)

            when (wireType) {
                WIRE_VARINT -> {
                    val (value, newPos) = readVarint(data, pos)
                    pos = newPos
                    fields.add(ProtoField(fieldNumber, wireType, value))
                }
                WIRE_LENGTH_DELIMITED -> {
                    val (length, newPos) = readVarint(data, pos)
                    pos = newPos
                    val content = data.copyOfRange(pos, pos + length.toInt())
                    pos += length.toInt()
                    fields.add(ProtoField(fieldNumber, wireType, content))
                }
                else -> {
                    Log.w(TAG, "Unknown wire type $wireType at field $fieldNumber, stopping")
                    break
                }
            }
        }
        return fields
    }

    /** Helper: get first varint value for a field number */
    private fun List<ProtoField>.varint(fieldNumber: Int): Long? =
        firstOrNull { it.fieldNumber == fieldNumber && it.wireType == WIRE_VARINT }?.value as? Long

    /** Helper: get first message (bytes) for a field number */
    private fun List<ProtoField>.message(fieldNumber: Int): ByteArray? =
        firstOrNull { it.fieldNumber == fieldNumber && it.wireType == WIRE_LENGTH_DELIMITED }?.value as? ByteArray

    // ---- Element parsing from protobuf ----

    /**
     * Parse a NumberElement protobuf message.
     * Fields: 1=TopLeftX, 2=TopLeftY, 3=BottomRightX, 4=BottomRightY,
     *         5=Alignment, 6=Spacing, 7=ImageIndex, 8=ImagesCount
     */
    private fun parseNumberElement(data: ByteArray): NumberElementData {
        val fields = parseProtoFields(data)
        return NumberElementData(
            topLeftX = fields.varint(1)?.toInt() ?: 0,
            topLeftY = fields.varint(2)?.toInt() ?: 0,
            bottomRightX = fields.varint(3)?.toInt() ?: 0,
            bottomRightY = fields.varint(4)?.toInt() ?: 0,
            alignment = fields.varint(5)?.toInt() ?: 0,
            spacing = fields.varint(6)?.toInt() ?: 0,
            imageIndex = fields.varint(7)?.toInt() ?: 0,
            imagesCount = fields.varint(8)?.toInt() ?: 0,
        )
    }

    /**
     * Parse an ImageElement protobuf message.
     * Fields: 1=X, 2=Y, 3=ImageIndex
     */
    private fun parseImageElement(data: ByteArray): ImageElementData {
        val fields = parseProtoFields(data)
        return ImageElementData(
            x = fields.varint(1)?.toInt() ?: 0,
            y = fields.varint(2)?.toInt() ?: 0,
            imageIndex = fields.varint(3)?.toInt() ?: 0,
        )
    }

    data class NumberElementData(
        val topLeftX: Int, val topLeftY: Int,
        val bottomRightX: Int, val bottomRightY: Int,
        val alignment: Int, val spacing: Int,
        val imageIndex: Int, val imagesCount: Int,
    )

    data class ImageElementData(
        val x: Int, val y: Int, val imageIndex: Int,
    )

    /** ImageSet: used for TwoDigits Tens/Ones, WeekDay images */
    data class ImageSetData(
        val x: Int, val y: Int, val imageIndex: Int, val imagesCount: Int,
    )

    private fun parseImageSet(data: ByteArray): ImageSetData {
        val fields = parseProtoFields(data)
        return ImageSetData(
            x = fields.varint(1)?.toInt() ?: 0,
            y = fields.varint(2)?.toInt() ?: 0,
            imageIndex = fields.varint(3)?.toInt() ?: 0,
            imagesCount = fields.varint(4)?.toInt() ?: 0,
        )
    }

    private fun encodeImageSet(img: ImageSetData): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeVarintField(1, img.x.toLong()))
        out.write(encodeVarintField(2, img.y.toLong()))
        out.write(encodeVarintField(3, img.imageIndex.toLong()))
        out.write(encodeVarintField(4, img.imagesCount.toLong()))
        return out.toByteArray()
    }

    // ---- Parse params table elements ----

    /**
     * Parse a single element's protobuf blob and return WatchfaceElement(s).
     * @param fieldId the descriptor field that tells us which element type this is
     * @param data the raw protobuf bytes for this element
     */
    fun parseElementBlob(fieldId: Int, data: ByteArray): List<WatchfaceElement> {
        val elements = mutableListOf<WatchfaceElement>()
        val fields = parseProtoFields(data)

        when (fieldId) {
            // field 2: Background - contains ImageElement
            2 -> {
                val imgBytes = fields.message(1)
                if (imgBytes != null) {
                    val img = parseImageElement(imgBytes)
                    elements.add(WatchfaceElement.Background(img.x, img.y, img.imageIndex, 1))
                }
            }
            // field 3: Time - contains Hours(1), Minutes(2), Delimiter(10)
            // Hours/Minutes are TwoDigits: Tens(1) + Ones(2), each is an ImageSet
            3 -> {
                fields.message(1)?.let { hoursBytes ->
                    val hoursFields = parseProtoFields(hoursBytes)
                    hoursFields.message(1)?.let { tensBytes ->
                        val tens = parseImageSet(tensBytes)
                        elements.add(WatchfaceElement.TimeHours(
                            tens.x, tens.y, tens.imageIndex, tens.imagesCount
                        ))
                    }
                }
                fields.message(2)?.let { minutesBytes ->
                    val minutesFields = parseProtoFields(minutesBytes)
                    minutesFields.message(1)?.let { tensBytes ->
                        val tens = parseImageSet(tensBytes)
                        elements.add(WatchfaceElement.TimeMinutes(
                            tens.x, tens.y, tens.imageIndex, tens.imagesCount
                        ))
                    }
                }
                // Delimiter is field 10 (not 3) per the spec
                val delimBytes = fields.message(10) ?: fields.message(3)
                delimBytes?.let {
                    val img = parseImageElement(it)
                    elements.add(WatchfaceElement.TimeColon(img.x, img.y, img.imageIndex, 1))
                }
            }
            // field 4: Activity - Steps(1)
            4 -> {
                fields.message(1)?.let { stepsBytes ->
                    val stepsFields = parseProtoFields(stepsBytes)
                    stepsFields.message(1)?.let { numBytes ->
                        val num = parseNumberElement(numBytes)
                        elements.add(WatchfaceElement.Steps(
                            num.topLeftX, num.topLeftY, num.imageIndex, num.imagesCount
                        ))
                    }
                }
            }
            // field 5: Date — Mi Band 4 structure (from reference watchface):
            // MonthAndDay(1) { Separate(1) { Month(1), Day(3) }, TwoDigitsMonth(3), TwoDigitsDay(4) }
            // WeekDay(2) { ImageSet }
            5 -> {
                fields.message(1)?.let { madBytes ->
                    val madFields = parseProtoFields(madBytes)
                    // Separate (field 1): Month at sub-field 1, Day at sub-field 3
                    madFields.message(1)?.let { separateBytes ->
                        val sepFields = parseProtoFields(separateBytes)
                        // Use Month (field 1) for the Date element position
                        sepFields.message(1)?.let { monthBytes ->
                            val num = parseNumberElement(monthBytes)
                            elements.add(WatchfaceElement.Date(
                                num.topLeftX, num.topLeftY, num.imageIndex, num.imagesCount
                            ))
                        }
                    }
                }
                // WeekDay is field 2 (confirmed by reference watchface)
                fields.message(2)?.let { weekDayBytes ->
                    val imgSet = parseImageSet(weekDayBytes)
                    elements.add(WatchfaceElement.WeekDay(imgSet.x, imgSet.y, imgSet.imageIndex, imgSet.imagesCount))
                }
            }
            // field 8: Status — battery percentage at sub-field 4 → field 2 (NumberElement)
            8 -> {
                fields.message(4)?.let { batteryBytes ->
                    val batteryFields = parseProtoFields(batteryBytes)
                    batteryFields.message(2)?.let { numBytes ->
                        val num = parseNumberElement(numBytes)
                        elements.add(WatchfaceElement.Battery(
                            num.topLeftX, num.topLeftY, num.imageIndex, num.imagesCount
                        ))
                    }
                }
            }
            // field 9: Legacy battery format (backward compat with old saved .bin)
            9 -> {
                fields.message(1)?.let { textBytes ->
                    val textFields = parseProtoFields(textBytes)
                    textFields.message(1)?.let { numBytes ->
                        val num = parseNumberElement(numBytes)
                        elements.add(WatchfaceElement.Battery(
                            num.topLeftX, num.topLeftY, num.imageIndex, num.imagesCount
                        ))
                    }
                }
            }
            // field 7: Status (contains HeartRate among others)
            7 -> {
                // HeartRate is typically sub-field 1 of Status
                fields.message(1)?.let { hrBytes ->
                    val hrFields = parseProtoFields(hrBytes)
                    hrFields.message(1)?.let { numBytes ->
                        val num = parseNumberElement(numBytes)
                        elements.add(WatchfaceElement.HeartRate(
                            num.topLeftX, num.topLeftY, num.imageIndex, num.imagesCount
                        ))
                    }
                }
            }
            else -> {
                Log.d(TAG, "Unknown element fieldId=$fieldId, skipping (${data.size} bytes)")
            }
        }

        return elements
    }

    // ---- Generate protobuf for elements ----

    private fun encodeNumberElement(num: NumberElementData): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeVarintField(1, num.topLeftX.toLong()))
        out.write(encodeVarintField(2, num.topLeftY.toLong()))
        out.write(encodeVarintField(3, num.bottomRightX.toLong()))
        out.write(encodeVarintField(4, num.bottomRightY.toLong()))
        out.write(encodeVarintField(5, num.alignment.toLong()))
        out.write(encodeVarintField(6, num.spacing.toLong()))
        out.write(encodeVarintField(7, num.imageIndex.toLong()))
        out.write(encodeVarintField(8, num.imagesCount.toLong()))
        return out.toByteArray()
    }

    private fun encodeImageElement(img: ImageElementData): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encodeVarintField(1, img.x.toLong()))
        out.write(encodeVarintField(2, img.y.toLong()))
        out.write(encodeVarintField(3, img.imageIndex.toLong()))
        return out.toByteArray()
    }

    /**
     * Generate protobuf blobs for all elements, grouped by their top-level field.
     * Returns a map of fieldId → protobuf bytes for each element group.
     * @param images the project images, used to compute digit widths/heights for bounding boxes
     */
    fun generateElementBlobs(elements: List<WatchfaceElement>, images: List<android.graphics.Bitmap> = emptyList()): Map<Int, ByteArray> {
        val blobs = mutableMapOf<Int, ByteArray>()

        /** Get width/height of a digit image for an element */
        fun digitSize(el: WatchfaceElement): Pair<Int, Int> {
            val img = images.getOrNull(el.imageIndex)
            return (img?.width ?: 10) to (img?.height ?: 24)
        }

        // Background (field 2)
        elements.filterIsInstance<WatchfaceElement.Background>().firstOrNull()?.let { bg ->
            val imgData = encodeImageElement(ImageElementData(bg.x, bg.y, bg.imageIndex))
            val content = encodeMessageField(1, imgData)
            blobs[2] = content
        }

        // Time (field 3): Hours(1), Minutes(2), Delimiter(10)
        // Hours/Minutes use TwoDigits: Tens(1) + Ones(2), each is ImageSet
        val hours = elements.filterIsInstance<WatchfaceElement.TimeHours>().firstOrNull()
        val minutes = elements.filterIsInstance<WatchfaceElement.TimeMinutes>().firstOrNull()
        val colon = elements.filterIsInstance<WatchfaceElement.TimeColon>().firstOrNull()
        if (hours != null || minutes != null || colon != null) {
            val out = ByteArrayOutputStream()
            hours?.let { h ->
                val (dw, _) = digitSize(h)
                val tens = ImageSetData(h.x, h.y, h.imageIndex, h.imagesCount)
                val tensMsg = encodeMessageField(1, encodeImageSet(tens))
                val ones = ImageSetData(h.x + dw, h.y, h.imageIndex, h.imagesCount)
                val onesMsg = encodeMessageField(2, encodeImageSet(ones))
                out.write(encodeMessageField(1, tensMsg + onesMsg))
            }
            minutes?.let { m ->
                val (dw, _) = digitSize(m)
                val tens = ImageSetData(m.x, m.y, m.imageIndex, m.imagesCount)
                val tensMsg = encodeMessageField(1, encodeImageSet(tens))
                val ones = ImageSetData(m.x + dw, m.y, m.imageIndex, m.imagesCount)
                val onesMsg = encodeMessageField(2, encodeImageSet(ones))
                out.write(encodeMessageField(2, tensMsg + onesMsg))
            }
            colon?.let { c ->
                val imgData = encodeImageElement(ImageElementData(c.x, c.y, c.imageIndex))
                out.write(encodeMessageField(10, imgData))
            }
            blobs[3] = out.toByteArray()
        }

        // Helper for NumberElement-based elements (alignment=20 = right-align, matches reference)
        fun encodeNumberBlob(el: WatchfaceElement, maxDigits: Int): ByteArray {
            val (dw, dh) = digitSize(el)
            val numData = NumberElementData(
                el.x, el.y, el.x + dw * maxDigits, el.y + dh, 20, 0, el.imageIndex, el.imagesCount
            )
            return encodeNumberElement(numData)
        }

        // Activity/Steps (field 4)
        elements.filterIsInstance<WatchfaceElement.Steps>().firstOrNull()?.let { s ->
            val numMsg = encodeMessageField(1, encodeNumberBlob(s, 5))
            val stepsMsg = encodeMessageField(1, numMsg)
            blobs[4] = stepsMsg
        }

        // Date (field 5) — Mi Band 4 structure (from reference watchface):
        // MonthAndDay(1) { Separate(1) { Month(1), Day(3) }, TwoDigitsMonth(3), TwoDigitsDay(4) }
        // WeekDay(2) { ImageSet }
        val date = elements.filterIsInstance<WatchfaceElement.Date>().firstOrNull()
        val weekDay = elements.filterIsInstance<WatchfaceElement.WeekDay>().firstOrNull()
        if (date != null || weekDay != null) {
            val out = ByteArrayOutputStream()
            date?.let { d ->
                val (dw, dh) = digitSize(d)
                val gap = dw  // gap between day and month (1 digit width)

                // Day first (left) for DD/MM format
                val dayNum = NumberElementData(
                    d.x, d.y, d.x + dw * 2, d.y + dh,
                    20, 0, d.imageIndex, d.imagesCount
                )
                // Month after day + gap (right)
                val monthX = d.x + dw * 2 + gap
                val monthNum = NumberElementData(
                    monthX, d.y, monthX + dw * 2, d.y + dh,
                    20, 0, d.imageIndex, d.imagesCount
                )

                // Separate: Month at field 1, Day at field 3 (Mi Band 4 specific!)
                val separateContent = ByteArrayOutputStream()
                separateContent.write(encodeMessageField(1, encodeNumberElement(monthNum)))  // Month
                separateContent.write(encodeMessageField(3, encodeNumberElement(dayNum)))     // Day (field 3!)

                val madContent = ByteArrayOutputStream()
                madContent.write(encodeMessageField(1, separateContent.toByteArray()))  // Separate
                madContent.write(encodeVarintField(3, 1))   // TwoDigitsMonth = true
                madContent.write(encodeVarintField(4, 1))   // TwoDigitsDay = true
                out.write(encodeMessageField(1, madContent.toByteArray()))  // MonthAndDay
            }
            weekDay?.let { w ->
                val imgSet = encodeImageSet(ImageSetData(w.x, w.y, w.imageIndex, w.imagesCount))
                out.write(encodeMessageField(2, imgSet))  // field 2: WeekDay (confirmed by reference)
            }
            blobs[5] = out.toByteArray()
        }

        // HeartRate (field 7 → Status)
        elements.filterIsInstance<WatchfaceElement.HeartRate>().firstOrNull()?.let { hr ->
            val numMsg = encodeMessageField(1, encodeNumberBlob(hr, 3))
            val hrMsg = encodeMessageField(1, numMsg)
            blobs[7] = hrMsg
        }

        // Battery (field 8: full Status structure matching reference)
        // Band requires: fields 1-3 (battery icons) + field 4 (text section)
        elements.filterIsInstance<WatchfaceElement.Battery>().firstOrNull()?.let { b ->
            val out = ByteArrayOutputStream()

            // Fields 1-3: battery status icons (normal, charging, low)
            // Using dummy ImageSet pointing to image 0 (background) as placeholder
            fun encodeBatteryIcon(x: Int, y: Int, imgIdx: Int, imgCnt: Int, statusField: Int, statusVal: Int): ByteArray {
                val iconOut = ByteArrayOutputStream()
                val imgSet = ByteArrayOutputStream()
                imgSet.write(encodeVarintField(1, x.toLong()))
                imgSet.write(encodeVarintField(2, y.toLong()))
                imgSet.write(encodeVarintField(3, imgIdx.toLong()))
                imgSet.write(encodeVarintField(4, imgCnt.toLong()))
                imgSet.write(encodeVarintField(5, 0))
                iconOut.write(encodeMessageField(1, imgSet.toByteArray()))
                iconOut.write(encodeVarintField(statusField, statusVal.toLong()))
                return iconOut.toByteArray()
            }
            out.write(encodeMessageField(1, encodeBatteryIcon(0, 0, 0, 1, 2, 2)))  // normal
            out.write(encodeMessageField(2, encodeBatteryIcon(0, 0, 0, 1, 2, 1)))  // charging
            out.write(encodeMessageField(3, encodeBatteryIcon(0, 0, 0, 1, 3, 3)))  // low

            // Field 4: battery text { field 1: frame ImageElement, field 2: NumberElement }
            val textOut = ByteArrayOutputStream()
            val dummyFrame = encodeImageElement(ImageElementData(0, 0, 0))
            textOut.write(encodeMessageField(1, dummyFrame))
            textOut.write(encodeMessageField(2, encodeNumberBlob(b, 3)))
            out.write(encodeMessageField(4, textOut.toByteArray()))

            blobs[8] = out.toByteArray()
        }

        return blobs
    }
}

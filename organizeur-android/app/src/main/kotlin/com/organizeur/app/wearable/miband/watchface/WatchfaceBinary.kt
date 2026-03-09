package com.organizeur.app.wearable.miband.watchface

import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser/writer for the Mi Band 4 watchface .bin format.
 *
 * Real binary structure:
 * ```
 * [Header 40B]
 * [Descriptor protobuf (descriptorSize bytes)]
 * [Params Table protobuf (parametersTableLength bytes)]
 * [Image Offsets (imagesCount * 4B, uint32 LE, relative to image data start)]
 * [Image Data (concatenated custom BMP)]
 * ```
 *
 * Header layout (40 bytes):
 * - 0-6:   "HMDIAL\0"
 * - 7:     0xFF (BIP mode)
 * - 8-15:  0xFF padding
 * - 16-23: Device-specific bytes
 * - 24-31: 0xFF padding
 * - 32-35: uint32 LE unknown
 * - 36-39: uint32 LE descriptorSize
 *
 * Descriptor protobuf:
 * - field 1 (message): { field 1: parametersTableLength (varint), field 2: imagesCount (varint) }
 * - field 2+ (messages): { field 1: offset (varint), field 2: length (varint) } for each element
 */
object WatchfaceBinary {

    private const val TAG = "WatchfaceBinary"
    private const val HEADER_SIGNATURE = "HMDIAL"
    private const val HEADER_SIZE = 40
    private const val IMAGE_OFFSET_SIZE = 4

    /**
     * Parse a .bin watchface file into a WatchfaceProject.
     */
    fun parse(data: ByteArray): WatchfaceProject? {
        if (data.size < HEADER_SIZE) {
            Log.e(TAG, "File too small: ${data.size} bytes")
            return null
        }

        // Verify header signature
        val signature = String(data, 0, HEADER_SIGNATURE.length, Charsets.US_ASCII)
        if (signature != HEADER_SIGNATURE) {
            Log.e(TAG, "Invalid signature: $signature")
            return null
        }

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // Dump raw header for analysis
        val headerHex = data.take(HEADER_SIZE).joinToString("") { "%02x".format(it) }
        Log.d(TAG, "Raw header (40B): $headerHex")

        // Extract header fields
        val deviceBytes = data.copyOfRange(16, 24)
        buf.position(32)
        val unknownHeaderInt = buf.getInt()
        val descriptorSize = buf.getInt()

        Log.d(TAG, "Header: descriptorSize=$descriptorSize, unknownInt=$unknownHeaderInt")
        Log.d(TAG, "Device bytes: ${deviceBytes.joinToString("") { "%02x".format(it) }}")

        // Parse descriptor (protobuf, starts at offset 40)
        val descriptorStart = HEADER_SIZE
        if (descriptorStart + descriptorSize > data.size) {
            Log.e(TAG, "Descriptor overflows file: ${descriptorStart + descriptorSize} > ${data.size}")
            return null
        }
        val descriptorData = data.copyOfRange(descriptorStart, descriptorStart + descriptorSize)
        val descriptorFields = WatchfaceParams.parseProtoFields(descriptorData)

        // Field 1 of descriptor: main info (parametersTableLength, imagesCount)
        val mainInfoBytes = descriptorFields
            .firstOrNull { it.fieldNumber == 1 && it.wireType == 2 }
            ?.value as? ByteArray
        if (mainInfoBytes == null) {
            Log.e(TAG, "Descriptor missing field 1 (main info)")
            return null
        }
        val mainInfoFields = WatchfaceParams.parseProtoFields(mainInfoBytes)
        val parametersTableLength = (mainInfoFields
            .firstOrNull { it.fieldNumber == 1 && it.wireType == 0 }
            ?.value as? Long)?.toInt() ?: 0
        val imagesCount = (mainInfoFields
            .firstOrNull { it.fieldNumber == 2 && it.wireType == 0 }
            ?.value as? Long)?.toInt() ?: 0

        Log.d(TAG, "Descriptor: paramsTableLen=$parametersTableLength, imagesCount=$imagesCount")

        // Element descriptors: field 2+ of descriptor
        data class ElementDesc(val fieldId: Int, val offset: Int, val length: Int)
        val elementDescs = mutableListOf<ElementDesc>()
        for (field in descriptorFields) {
            if (field.fieldNumber >= 2 && field.wireType == 2) {
                val elemBytes = field.value as ByteArray
                val elemFields = WatchfaceParams.parseProtoFields(elemBytes)
                val offset = (elemFields
                    .firstOrNull { it.fieldNumber == 1 && it.wireType == 0 }
                    ?.value as? Long)?.toInt() ?: 0
                val length = (elemFields
                    .firstOrNull { it.fieldNumber == 2 && it.wireType == 0 }
                    ?.value as? Long)?.toInt() ?: 0
                elementDescs.add(ElementDesc(field.fieldNumber, offset, length))
                Log.d(TAG, "Element descriptor: field=${field.fieldNumber}, offset=$offset, length=$length")
            }
        }

        // Parse params table
        val paramsTableStart = descriptorStart + descriptorSize
        if (paramsTableStart + parametersTableLength > data.size) {
            Log.e(TAG, "Params table overflows file")
            return null
        }
        val paramsTableData = data.copyOfRange(paramsTableStart, paramsTableStart + parametersTableLength)

        val elements = mutableListOf<WatchfaceElement>()
        for (desc in elementDescs) {
            if (desc.offset + desc.length > paramsTableData.size) {
                Log.w(TAG, "Element at field ${desc.fieldId} overflows params table, skipping")
                continue
            }
            val elementData = paramsTableData.copyOfRange(desc.offset, desc.offset + desc.length)
            val parsed = WatchfaceParams.parseElementBlob(desc.fieldId, elementData)
            elements.addAll(parsed)
            for (el in parsed) {
                Log.d(TAG, "Parsed element: ${el.displayName} x=${el.x} y=${el.y} imgIdx=${el.imageIndex}")
            }
        }

        // Parse image offsets (relative to image data start)
        val imageOffsetsStart = paramsTableStart + parametersTableLength
        val imageDataStart = imageOffsetsStart + imagesCount * IMAGE_OFFSET_SIZE

        val images = mutableListOf<android.graphics.Bitmap>()
        if (imagesCount > 0 && imageDataStart <= data.size) {
            val relativeOffsets = mutableListOf<Int>()
            buf.position(imageOffsetsStart)
            for (i in 0 until imagesCount) {
                if (buf.remaining() >= 4) {
                    relativeOffsets.add(buf.getInt())
                }
            }

            Log.d(TAG, "Image offsets (relative): ${relativeOffsets.map { "0x%04x".format(it) }}")

            for (i in 0 until imagesCount) {
                val imgStart = imageDataStart + relativeOffsets[i]
                val imgEnd = if (i + 1 < imagesCount) {
                    imageDataStart + relativeOffsets[i + 1]
                } else {
                    data.size
                }
                if (imgStart < data.size && imgEnd <= data.size && imgStart < imgEnd) {
                    val imgData = data.copyOfRange(imgStart, imgEnd)
                    val bitmap = WatchfaceImage.decodeBmpCustom(imgData)
                    if (bitmap != null) {
                        images.add(bitmap)
                    } else {
                        Log.w(TAG, "Failed to decode image $i (${imgData.size} bytes)")
                        images.add(android.graphics.Bitmap.createBitmap(
                            1, 1, android.graphics.Bitmap.Config.ARGB_8888
                        ))
                    }
                }
            }
        }

        Log.d(TAG, "Parsed: ${elements.size} elements, ${images.size} images")

        return WatchfaceProject(
            elements = elements.toMutableList(),
            images = images,
            deviceBytes = deviceBytes,
            unknownHeaderInt = unknownHeaderInt,
        )
    }

    /**
     * Generate a .bin watchface file from a WatchfaceProject.
     */
    fun generate(project: WatchfaceProject): ByteArray {
        // 1. Generate protobuf blobs for each element group
        val elementBlobs = WatchfaceParams.generateElementBlobs(project.elements, project.images)

        // 2. Build params table: concatenate all element blobs, track offsets
        data class ElementEntry(val fieldId: Int, val offset: Int, val length: Int)
        val paramsTableOut = ByteArrayOutputStream()
        val elementEntries = mutableListOf<ElementEntry>()
        for ((fieldId, blob) in elementBlobs.toSortedMap()) {
            val offset = paramsTableOut.size()
            paramsTableOut.write(blob)
            elementEntries.add(ElementEntry(fieldId, offset, blob.size))
        }
        val paramsTable = paramsTableOut.toByteArray()

        Log.d(TAG, "Params table: ${paramsTable.size} bytes, ${elementEntries.size} elements")

        // 3. Encode images
        val encodedImages = project.images.map { WatchfaceImage.encodeBmpCustom(it) }
        for ((i, img) in encodedImages.withIndex()) {
            Log.d(TAG, "Image $i: ${img.size} bytes")
        }

        // 4. Build descriptor protobuf
        val descriptorOut = ByteArrayOutputStream()

        // Field 1: main info { parametersTableLength, imagesCount }
        val mainInfoOut = ByteArrayOutputStream()
        mainInfoOut.write(WatchfaceParams.encodeVarintField(1, paramsTable.size.toLong()))
        mainInfoOut.write(WatchfaceParams.encodeVarintField(2, encodedImages.size.toLong()))
        descriptorOut.write(WatchfaceParams.encodeMessageField(1, mainInfoOut.toByteArray()))

        // Field 2+ for each element: { offset, length }
        for (entry in elementEntries) {
            val entryOut = ByteArrayOutputStream()
            entryOut.write(WatchfaceParams.encodeVarintField(1, entry.offset.toLong()))
            entryOut.write(WatchfaceParams.encodeVarintField(2, entry.length.toLong()))
            descriptorOut.write(WatchfaceParams.encodeMessageField(entry.fieldId, entryOut.toByteArray()))
        }
        val descriptor = descriptorOut.toByteArray()

        Log.d(TAG, "Descriptor: ${descriptor.size} bytes")

        // 5. Calculate image offsets (relative to image data start)
        val relativeOffsets = mutableListOf<Int>()
        var currentOffset = 0
        for (img in encodedImages) {
            relativeOffsets.add(currentOffset)
            currentOffset += img.size
        }

        // 6. Calculate total size
        val totalSize = HEADER_SIZE + descriptor.size + paramsTable.size +
                encodedImages.size * IMAGE_OFFSET_SIZE + encodedImages.sumOf { it.size }

        // 7. Build the binary
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // Header (40 bytes)
        // 0-6: "HMDIAL\0"
        buf.put(HEADER_SIGNATURE.toByteArray(Charsets.US_ASCII))
        buf.put(0)
        // 7: 0xFF (BIP mode)
        buf.put(0xFF.toByte())
        // 8-15: 0xFF padding
        for (i in 0 until 8) buf.put(0xFF.toByte())
        // 16-23: device bytes
        buf.put(project.deviceBytes.copyOf(8))
        // 24-31: 0xFF padding
        for (i in 0 until 8) buf.put(0xFF.toByte())
        // 32-35: parameters buffer size hint (band uses this to allocate decode buffer)
        // Must be large enough or the watchface is rejected
        val paramsBufSize = maxOf(paramsTable.size, descriptor.size, 512)
        buf.putInt(paramsBufSize)
        // 36-39: descriptor size
        buf.putInt(descriptor.size)

        // Descriptor
        buf.put(descriptor)

        // Params table
        buf.put(paramsTable)

        // Image offsets (relative, uint32 LE)
        for (offset in relativeOffsets) {
            buf.putInt(offset)
        }

        // Image data
        for (img in encodedImages) {
            buf.put(img)
        }

        val result = buf.array()
        Log.d(TAG, "Generated .bin: ${result.size} bytes")
        Log.d(TAG, "Header: ${result.take(40).joinToString("") { "%02x".format(it) }}")
        Log.d(TAG, "Descriptor (${descriptor.size}B): ${descriptor.joinToString("") { "%02x".format(it) }}")
        Log.d(TAG, "Params table (${paramsTable.size}B): ${paramsTable.joinToString("") { "%02x".format(it) }}")
        Log.d(TAG, "Image offsets (relative): ${relativeOffsets.map { "0x%04x".format(it) }}")
        // Dump first 80 bytes after header for analysis
        val postHeader = result.copyOfRange(HEADER_SIZE, minOf(HEADER_SIZE + 80, result.size))
        Log.d(TAG, "Post-header (80B): ${postHeader.joinToString("") { "%02x".format(it) }}")

        return result
    }

    /**
     * Round-trip test: parse a .bin, regenerate, and log differences.
     */
    fun debugRoundTrip(originalData: ByteArray) {
        val project = parse(originalData) ?: run {
            Log.e(TAG, "RT: failed to parse original")
            return
        }
        Log.d(TAG, "RT: parsed ${project.elements.size} elements, ${project.images.size} images")
        for ((i, el) in project.elements.withIndex()) {
            Log.d(TAG, "RT: element[$i] = ${el.displayName} x=${el.x} y=${el.y} imgIdx=${el.imageIndex} imgCnt=${el.imagesCount}")
        }

        val regenerated = generate(project)
        Log.d(TAG, "RT: original=${originalData.size} bytes, regenerated=${regenerated.size} bytes")
        Log.d(TAG, "RT: orig header=${originalData.take(40).joinToString("") { "%02x".format(it) }}")
        Log.d(TAG, "RT: regen header=${regenerated.take(40).joinToString("") { "%02x".format(it) }}")

        // Compare byte by byte
        val minLen = minOf(originalData.size, regenerated.size)
        var diffCount = 0
        for (i in 0 until minLen) {
            if (originalData[i] != regenerated[i]) {
                if (diffCount < 5) {
                    val ctx = maxOf(0, i - 4)
                    val origCtx = originalData.copyOfRange(ctx, minOf(ctx + 20, originalData.size))
                        .joinToString("") { "%02x".format(it) }
                    val regenCtx = regenerated.copyOfRange(ctx, minOf(ctx + 20, regenerated.size))
                        .joinToString("") { "%02x".format(it) }
                    Log.w(TAG, "RT: DIFF at offset $i (0x${"%04x".format(i)}): orig=$origCtx regen=$regenCtx")
                }
                diffCount++
            }
        }
        if (originalData.size != regenerated.size) {
            Log.w(TAG, "RT: sizes differ: orig=${originalData.size} regen=${regenerated.size}")
        }
        if (diffCount == 0 && originalData.size == regenerated.size) {
            Log.d(TAG, "RT: PERFECT MATCH!")
        } else {
            Log.w(TAG, "RT: $diffCount byte differences found")
        }
    }
}

package com.organizeur.app.wearable.miband.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.organizeur.app.wearable.miband.watchface.WatchfaceElement
import com.organizeur.app.wearable.miband.watchface.WatchfaceProject

@Composable
fun WatchfacePreview(
    project: WatchfaceProject,
    selectedElementIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    val screenW = WatchfaceProject.SCREEN_WIDTH.toFloat()
    val screenH = WatchfaceProject.SCREEN_HEIGHT.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(screenW / screenH)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        ) {
            val scaleFactorX = size.width / screenW
            val scaleFactorY = size.height / screenH

            scale(scaleFactorX, scaleFactorY, pivot = Offset.Zero) {
                for ((index, element) in project.elements.withIndex()) {
                    if (element is WatchfaceElement.Background) {
                        drawElement(element, project.images, index == selectedElementIndex)
                    }
                }
                for ((index, element) in project.elements.withIndex()) {
                    if (element !is WatchfaceElement.Background) {
                        drawElement(element, project.images, index == selectedElementIndex)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawElement(
    element: WatchfaceElement,
    images: List<Bitmap>,
    isSelected: Boolean,
) {
    when (element) {
        is WatchfaceElement.Background -> {
            val img = images.getOrNull(element.imageIndex)
            if (img != null) {
                drawImage(
                    image = img.asImageBitmap(),
                    dstOffset = IntOffset(element.x, element.y),
                    dstSize = IntSize(img.width, img.height),
                )
            }
        }
        else -> {
            val sampleDigits = getSampleDigits(element)
            var xPos = element.x
            var drewSomething = false

            for (digit in sampleDigits) {
                if (digit < 0) {
                    val gapImg = images.getOrNull(element.imageIndex)
                    xPos += gapImg?.width ?: 8
                    continue
                }
                val imgIndex = element.imageIndex + digit
                val img = images.getOrNull(imgIndex)
                if (img != null) {
                    drawImage(
                        image = img.asImageBitmap(),
                        dstOffset = IntOffset(xPos, element.y),
                        dstSize = IntSize(img.width, img.height),
                    )
                    xPos += img.width
                    drewSomething = true
                }
            }

            if (!drewSomething) {
                val placeholderW = sampleDigits.size * 12f
                val placeholderH = 20f
                drawRect(
                    color = Color(0x88FF6600),
                    topLeft = Offset(element.x.toFloat(), element.y.toFloat()),
                    size = Size(placeholderW, placeholderH),
                )
            }
        }
    }

    if (isSelected) {
        val img = images.getOrNull(element.imageIndex)
        val imgW = img?.width?.toFloat() ?: 20f
        val imgH = img?.height?.toFloat() ?: 20f
        val sampleDigits = getSampleDigits(element)
        val digitCount = sampleDigits.count { it >= 0 }
        val gapCount = sampleDigits.count { it < 0 }
        val totalW = imgW * digitCount + imgW * gapCount
        drawRect(
            color = Color(0x4400AAFF),
            topLeft = Offset(element.x.toFloat() - 1, element.y.toFloat() - 1),
            size = Size(totalW + 2, imgH + 2),
        )
    }
}

private fun getSampleDigits(element: WatchfaceElement): List<Int> {
    return when (element) {
        is WatchfaceElement.TimeHours -> listOf(1, 2)
        is WatchfaceElement.TimeMinutes -> listOf(3, 4)
        is WatchfaceElement.Date -> listOf(1, 8, -1, 0, 2)
        is WatchfaceElement.Steps -> listOf(5, 4, 3, 2)
        is WatchfaceElement.HeartRate -> listOf(7, 5)
        is WatchfaceElement.Battery -> listOf(8, 5)
        is WatchfaceElement.WeekDay -> listOf(14)
        is WatchfaceElement.TimeColon -> listOf(0)
        is WatchfaceElement.Background -> emptyList()
    }
}

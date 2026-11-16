package com.shivam.vaultnotes.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_NINE("16:9", 16f / 9f)
}

/**
 * Full-screen In-App Image Cropper:
 * - Draggable corner handles & pan inside crop rectangle
 * - Aspect ratio chips: `Free`, `1:1`, `4:3`, `16:9`
 * - Top-right `Next` pill button saving cropped JPEG to `vault/assets/` and inserting at cursor
 */
@Composable
fun ImageCropScreen(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalVaultPalette.current

    var sourceBitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var selectedAspect by remember { mutableStateOf(CropAspectRatio.FREE) }

    // Normalized crop coordinates in [0f..1f] relative to the displayed image rect
    var cropLeft by remember { mutableFloatStateOf(0.08f) }
    var cropTop by remember { mutableFloatStateOf(0.08f) }
    var cropRight by remember { mutableFloatStateOf(0.92f) }
    var cropBottom by remember { mutableFloatStateOf(0.92f) }

    LaunchedEffect(sourceUri) {
        val loaded = withContext(Dispatchers.IO) {
            decodeSampledBitmapFromUri(context, sourceUri, 1600)
        }
        sourceBitmap = loaded
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E11))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header with Close (×) and Top-Right "Next" Pill Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.testTag("crop_cancel_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Cancel crop",
                    tint = Color.White
                )
            }

            Text(
                text = "Crop Image",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
            )

            Button(
                onClick = {
                    val bmp = sourceBitmap ?: return@Button
                    val x = (cropLeft * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
                    val y = (cropTop * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
                    val w = ((cropRight - cropLeft) * bmp.width).roundToInt().coerceIn(1, bmp.width - x)
                    val h = ((cropBottom - cropTop) * bmp.height).roundToInt().coerceIn(1, bmp.height - y)
                    val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
                    onCropConfirmed(cropped)
                },
                enabled = sourceBitmap != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.activeToolIndigo,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("crop_next_button")
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Interactive Crop Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = sourceBitmap
            if (bmp != null) {
                val imgBitmap = remember(bmp) { bmp.asImageBitmap() }
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }

                val imageBounds = remember(canvasSize, bmp.width, bmp.height) {
                    if (canvasSize.width == 0 || canvasSize.height == 0) {
                        Rect.Zero
                    } else {
                        val scale = min(
                            canvasSize.width.toFloat() / bmp.width.toFloat(),
                            canvasSize.height.toFloat() / bmp.height.toFloat()
                        )
                        val drawW = bmp.width * scale
                        val drawH = bmp.height * scale
                        val left = (canvasSize.width - drawW) / 2f
                        val top = (canvasSize.height - drawH) / 2f
                        Rect(left, top, left + drawW, top + drawH)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(imageBounds, selectedAspect) {
                            if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@pointerInput
                            var activeHandle = 0 // 1=TL, 2=TR, 3=BL, 4=BR, 5=Inside

                            detectDragGestures(
                                onDragStart = { offset ->
                                    val cL = imageBounds.left + cropLeft * imageBounds.width
                                    val cT = imageBounds.top + cropTop * imageBounds.height
                                    val cR = imageBounds.left + cropRight * imageBounds.width
                                    val cB = imageBounds.top + cropBottom * imageBounds.height
                                    val threshold = 64f

                                    activeHandle = when {
                                        (offset - Offset(cL, cT)).getDistance() < threshold -> 1
                                        (offset - Offset(cR, cT)).getDistance() < threshold -> 2
                                        (offset - Offset(cL, cB)).getDistance() < threshold -> 3
                                        (offset - Offset(cR, cB)).getDistance() < threshold -> 4
                                        offset.x in cL..cR && offset.y in cT..cB -> 5
                                        else -> 0
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x / imageBounds.width
                                    val dy = dragAmount.y / imageBounds.height
                                    val minSpan = 0.15f

                                    when (activeHandle) {
                                        1 -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSpan)
                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSpan)
                                        }
                                        2 -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minSpan, 1f)
                                            cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSpan)
                                        }
                                        3 -> {
                                            cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSpan)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minSpan, 1f)
                                        }
                                        4 -> {
                                            cropRight = (cropRight + dx).coerceIn(cropLeft + minSpan, 1f)
                                            cropBottom = (cropBottom + dy).coerceIn(cropTop + minSpan, 1f)
                                        }
                                        5 -> {
                                            val w = cropRight - cropLeft
                                            val h = cropBottom - cropTop
                                            val newL = (cropLeft + dx).coerceIn(0f, 1f - w)
                                            val newT = (cropTop + dy).coerceIn(0f, 1f - h)
                                            cropLeft = newL
                                            cropRight = newL + w
                                            cropTop = newT
                                            cropBottom = newT + h
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    if (imageBounds.width > 0f && imageBounds.height > 0f) {
                        drawImage(
                            image = imgBitmap,
                            dstOffset = IntOffset(imageBounds.left.roundToInt(), imageBounds.top.roundToInt()),
                            dstSize = IntSize(imageBounds.width.roundToInt(), imageBounds.height.roundToInt())
                        )

                        val cL = imageBounds.left + cropLeft * imageBounds.width
                        val cT = imageBounds.top + cropTop * imageBounds.height
                        val cR = imageBounds.left + cropRight * imageBounds.width
                        val cB = imageBounds.top + cropBottom * imageBounds.height

                        // Dim outside crop region
                        val scrim = Color.Black.copy(alpha = 0.55f)
                        drawRect(scrim, Offset(imageBounds.left, imageBounds.top), Size(imageBounds.width, cT - imageBounds.top))
                        drawRect(scrim, Offset(imageBounds.left, cB), Size(imageBounds.width, imageBounds.bottom - cB))
                        drawRect(scrim, Offset(imageBounds.left, cT), Size(cL - imageBounds.left, cB - cT))
                        drawRect(scrim, Offset(cR, cT), Size(imageBounds.right - cR, cB - cT))

                        // Crop rectangle border
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cL, cT),
                            size = Size(cR - cL, cB - cT),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // Rule of thirds lines
                        val thirdW = (cR - cL) / 3f
                        val thirdH = (cB - cT) / 3f
                        val gridColor = Color.White.copy(alpha = 0.35f)
                        for (i in 1..2) {
                            drawLine(gridColor, Offset(cL + thirdW * i, cT), Offset(cL + thirdW * i, cB), 1.dp.toPx())
                            drawLine(gridColor, Offset(cL, cT + thirdH * i), Offset(cR, cT + thirdH * i), 1.dp.toPx())
                        }

                        // 4 Draggable Corner Handles
                        val handleRadius = 8.dp.toPx()
                        val handleColor = Color(0xFF818CF8)
                        listOf(
                            Offset(cL, cT),
                            Offset(cR, cT),
                            Offset(cL, cB),
                            Offset(cR, cB)
                        ).forEach { corner ->
                            drawCircle(Color.White, radius = handleRadius + 2.dp.toPx(), center = corner)
                            drawCircle(handleColor, radius = handleRadius, center = corner)
                        }
                    }
                }
            }
        }

        // Aspect Ratio Chips Bar: Free, 1:1, 4:3, 16:9
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CropAspectRatio.entries.forEach { aspect ->
                val isSelected = selectedAspect == aspect
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) palette.activeToolIndigo else Color(0xFF1E2028),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (isSelected) palette.activeToolIndigo else Color(0xFF262932)
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            selectedAspect = aspect
                            val bmp = sourceBitmap
                            val ratio = aspect.ratio
                            if (bmp != null && ratio != null) {
                                val imgRatio = bmp.width.toFloat() / bmp.height.toFloat()
                                val normRatio = ratio / imgRatio
                                val size = 0.76f
                                if (normRatio >= 1f) {
                                    val w = size
                                    val h = (size / normRatio).coerceAtMost(size)
                                    cropLeft = (1f - w) / 2f
                                    cropRight = cropLeft + w
                                    cropTop = (1f - h) / 2f
                                    cropBottom = cropTop + h
                                } else {
                                    val h = size
                                    val w = (size * normRatio).coerceAtMost(size)
                                    cropLeft = (1f - w) / 2f
                                    cropRight = cropLeft + w
                                    cropTop = (1f - h) / 2f
                                    cropBottom = cropTop + h
                                }
                            }
                        }
                        .testTag("aspect_chip_${aspect.label}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = aspect.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxDimension ||
            boundsOptions.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    } catch (_: Exception) {
        null
    }
}

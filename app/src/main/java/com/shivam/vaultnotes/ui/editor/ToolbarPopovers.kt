package com.shivam.vaultnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.ui.home.SheetDragHandle
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import kotlin.math.roundToInt

/**
 * 10 Preset Text Colors for the Floating 48.dp Colour Pill Strip.
 */
val TEXT_COLOR_PRESETS = listOf(
    "#0F172A" to Color(0xFF0F172A), // Slate Black
    "#DC2626" to Color(0xFFDC2626), // Crimson Red
    "#EA580C" to Color(0xFFEA580C), // Burnt Orange
    "#D97706" to Color(0xFFD97706), // Warm Amber
    "#16A34A" to Color(0xFF16A34A), // Emerald Green
    "#0D9488" to Color(0xFF0D9488), // Deep Teal
    "#2563EB" to Color(0xFF2563EB), // Royal Blue
    "#4F46E5" to Color(0xFF4F46E5), // Crisp Indigo
    "#9333EA" to Color(0xFF9333EA), // Vivid Purple
    "#DB2777" to Color(0xFFDB2777)  // Rose Magenta
)

/**
 * 10 Preset Translucent Highlighters (alpha = 0.40f) for the Floating 48.dp Highlighter Pill Strip.
 */
val HIGHLIGHTER_PRESETS = listOf(
    "rgba(255,235,59,0.40)" to Color(0x66FFEB3B), // Classic Yellow
    "rgba(253,186,116,0.40)" to Color(0x66FDBA74), // Soft Peach
    "rgba(252,165,165,0.40)" to Color(0x66FCA5A5), // Coral Pink
    "rgba(249,168,212,0.40)" to Color(0x66F9A8D4), // Rose Highlighter
    "rgba(216,180,254,0.40)" to Color(0x66D8B4FE), // Lavender
    "rgba(165,180,252,0.40)" to Color(0x66A5B4FC), // Soft Indigo
    "rgba(147,197,253,0.40)" to Color(0x6693C5FD), // Sky Blue
    "rgba(103,232,249,0.40)" to Color(0x6667E8F9), // Cyan Mist
    "rgba(134,239,172,0.40)" to Color(0x6686EFAC), // Mint Green
    "rgba(190,242,100,0.40)" to Color(0x66BEF264)  // Lime Marker
)

/**
 * Floating 48.dp Pill Strip 8.dp above the toolbar for Colour (A) or Highlighter (🖍️)
 * with 10 preset swatches (active selection ring) + Clear button + `+` Custom HSV/Hex picker.
 */
@Composable
fun FloatingColorSwatchStrip(
    presets: List<Pair<String, Color>>,
    selectedCssColor: String?,
    onSelectColor: (String?) -> Unit,
    onOpenCustomPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = modifier
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .testTag("floating_color_strip")
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Reset / Default swatch
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.cardSurface)
                    .border(
                        width = if (selectedCssColor == null) 2.dp else 0.8.dp,
                        color = if (selectedCssColor == null) palette.activeToolIndigo else palette.hairlineBorder,
                        shape = CircleShape
                    )
                    .clickable { onSelectColor(null) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FormatColorReset,
                    contentDescription = "Clear color",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            presets.forEach { (cssValue, composeColor) ->
                val isSelected = selectedCssColor?.equals(cssValue, ignoreCase = true) == true
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(composeColor)
                        .border(
                            width = if (isSelected) 2.4.dp else 0.8.dp,
                            color = if (isSelected) palette.activeToolIndigo else palette.hairlineBorder,
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(cssValue) }
                        .testTag("swatch_$cssValue"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // `+` Custom HSV / Hex Picker Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.cardSurface)
                    .border(0.8.dp, palette.hairlineBorder, CircleShape)
                    .clickable(onClick = onOpenCustomPicker)
                    .testTag("custom_color_picker_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Custom color",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Floating Font Size Stepper / Slider Pill (`10px` to `30px`, step `2px`) 8.dp above the toolbar.
 */
@Composable
fun FloatingFontSizePill(
    currentSizeSp: Int,
    onSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val clamped = currentSizeSp.coerceIn(10, 30)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = modifier
            .height(48.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .testTag("floating_fontsize_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Decrement (-2px)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.cardSurface)
                    .border(0.8.dp, palette.hairlineBorder, CircleShape)
                    .clickable(enabled = clamped > 10) {
                        onSizeChange((clamped - 2).coerceAtLeast(10))
                    }
                    .testTag("fontsize_decrease_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "Decrease font size",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "${clamped}px",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.activeToolIndigo
                ),
                modifier = Modifier.width(40.dp)
            )

            Slider(
                value = clamped.toFloat(),
                onValueChange = { raw ->
                    val stepped = ((raw / 2f).roundToInt() * 2).coerceIn(10, 30)
                    onSizeChange(stepped)
                },
                valueRange = 10f..30f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = palette.activeToolIndigo,
                    activeTrackColor = palette.activeToolIndigo,
                    inactiveTrackColor = palette.hairlineBorder
                ),
                modifier = Modifier.width(150.dp)
            )

            // Increment (+2px)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.cardSurface)
                    .border(0.8.dp, palette.hairlineBorder, CircleShape)
                    .clickable(enabled = clamped < 30) {
                        onSizeChange((clamped + 2).coerceAtMost(30))
                    }
                    .testTag("fontsize_increase_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Increase font size",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Custom HSV / Hex Color Picker ModalBottomSheet triggered by `+` in the Colour or Highlighter strip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPickerBottomSheet(
    isHighlighter: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hue by remember { mutableFloatStateOf(if (isHighlighter) 54f else 235f) }
    var saturation by remember { mutableFloatStateOf(0.85f) }
    var value by remember { mutableFloatStateOf(0.92f) }
    var hexInput by remember { mutableStateOf(if (isHighlighter) "#FFEB3B" else "#4F46E5") }

    val previewColor = remember(hue, saturation, value, isHighlighter) {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        val c = Color(rgb)
        if (isHighlighter) c.copy(alpha = 0.40f) else c
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isHighlighter) "Custom Highlighter" else "Custom Text Colour",
                    style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(previewColor)
                        .border(0.8.dp, palette.hairlineBorder, RoundedCornerShape(10.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Hue (${hue.toInt()}°)", style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary))
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                    hexInput = String.format("#%06X", 0xFFFFFF and rgb)
                },
                valueRange = 0f..360f
            )

            Text("Saturation (${(saturation * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary))
            Slider(
                value = saturation,
                onValueChange = {
                    saturation = it
                    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                    hexInput = String.format("#%06X", 0xFFFFFF and rgb)
                },
                valueRange = 0f..1f
            )

            Text("Brightness (${(value * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary))
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                    hexInput = String.format("#%06X", 0xFFFFFF and rgb)
                },
                valueRange = 0.1f..1f
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = hexInput,
                onValueChange = { newHex ->
                    hexInput = newHex
                    try {
                        val parsed = android.graphics.Color.parseColor(newHex.trim())
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(parsed, hsv)
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                    } catch (_: Exception) {
                    }
                },
                label = { Text("Hex Code") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", color = palette.textPrimary)
                }
                Button(
                    onClick = {
                        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                        val cssString = if (isHighlighter) {
                            val r = android.graphics.Color.red(rgb)
                            val g = android.graphics.Color.green(rgb)
                            val b = android.graphics.Color.blue(rgb)
                            "rgba($r,$g,$b,0.40)"
                        } else {
                            String.format("#%06X", 0xFFFFFF and rgb)
                        }
                        onColorSelected(cssString)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryBrandAccent,
                        contentColor = palette.onPrimaryBrandAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

/**
 * Modern ModalBottomSheet for Table (⊞) asking Rows & Columns before inserting TableBlock at cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertTableBottomSheet(
    onDismiss: () -> Unit,
    onInsertTable: (Int, Int) -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rows by remember { mutableIntStateOf(3) }
    var columns by remember { mutableIntStateOf(2) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Insert Table",
                style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose initial rows and columns. You can add, remove, or resize columns anytime.",
                style = MaterialTheme.typography.bodyMedium.copy(color = palette.textSecondary)
            )
            Spacer(modifier = Modifier.height(18.dp))

            CounterPickerRow(
                label = "Rows",
                value = rows,
                onValueChange = { rows = it.coerceIn(1, 15) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            CounterPickerRow(
                label = "Columns",
                value = columns,
                onValueChange = { columns = it.coerceIn(1, 8) }
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", color = palette.textPrimary)
                }
                Button(
                    onClick = {
                        onInsertTable(rows, columns)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryBrandAccent,
                        contentColor = palette.onPrimaryBrandAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_insert_table_button")
                ) {
                    Text("Insert ${rows}×$columns Table")
                }
            }
        }
    }
}

@Composable
private fun CounterPickerRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val palette = LocalVaultPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(color = palette.textPrimary)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.cardSurface)
                        .border(0.8.dp, palette.hairlineBorder, CircleShape)
                        .clickable { onValueChange(value - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = "Decrease $label",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.cardSurface)
                        .border(0.8.dp, palette.hairlineBorder, CircleShape)
                        .clickable { onValueChange(value + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Increase $label",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

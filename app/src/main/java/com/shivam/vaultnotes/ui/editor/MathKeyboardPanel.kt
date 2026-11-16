package com.shivam.vaultnotes.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette

data class MathKeyButton(
    val label: String,
    val latexSnippet: String
)

private val CORE_MATH_KEYS = listOf(
    MathKeyButton("√", "\\sqrt{x}"),
    MathKeyButton("x²", "x^2"),
    MathKeyButton("a/b", "\\frac{a}{b}"),
    MathKeyButton("π", "\\pi"),
    MathKeyButton("θ", "\\theta"),
    MathKeyButton("∑", "\\sum_{i=1}^{n}"),
    MathKeyButton("∫", "\\int_{a}^{b}"),
    MathKeyButton("≤", "\\leq"),
    MathKeyButton("≥", "\\geq"),
    MathKeyButton("≠", "\\neq"),
    MathKeyButton("∞", "\\infty"),
    MathKeyButton("→", "\\rightarrow"),
    MathKeyButton("α", "\\alpha"),
    MathKeyButton("β", "\\beta"),
    MathKeyButton("Δ", "\\Delta"),
    MathKeyButton("λ", "\\lambda"),
    MathKeyButton("±", "\\pm"),
    MathKeyButton("×", "\\times"),
    MathKeyButton("(", "("),
    MathKeyButton(")", ")"),
    MathKeyButton("[", "["),
    MathKeyButton("]", "]"),
    MathKeyButton("{", "{"),
    MathKeyButton("}", "}")
)

private val QUICK_FORMULA_PRESETS = listOf(
    "K = \\frac{1}{2}mv^2" to "½mv²",
    "E = mc^2" to "E = mc²",
    "a^2 + b^2 = c^2" to "a²+b²=c²",
    "\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}" to "Quadratic"
)

/**
 * Custom Mathematical Keyboard Panel (`220.dp` height) with visual keys
 * (`√`, `x²`, `a/b`, `π`, `θ`, `∑`, `∫`, `≤`, `≥`, `≠`, `∞`, `→`, Greek letters, brackets)
 * + live JLaTeXMath preview + Insert button rendering LaTeX inline at the cursor anywhere.
 */
@Composable
fun MathKeyboardPanel(
    onInsertLatex: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val density = LocalDensity.current
    var latexInput by remember { mutableStateOf("K = \\frac{1}{2}mv^2") }

    val previewHeightPx = with(density) { 26.dp.toPx() }
    val previewBitmap = remember(latexInput, palette.textPrimary) {
        LatexBitmapCache.getOrRender(
            latex = latexInput.ifBlank { "x" },
            textSizePx = previewHeightPx,
            textColorArgb = palette.textPrimary.toArgb()
        ).first
    }

    Surface(
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .testTag("math_keyboard_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Top bar: Live LaTeX input + rendered preview + Backspace + Insert button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = latexInput,
                            onValueChange = { latexInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = palette.textPrimary
                            ),
                            cursorBrush = SolidColor(palette.activeToolIndigo),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("math_latex_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Math preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (latexInput.isNotEmpty()) {
                            latexInput = latexInput.dropLast(1)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Backspace",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = {
                        if (latexInput.trim().isNotEmpty()) {
                            onInsertLatex(latexInput.trim())
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.activeToolIndigo,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("math_insert_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Insert",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            // Quick formula presets strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QUICK_FORMULA_PRESETS.forEach { (tex, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.cardSurface,
                        border = BorderStroke(0.8.dp, palette.hairlineBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { latexInput = tex }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Visual Keys Grid (√, x², a/b, π, θ, ∑, ∫, ≤, ≥, ≠, ∞, →, Greek letters, brackets)
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(CORE_MATH_KEYS) { keyItem ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.cardSurface,
                        border = BorderStroke(0.8.dp, palette.hairlineBorder),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                latexInput = if (latexInput.isEmpty()) {
                                    keyItem.latexSnippet
                                } else {
                                    "$latexInput ${keyItem.latexSnippet}"
                                }
                            }
                            .testTag("math_key_${keyItem.label}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = keyItem.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

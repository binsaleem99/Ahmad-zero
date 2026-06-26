package com.zero.crm.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- NEUMORPHIC GLOBAL COLOR SCHEME ---
val NeuBg = Color(0xFF0F111A)             // Premium dark luxury slate/midnight navy background (Image 2 style)
val NeuLightShadow = Color(0x12FFFFFF)     // Crisp white highlight shadow reflection
val NeuDarkShadow = Color(0x3D000000)      // Soft, depth-creating dark shadow color for cards
val NeuTextPrimary = Color(0xFFFFFFFF)     // Pure white primary text for absolute clarity on dark bg
val NeuTextSecondary = Color(0xFF94A3B8)   // High-contrast slate grey for secondary readable text
val NeuAccent = Color(0xFF3B82F6)          // Vibrant electric blue accent
val NeuRed = Color(0xFFEF4444)             // Coral red
val NeuGreen = Color(0xFF10B981)           // Emerald green
val NeuYellow = Color(0xFFFBBF24)          // Amber yellow

// --- RE-MAP BAUHAUS COLORS FOR AUTOMATIC LEGACY COMPATIBILITY ---
// This ensures that existing references to Bauhaus colors in MainActivity are beautifully
// adapted to the premium high-contrast dark-mode theme instantly!
val BauhausBlack = Color(0xFF0F111A)       // Background of screens
val BauhausDarkGray = Color(0xFF1E213C)    // Background of cards - distinctly lighter slate (Image 2 style)
val BauhausWhite = Color(0xFFFFFFFF)       // Crisp white text
val BauhausLightGray = Color(0xFFF8FAFC)   // Off-white highlights
val BauhausMediumGray = Color(0xFF94A3B8)  // Medium slate gray for clear descriptions
val BauhausRed = Color(0xFF3B82F6)         // Electric blue accent color
val BauhausYellow = NeuYellow             // Ratings are amber
val BauhausBlue = Color(0xFF0EA5E9)        // Bright sky blue
val BauhausGridLine = Color(0x1FFFFFFF)    // Clear dividers
val BauhausGlassBorder = Color(0x22FFFFFF) // Border shine

// --- CUSTOM NEUMORPHIC EXTENSION MODIFIER ---
fun Modifier.neumorphic(
    shapeRadius: Dp = 16.dp,
    elevation: Dp = 6.dp,
    lightShadowColor: Color = Color(0x10FFFFFF),
    darkShadowColor: Color = Color(0x35000000),
    backgroundColor: Color = Color(0xFF1E213C)  // Card background is distinctly elevated
): Modifier = this.drawBehind {
    val radiusPx = shapeRadius.toPx()
    val elevationPx = elevation.toPx()
    
    drawIntoCanvas { canvas ->
        val paintDark = Paint().asFrameworkPaint().apply {
            color = backgroundColor.toArgb()
            setShadowLayer(elevationPx, elevationPx / 2f, elevationPx / 2f, darkShadowColor.toArgb())
        }
        val paintLight = Paint().asFrameworkPaint().apply {
            color = backgroundColor.toArgb()
            setShadowLayer(elevationPx, -elevationPx / 3f, -elevationPx / 3f, lightShadowColor.toArgb())
        }
        
        // Draw dark shadow
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            radiusPx, radiusPx,
            paintDark
        )
        // Draw light shadow
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            radiusPx, radiusPx,
            paintLight
        )
    }
}

// --- CONCAVE/PRESSED NEUMORPHIC SHADOW MODIFIER (FOR PRESSED BUTTONS) ---
fun Modifier.neumorphicPressed(
    shapeRadius: Dp = 16.dp,
    elevation: Dp = 4.dp,
    lightShadowColor: Color = Color(0x10FFFFFF),
    darkShadowColor: Color = Color(0x35000000),
    backgroundColor: Color = Color(0xFF1E213C)
): Modifier = this.drawBehind {
    val radiusPx = shapeRadius.toPx()
    val elevationPx = elevation.toPx()
    
    drawIntoCanvas { canvas ->
        val paintBg = Paint().asFrameworkPaint().apply {
            color = backgroundColor.toArgb()
        }
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            radiusPx, radiusPx,
            paintBg
        )
        
        // Draw recessed inner borders
        val paintInnerDark = Paint().asFrameworkPaint().apply {
            color = Color.Transparent.toArgb()
            strokeWidth = 2.dp.toPx()
            style = android.graphics.Paint.Style.STROKE
            setShadowLayer(elevationPx, elevationPx / 2f, elevationPx / 2f, darkShadowColor.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            radiusPx, radiusPx,
            paintInnerDark
        )
    }
}

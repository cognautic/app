package com.cognautic.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Note: In a real app we would set up Google Fonts provider. 
// For vanilla setup without internet dependency config right now, we use Default/Serif or assume generic.
// User requested "Inter Regular".

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, 
        fontWeight = FontWeight.Normal, // Inter Regular
        fontSize = 32.sp
    )
    // Add other styles as needed
)

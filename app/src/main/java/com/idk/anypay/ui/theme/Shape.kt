package com.idk.anypay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 Expressive Shapes
 * Features more rounded, expressive corners compared to standard Material3
 */
val Shapes = Shapes(
    // Extra Small - Used for small elements like chips
    extraSmall = RoundedCornerShape(8.dp),
    
    // Small - Used for buttons, text fields
    small = RoundedCornerShape(12.dp),
    
    // Medium - Used for cards, dialogs
    medium = RoundedCornerShape(16.dp),
    
    // Large - Used for bottom sheets, navigation drawers
    large = RoundedCornerShape(24.dp),
    
    // Extra Large - Used for full-screen dialogs
    extraLarge = RoundedCornerShape(32.dp)
)

package com.archosoftware.home.playos.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object PlayColors {
    val Base = Color(0xFF04060B)
    val Tile = Color(0xFF15181F)
    val TileFocused = Color(0xFF1B1F29)
    val Card = Color(0xFF14171F)
    val Ring = Color.White.copy(alpha = 0.9f)
    val TextDim = Color.White.copy(alpha = 0.65f)
    val Favorite = Color(0xFFFFC83D)
    val Accent = Color(0xFFA8C4FF)
    val OnAccent = Color(0xFF0B1226)
    val FocusFill = Color(0xFFE8EEFF)
}

internal val SmallTile = 56.dp
internal val FocusedTile = 92.dp
internal val TileGap = 8.dp
internal val TileShape = RoundedCornerShape(12.dp)
internal val ScreenPadding = 64.dp
internal val RowAnchor = (SmallTile / 2) + TileGap
internal val RowHeight = FocusedTile + 16.dp

internal const val TAB_APPS = 0

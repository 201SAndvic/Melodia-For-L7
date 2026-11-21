package com.lin0721.linmusic.core.ui.layout

import androidx.compose.runtime.staticCompositionLocalOf
import com.lin0721.linmusic.core.preferences.DisplayLyricCardColors

val LocalDisplayLyricCardColors = staticCompositionLocalOf { DisplayLyricCardColors() }
// Physical display role, not orientation or the current compact/wide breakpoint.
val LocalPassengerDisplay = staticCompositionLocalOf { false }

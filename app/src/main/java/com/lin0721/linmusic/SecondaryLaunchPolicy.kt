package com.lin0721.linmusic

internal enum class SecondaryLaunchAction { CREATE, REUSE, RESET_CONTENT }

/** Returning to the main screen must not rebuild a visible passenger composition. */
internal fun secondaryLaunchAction(
    existingDisplayId: Int?,
    existingIsShowing: Boolean,
    targetDisplayId: Int,
    resetContent: Boolean
): SecondaryLaunchAction = when {
    !existingIsShowing || existingDisplayId != targetDisplayId -> SecondaryLaunchAction.CREATE
    resetContent -> SecondaryLaunchAction.RESET_CONTENT
    else -> SecondaryLaunchAction.REUSE
}

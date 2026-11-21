package com.lin0721.linmusic

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryLaunchPolicyTest {
    @Test fun returningToMainReusesVisiblePassengerWithoutReset() {
        repeat(10) { assertEquals(SecondaryLaunchAction.REUSE, secondaryLaunchAction(1, true, 1, false)) }
    }
    @Test fun onlyExplicitContentChangesResetExistingPassenger() {
        assertEquals(SecondaryLaunchAction.RESET_CONTENT, secondaryLaunchAction(1, true, 1, true))
    }
    @Test fun missingClosedOrDifferentDisplayCreatesNewWindow() {
        assertEquals(SecondaryLaunchAction.CREATE, secondaryLaunchAction(null, false, 1, false))
        assertEquals(SecondaryLaunchAction.CREATE, secondaryLaunchAction(1, false, 1, false))
        assertEquals(SecondaryLaunchAction.CREATE, secondaryLaunchAction(1, true, 2, false))
    }
}

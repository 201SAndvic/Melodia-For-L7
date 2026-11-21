package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionUpdatePolicyTest {
    @Test fun lowModeKeepsLineTimingWithoutTheHighFrequencyKaraokeClock() {
        assertEquals(1000L, positionIntervalFor(false, false))
        assertEquals(1000L, positionIntervalFor(false, true))
        assertEquals(50L, positionIntervalFor(true, false))
        assertEquals(200L, positionIntervalFor(true, true))
    }
}

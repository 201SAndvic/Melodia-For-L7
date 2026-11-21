package com.lin0721.linmusic.core.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class SampledProgressTest {
    @Test fun progressRefreshesOnlyAtDisplayCadence() {
        assertEquals(1000L, quantizeProgress(1049L, 250L))
        assertEquals(1000L, quantizeProgress(1249L, 250L))
        assertEquals(1250L, quantizeProgress(1250L, 250L))
        assertEquals(1000L, quantizeProgress(1499L, 500L))
        assertEquals(0L, quantizeProgress(-100L, 500L))
    }
}

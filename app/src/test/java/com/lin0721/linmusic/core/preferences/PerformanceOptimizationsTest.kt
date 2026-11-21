package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class PerformanceOptimizationsTest {
    @Test fun newInstallHasNoOptimizationsEnabled() {
        assertTrue(mutablePreferencesOf().readPerformanceOptimizations().enabledOptions.isEmpty())
    }

    @Test fun oldEnabledModeMigratesAllOptionsWithoutChangingUntouchedOnes() {
        val legacy = booleanPreferencesKey("low_resource_mode")
        val prefs = mutablePreferencesOf(legacy to true)
        assertEquals(PerformanceOptimization.entries.toSet(), prefs.readPerformanceOptimizations().enabledOptions)
        prefs.writePerformanceOptimization(PerformanceOptimization.SKIP_COMMENTS, false)
        assertNull(prefs[legacy])
        assertEquals(PerformanceOptimization.entries.toSet() - PerformanceOptimization.SKIP_COMMENTS,
            prefs.readPerformanceOptimizations().enabledOptions)
        assertTrue(prefs.readPerformanceOptimizations().cardLoadPolicy.comments)
        assertFalse(prefs.readPerformanceOptimizations().cardLoadPolicy.wiki)
    }

    @Test fun oldDisabledModeDoesNotEnableOtherOptionsWhenOneIsEnabled() {
        val prefs = mutablePreferencesOf(booleanPreferencesKey("low_resource_mode") to false)
        prefs.writePerformanceOptimization(PerformanceOptimization.SLOW_PROGRESS, true)
        assertEquals(setOf(PerformanceOptimization.SLOW_PROGRESS), prefs.readPerformanceOptimizations().enabledOptions)
    }

    @Test fun explicitFalseOverridesOldTrueAndUnrelatedSettingsSurviveSaving() {
        val unrelated = booleanPreferencesKey("dual_display_enabled")
        val prefs = mutablePreferencesOf(booleanPreferencesKey("low_resource_mode") to true,
            booleanPreferencesKey(PerformanceOptimization.LINE_HIGHLIGHT.key) to false, unrelated to true)
        prefs.writePerformanceOptimization(PerformanceOptimization.STATIC_LYRIC_SCALE, false)
        assertFalse(prefs.readPerformanceOptimizations().enabled(PerformanceOptimization.LINE_HIGHLIGHT))
        assertEquals(true, prefs[unrelated])
    }

    @Test fun everyCombinationAndToggleIsIndependentAndRoundTrips() {
        val options = PerformanceOptimization.entries
        for (mask in 0 until (1 shl options.size)) {
            val prefs = mutablePreferencesOf()
            options.forEachIndexed { index, option -> prefs.writePerformanceOptimization(option, mask and (1 shl index) != 0) }
            val state = prefs.readPerformanceOptimizations()
            options.forEachIndexed { index, option ->
                assertEquals(mask and (1 shl index) != 0, state.enabled(option))
                val toggled = state.with(option, !state.enabled(option))
                assertEquals(setOf(option), state.enabledOptions symmetricDifference toggled.enabledOptions)
            }
        }
    }

    @Test fun cardFiltersPreserveOriginalOrderVisibilityAndUnrelatedCards() {
        val layout = FullPlayerCardLayout.DEFAULT.reversed().map {
            if (it.card == FullPlayerCard.LYRICS) it.copy(visible = false) else it
        }
        val commentsOff = PerformanceOptimizations().with(PerformanceOptimization.SKIP_COMMENTS, true)
        assertEquals(layout.filter { it.card != FullPlayerCard.COMMENTS_PREVIEW }, commentsOff.filterCards(layout))
        assertEquals(layout, PerformanceOptimizations().filterCards(layout))
        assertEquals(PerformanceOptimizations().cardLoadPolicy,
            PerformanceOptimizations().with(PerformanceOptimization.LINE_HIGHLIGHT, true).cardLoadPolicy)
    }

    private infix fun <T> Set<T>.symmetricDifference(other: Set<T>) = (this - other) + (other - this)
}

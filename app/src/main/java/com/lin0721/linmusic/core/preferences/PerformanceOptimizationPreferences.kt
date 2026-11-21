package com.lin0721.linmusic.core.preferences

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

private val legacyLowResourceKey = booleanPreferencesKey("low_resource_mode")
private val optimizationKeys = PerformanceOptimization.entries.associateWith { booleanPreferencesKey(it.key) }

internal fun Preferences.readPerformanceOptimizations(): PerformanceOptimizations =
    PerformanceOptimizations.fromStored(this[legacyLowResourceKey] ?: false,
        optimizationKeys.mapNotNull { (option, key) -> this[key]?.let { option to it } }.toMap())

internal fun MutablePreferences.writePerformanceOptimization(option: PerformanceOptimization, enabled: Boolean) {
    // Materialize ALL old defaults before removing the legacy switch, preserving untouched options.
    val next = readPerformanceOptimizations().with(option, enabled)
    optimizationKeys.forEach { (item, key) -> this[key] = next.enabled(item) }
    remove(legacyLowResourceKey)
}

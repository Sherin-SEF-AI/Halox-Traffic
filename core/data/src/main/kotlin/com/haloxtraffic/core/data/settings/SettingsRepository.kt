package com.haloxtraffic.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.haloxtraffic.core.model.DeviceTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User-facing settings (§12.8), persisted in DataStore. */
data class AppSettings(
    /** Manual tier override; null = use the profiled tier. */
    val tierOverride: DeviceTier?,
    val officerId: String,
    val jurisdictionId: String?,
    /** Blur uninvolved bystanders' faces in exported evidence by default. */
    val bystanderBlurDefault: Boolean,
    /** Days to retain sealed evidence after successful sync; 0 = never auto-purge. */
    val retentionDays: Int,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            tierOverride = p[KEY_TIER_OVERRIDE]?.let { runCatching { DeviceTier.valueOf(it) }.getOrNull() },
            officerId = p[KEY_OFFICER_ID].orEmpty(),
            jurisdictionId = p[KEY_JURISDICTION_ID],
            bystanderBlurDefault = p[KEY_BYSTANDER_BLUR] ?: true,
            retentionDays = p[KEY_RETENTION_DAYS]?.toIntOrNull() ?: 0,
        )
    }

    // Block bodies (return Unit) so the DataStore `Preferences` type never leaks to callers' classpath.
    suspend fun setTierOverride(tier: DeviceTier?) {
        dataStore.edit { p -> if (tier == null) p.remove(KEY_TIER_OVERRIDE) else p[KEY_TIER_OVERRIDE] = tier.name }
    }

    suspend fun setOfficer(officerId: String) {
        dataStore.edit { it[KEY_OFFICER_ID] = officerId }
    }

    suspend fun setJurisdiction(id: String?) {
        dataStore.edit { p -> if (id == null) p.remove(KEY_JURISDICTION_ID) else p[KEY_JURISDICTION_ID] = id }
    }

    suspend fun setBystanderBlurDefault(enabled: Boolean) {
        dataStore.edit { it[KEY_BYSTANDER_BLUR] = enabled }
    }

    suspend fun setRetentionDays(days: Int) {
        dataStore.edit { it[KEY_RETENTION_DAYS] = days.toString() }
    }

    private companion object {
        val KEY_TIER_OVERRIDE = stringPreferencesKey("tier_override")
        val KEY_OFFICER_ID = stringPreferencesKey("officer_id")
        val KEY_JURISDICTION_ID = stringPreferencesKey("jurisdiction_id")
        val KEY_BYSTANDER_BLUR = booleanPreferencesKey("bystander_blur_default")
        val KEY_RETENTION_DAYS = stringPreferencesKey("retention_days")
    }
}

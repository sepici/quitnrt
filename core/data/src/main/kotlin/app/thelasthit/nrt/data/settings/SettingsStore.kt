package app.thelasthit.nrt.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.thelasthit.nrt.domain.time.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class Settings(
    val dayStart: LocalTime = DayBoundary.DEFAULT_DAY_START,
    /** ISO country code for the catalogue, e.g. "GB". Null until chosen. */
    val country: String? = null,
    val disclaimerAcceptedAt: Instant? = null,
    /** When the user stopped smoking or vaping, for the gentle "most courses run 8 to 12 weeks" note. */
    val stoppedSmokingOn: LocalDate? = null,
    val onboardingComplete: Boolean = false,
) {
    val dayBoundary: DayBoundary get() = DayBoundary(dayStart)
}

class SettingsStore(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { prefs ->
        Settings(
            dayStart = prefs[DAY_START_MINUTES]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: DayBoundary.DEFAULT_DAY_START,
            country = prefs[COUNTRY],
            disclaimerAcceptedAt = prefs[DISCLAIMER_ACCEPTED_AT]?.let(Instant::ofEpochMilli),
            stoppedSmokingOn = prefs[STOPPED_SMOKING_ON]?.let(LocalDate::ofEpochDay),
            onboardingComplete = prefs[ONBOARDING_COMPLETE] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    /** Changing the day start moves entries between days, so callers recalculate logical days after this. */
    suspend fun setDayStart(time: LocalTime) = store.edit { it[DAY_START_MINUTES] = time.hour * 60 + time.minute }

    suspend fun setCountry(country: String) = store.edit { it[COUNTRY] = country.uppercase() }

    suspend fun acceptDisclaimer(at: Instant) = store.edit { it[DISCLAIMER_ACCEPTED_AT] = at.toEpochMilli() }

    suspend fun setStoppedSmokingOn(date: LocalDate?) = store.edit { prefs ->
        if (date == null) {
            prefs.remove(STOPPED_SMOKING_ON)
        } else {
            prefs[STOPPED_SMOKING_ON] = date.toEpochDay()
        }
    }

    suspend fun completeOnboarding() = store.edit { it[ONBOARDING_COMPLETE] = true }

    companion object {
        private val DAY_START_MINUTES = intPreferencesKey("day_start_minutes")
        private val COUNTRY = stringPreferencesKey("country")
        private val DISCLAIMER_ACCEPTED_AT = longPreferencesKey("disclaimer_accepted_at")
        private val STOPPED_SMOKING_ON = longPreferencesKey("stopped_smoking_on")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        /** Only one DataStore may exist per file, so the app creates this once. */
        fun createDataStore(context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })
    }
}

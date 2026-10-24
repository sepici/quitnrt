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
import app.thelasthit.nrt.domain.creature.Creature
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
    /**
     * The demon the next plan starts with: the one picked in onboarding, or the last programme's
     * once it closes, with any restyle since. When a plan starts, the demon gets its HP and a row
     * in the database (ProgrammeRepository.startPlan), and this stays as it was while it runs.
     */
    val demonName: String? = null,
    val demonCreature: Creature = Creature.DRAGON,
    val demonVariant: String? = null,
    /** The game's sounds. On by default, and quiet in silent mode whatever this says. */
    val soundOn: Boolean = true,
    /** The gear shown in the lair, by its key, or null for none picked yet. */
    val equippedGear: String? = null,
    /** Estimated absorbed amounts beside the labelled mg (ROADMAP P9-1). Off by default. */
    val showAbsorbed: Boolean = false,
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
            demonName = prefs[DEMON_NAME],
            demonCreature = Creature.fromKey(prefs[DEMON_CREATURE]),
            demonVariant = prefs[DEMON_VARIANT],
            soundOn = prefs[SOUND_ON] ?: true,
            equippedGear = prefs[EQUIPPED_GEAR],
            showAbsorbed = prefs[SHOW_ABSORBED] ?: false,
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

    /** The demon the next plan starts with. A blank or null [name], or a null [variant], shows the app's default. */
    suspend fun setDemon(name: String?, creature: Creature, variant: String?) = store.edit { prefs ->
        val trimmed = name?.trim()?.ifBlank { null }
        if (trimmed == null) prefs.remove(DEMON_NAME) else prefs[DEMON_NAME] = trimmed
        prefs[DEMON_CREATURE] = creature.key
        if (variant == null) prefs.remove(DEMON_VARIANT) else prefs[DEMON_VARIANT] = variant
    }

    suspend fun setSoundOn(on: Boolean) = store.edit { it[SOUND_ON] = on }

    /** Only gear that's unlocked should be equipped: GameRepository.equip checks that. */
    suspend fun setEquippedGear(key: String?) = store.edit { prefs ->
        if (key == null) {
            prefs.remove(EQUIPPED_GEAR)
        } else {
            prefs[EQUIPPED_GEAR] = key
        }
    }

    suspend fun setShowAbsorbed(on: Boolean) = store.edit { it[SHOW_ABSORBED] = on }

    /**
     * When a backup was last restored, or null if none has been. A restore writes every entry it
     * brings in at that moment, so an agent's undo leaves out everything written up to it.
     */
    suspend fun restoredAt(): Instant? = store.data.first()[RESTORED_AT]?.let(Instant::ofEpochMilli)

    suspend fun setRestoredAt(at: Instant) = store.edit { it[RESTORED_AT] = at.toEpochMilli() }

    companion object {
        private val RESTORED_AT = longPreferencesKey("restored_at")
        private val DAY_START_MINUTES = intPreferencesKey("day_start_minutes")
        private val COUNTRY = stringPreferencesKey("country")
        private val DISCLAIMER_ACCEPTED_AT = longPreferencesKey("disclaimer_accepted_at")
        private val STOPPED_SMOKING_ON = longPreferencesKey("stopped_smoking_on")
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val DEMON_NAME = stringPreferencesKey("demon_name")
        private val DEMON_CREATURE = stringPreferencesKey("demon_creature")
        private val DEMON_VARIANT = stringPreferencesKey("demon_variant")
        private val SOUND_ON = booleanPreferencesKey("sound_on")
        private val EQUIPPED_GEAR = stringPreferencesKey("equipped_gear")
        private val SHOW_ABSORBED = booleanPreferencesKey("show_absorbed")

        /** Only one DataStore may exist per file, so the app creates this once. */
        fun createDataStore(context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })
    }
}

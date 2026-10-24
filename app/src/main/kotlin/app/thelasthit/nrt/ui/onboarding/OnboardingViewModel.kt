package app.thelasthit.nrt.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.data.settings.ReminderMarks
import app.thelasthit.nrt.domain.catalogue.Catalogue
import app.thelasthit.nrt.domain.catalogue.CatalogueProduct
import app.thelasthit.nrt.domain.catalogue.Markets
import app.thelasthit.nrt.domain.creature.Creature
import app.thelasthit.nrt.ui.catalogue.CatalogueBrowse
import app.thelasthit.nrt.ui.catalogue.browseFor
import app.thelasthit.nrt.domain.product.PillLabel
import app.thelasthit.nrt.domain.reminders.Reminder
import app.thelasthit.nrt.domain.reminders.ReminderSettings
import app.thelasthit.nrt.domain.reminders.SetUpReminders
import app.thelasthit.nrt.domain.time.DayBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class OnboardingStep { WELCOME, STOPPED, COUNTRY, PRODUCTS, DEMON, DAY_START, REMINDERS, DONE }

/** What the user has chosen so far. Nothing but the disclaimer is stored until "Finish set-up". */
data class OnboardingDraft(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val stoppedOn: LocalDate? = null,
    val country: String = Markets.ELSEWHERE,
    /** Catalogue IDs in the order picked, which becomes the quick log's order. */
    val picked: List<String> = emptyList(),
    val brand: String? = null,
    val query: String = "",
    val otherCountries: Boolean = false,
    val demonName: String = "",
    /** One of the drawn creatures ([Creature.isDrawn]). */
    val creature: Creature = Creature.DRAGON,
    val variant: String = DEFAULT_VARIANT,
    val dayStart: LocalTime = DayBoundary.DEFAULT_DAY_START,
    /** Which of the reminders set-up offers ([SetUpReminders.offered]) are on. */
    val reminders: Set<Reminder> = SetUpReminders.initial,
) {
    companion object {
        const val DEFAULT_VARIANT = "forest"
        const val MAX_NAME = 24
    }
}

data class OnboardingState(
    val draft: OnboardingDraft,
    val today: LocalDate,
    /** The market for the phone's region, tagged in the country list. */
    val region: String,
    val browse: CatalogueBrowse,
    val pickedProducts: List<CatalogueProduct>,
    val pickLabels: Map<String, PillLabel>,
    /** The reminder settings as set-up leaves them: what's stored, with this set-up's switches. */
    val reminders: ReminderSettings,
    /** The reminders' notes, for whether notifications have been asked for. */
    val marks: ReminderMarks,
) {
    val canName: Boolean get() = draft.demonName.isNotBlank()
}

class OnboardingViewModel(
    private val graph: DataGraph,
    /** The phone's market, such as "GB", from [Markets.forRegion]. */
    private val region: String,
    initial: OnboardingDraft = OnboardingDraft(country = region),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {

    private val draft = MutableStateFlow(initial)

    /** Parsed off the main thread, as the catalogue is a few hundred products. */
    private val catalogue = flow { emit(graph.catalogue) }.flowOn(Dispatchers.Default)

    val state: StateFlow<OnboardingState?> = combine(
        draft,
        catalogue,
        graph.reminders.reminders,
        graph.reminders.marks,
    ) { current, catalogue, reminders, marks ->
        build(current, catalogue, reminders, marks)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Set once "Finish set-up" starts, so a second tap, or a second answer from the phone, can't store it twice. */
    private var finishing = false

    private fun build(current: OnboardingDraft, catalogue: Catalogue, reminders: ReminderSettings, marks: ReminderMarks): OnboardingState {
        val picked = current.picked.mapNotNull(catalogue::find)
        return OnboardingState(
            draft = current,
            today = DayBoundary(current.dayStart).logicalDate(graph.clock.instant(), zone()),
            region = region,
            browse = catalogue.browseFor(current.country, current.otherCountries, current.brand, current.query),
            pickedProducts = picked,
            pickLabels = PillLabel.of(picked.map { it.toUserProduct() }),
            reminders = SetUpReminders.applied(reminders, current.reminders),
            marks = marks,
        )
    }

    fun next() = move(+1)

    fun back() = move(-1)

    private fun move(by: Int) = draft.update { current ->
        val steps = OnboardingStep.entries
        current.copy(step = steps[(current.step.ordinal + by).coerceIn(0, steps.lastIndex)])
    }

    /** Stores when the disclaimer was accepted, then moves on. */
    fun accept() {
        viewModelScope.launch {
            graph.settings.acceptDisclaimer(graph.clock.instant())
            next()
        }
    }

    fun pickStopDate(date: LocalDate) = draft.update { it.copy(stoppedOn = date) }

    fun skipStopDate() {
        draft.update { it.copy(stoppedOn = null) }
        next()
    }

    fun pickCountry(country: String) = draft.update { it.copy(country = country, brand = null) }

    fun pickBrand(brand: String) = draft.update { it.copy(brand = brand) }

    fun search(query: String) = draft.update { it.copy(query = query.take(60)) }

    fun showOtherCountries(show: Boolean) = draft.update { it.copy(otherCountries = show, brand = null) }

    /** Picks a product, or takes it off if it was already picked. Later picks move up. */
    fun togglePick(productId: String) = draft.update { current ->
        current.copy(picked = if (productId in current.picked) current.picked - productId else current.picked + productId)
    }

    fun nameDemon(name: String) = draft.update { it.copy(demonName = name.take(OnboardingDraft.MAX_NAME)) }

    /** Picks a creature, if it's drawn. Its colours stay, since every creature comes in the same four. */
    fun pickCreature(creature: Creature) {
        if (creature.isDrawn) draft.update { it.copy(creature = creature) }
    }

    fun pickVariant(variant: String) = draft.update { it.copy(variant = variant) }

    fun moveDayStart(steps: Int) = draft.update { it.copy(dayStart = DayBoundary.step(it.dayStart, steps)) }

    /** Switches one of the reminders set-up offers. */
    fun switchReminder(reminder: Reminder, on: Boolean) = draft.update {
        it.copy(reminders = if (on) it.reminders + reminder else it.reminders - reminder)
    }

    /**
     * Notes that the phone's notification question has been asked, just before it shows, as the
     * Reminders page does. Settings then says notifications are off, rather than inviting the
     * question, if the answer was no.
     */
    fun markPermissionAsked() {
        viewModelScope.launch { graph.reminders.markPermissionAsked() }
    }

    /** Stores everything chosen, then shows the summary. Only the first call counts. */
    fun finish() {
        if (finishing) return
        finishing = true
        val chosen = draft.value
        viewModelScope.launch {
            graph.settings.setStoppedSmokingOn(chosen.stoppedOn)
            graph.settings.setCountry(chosen.country)
            chosen.picked.forEach { graph.products.addFromCatalogue(it) }
            graph.settings.setDemon(chosen.demonName.trim(), chosen.creature, chosen.variant)
            graph.entries.changeDayStart(chosen.dayStart)
            SetUpReminders.offered.forEach { graph.reminders.setOn(it, it in chosen.reminders) }
            draft.update { it.copy(step = OnboardingStep.DONE) }
        }
    }

    /** Leaves onboarding for good. */
    fun start() {
        viewModelScope.launch { graph.settings.completeOnboarding() }
    }
}

package app.thelasthit.nrt.agents

import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionNotSupportedException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import app.thelasthit.nrt.LastHitApp
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.domain.agent.AgentLogAsk
import app.thelasthit.nrt.domain.agent.AgentRefusal
import app.thelasthit.nrt.ui.format.builtInTagName
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * The app's AppFunctions (PLAN §12): what Gemini and other on-device agents can call, on Android
 * 16 and newer. The AppFunctions compiler writes the service the manifest names,
 * [NrtAppFunctionService], and `assets/nrt_app_functions.xml`, which the system indexes, from the
 * functions here and their KDoc. On older Android the service is switched off.
 *
 * This is the only part of the app that knows the AppFunctions library, so a change in the
 * library stays here. [NrtAgent] does the work, through the same rules and repositories as the
 * rest of the app; this turns its answers into the library's types, and a refusal into the
 * library's exception with the app's own words for the agent. docs/APPFUNCTIONS.md has the adb
 * script for trying them and the notes on Google's Early Access Programme.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(serviceName = "NrtAppFunctionService", appFunctionXmlFileName = "nrt_app_functions")
abstract class NrtAppFunctions : AppFunctionService() {

    private val graph: DataGraph get() = (application as LastHitApp).graph

    private val agent by lazy {
        NrtAgent(graph, tagNames = { tag -> listOfNotNull(tag.customName, resources.builtInTagName(tag.key)) })
    }

    /**
     * Logs a dose of nicotine replacement therapy (NRT) the user has taken, such as "Log a 2 mg
     * nicotine gum" or "Log an inhalator session at 3 pm, stressed". It's logged like a dose from
     * the app's own log sheet.
     *
     * Nothing is saved when the description fits more than one of the user's products: the result
     * lists them as candidates, each with the quantity it would log, so ask which one and call
     * again with that candidate's product id as productDescription and its quantity as quantity.
     * Nothing is saved either when the same product was logged less than a minute before or after
     * from somewhere else in the app, such as its widget or log sheet: possibleDuplicate is then
     * true, so ask whether it's a second dose and call again with keepDuplicate set to true if it
     * is. That check doesn't cover doses this function logged, so each call logs a dose: if you
     * aren't sure a call went through, check getNrtSummary before calling again. A product that
     * matches nothing, a time that hasn't come yet, or a tag that names none of the user's tags is
     * refused with a message that says why, and nothing is logged.
     *
     * @param productDescription The product in the user's words, such as "2mg nicotine gum", "two
     *   4 mg gums", "nicorette 4", "21mg patch", "two sprays of QuickMist" or "inhalator session",
     *   or a product's id from listNrtProducts. A number in it is how many, unless it's the
     *   strength.
     * @param quantity How many of what the description names, from 1 to 99. Leave it out to use
     *   the description's own number, or one.
     * @param time When the dose was taken, on the phone's clock. Leave it out for now. A time in
     *   the future is refused.
     * @param tags What was going on, as tag names or keys, such as "Stressed", "After a meal" or
     *   "coffee_tea". Each has to name one of the user's tags.
     * @param cravingStrength How strong the craving was, from 1 to 5, if the user says.
     * @param keepDuplicate Set to true only after the user has said a possible duplicate is a
     *   second dose.
     * @return What was logged with the user's day and week after it, or the question to ask.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun logNrtUsage(
        productDescription: String,
        quantity: Int? = null,
        time: LocalDateTime? = null,
        tags: List<String> = emptyList(),
        cravingStrength: Int? = null,
        keepDuplicate: Boolean = false,
    ): NrtLogResult {
        val outcome = agent.log(AgentLogAsk(productDescription, quantity, time, tags, cravingStrength), keepDuplicate)
        val text = text()
        return when (outcome) {
            is AgentLogOutcome.Logged -> NrtLogResult(
                message = text.logged(outcome),
                saved = true,
                entry = outcome.entry.toResponse(text.productName(outcome.entry)),
                possibleDuplicate = false,
                duplicateOf = null,
                candidates = emptyList(),
                summary = outcome.summary.toResponse(text),
            )
            is AgentLogOutcome.PossibleDuplicate -> NrtLogResult(
                message = text.possibleDuplicate(outcome),
                saved = false,
                entry = null,
                possibleDuplicate = true,
                duplicateOf = outcome.existing.toResponse(text.productName(outcome.existing)),
                candidates = emptyList(),
                summary = null,
            )
            is AgentLogOutcome.Ambiguous -> NrtLogResult(
                message = text.ambiguous(outcome),
                saved = false,
                entry = null,
                possibleDuplicate = false,
                duplicateOf = null,
                candidates = outcome.choices.map { it.toCandidate(text) },
                summary = null,
            )
            is AgentLogOutcome.Refused -> throw refusal(outcome.reason, text, logging = true)
        }
    }

    /**
     * Lists the user's NRT products that can be logged, the ones on the app's quick log first, so
     * an agent can tell them apart and log exactly the one meant by its id.
     *
     * @return The products, with their strengths and how each is counted.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun listNrtProducts(): List<NrtProduct> {
        val text = text()
        return when (val answer = agent.products()) {
            is AgentAnswer.Given -> answer.value.map { it.toResponse(text) }
            is AgentAnswer.Refused -> throw refusal(answer.reason, text)
        }
    }

    /**
     * Summarises the user's NRT use: how much they've had today against today's daily guide, the
     * week so far against its allowance, when they last had a dose, and their demon's HP and
     * banked hits. The guide and the allowance are ceilings to stay at or under, never amounts to
     * reach, and they only exist while a taper plan runs.
     *
     * @return The user's day and week, with a few sentences to read to them.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getNrtSummary(): NrtSummary {
        val text = text()
        return when (val answer = agent.summary()) {
            is AgentAnswer.Given -> answer.value.toResponse(text)
            is AgentAnswer.Refused -> throw refusal(answer.reason, text)
        }
    }

    /**
     * Undoes the user's most recent NRT entry: the one logged last, from anywhere, as long as it
     * was logged in the last 24 hours. It goes by when the entry was logged, so a dose just logged
     * with an earlier time is the one undone. Doses brought back from a backup weren't logged here,
     * so they're never undone, and nor is anything logged before the restore. Older entries are
     * deleted in the app's History. Only call it when the user asks to undo or remove their last
     * entry.
     *
     * @return What was deleted, with the user's day and week now that it's gone.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun undoLastNrtEntry(): NrtUndoResult {
        val outcome = agent.undoLast()
        val text = text()
        return when (outcome) {
            is AgentUndoOutcome.Undone -> NrtUndoResult(
                message = text.undone(outcome),
                deleted = outcome.entry.toResponse(text.productName(outcome.entry)),
                summary = outcome.summary.toResponse(text),
            )
            is AgentUndoOutcome.Refused -> throw refusal(outcome.reason, text)
        }
    }

    /**
     * Gives the best strategy for getting through a craving, from NHS, CDC and HSE guidance: for
     * the trigger asked about, or else for the user's top trigger over the last 14 days. With too
     * little logged to know their triggers, it's the 4 Ds (delay, deep breaths, drink water, do
     * something else). The advice is general information, not medical advice, and never suggests
     * taking more nicotine. It comes with the free quitlines for the user's country, to pass on
     * when a craving is strong or keeps up, or the user wants more help.
     *
     * @param tag A trigger, as a tag name or key, such as "Stressed", "Bored" or "after_meal".
     *   Leave it out for the user's top trigger.
     * @return The strategy, its steps, why it's the one, and the quitlines.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getNrtAdvice(tag: String? = null): NrtAdvice {
        val text = text()
        return when (val answer = agent.advice(tag)) {
            is AgentAnswer.Given -> answer.value.toResponse(text)
            is AgentAnswer.Refused -> throw refusal(answer.reason, text)
        }
    }

    /** The words for this call, with the user's tag names and the phone's time format. */
    private suspend fun text(): AgentText {
        val tags = graph.tags.all.first().associate { it.key to (it.customName ?: resources.builtInTagName(it.key) ?: it.key) }
        val names = graph.products.shortNames.first()
        return AgentText(
            resources,
            is24Hour = DateFormat.is24HourFormat(this),
            tagName = { key -> tags[key] ?: key },
            shortName = { entry -> names[entry.productId] ?: entry.productName },
            now = graph.clock::instant,
        )
    }

    /** The library's exception for [reason], with the app's words for it. */
    private fun refusal(reason: AgentRefusal, text: AgentText, logging: Boolean = false): AppFunctionException {
        val message = text.refusal(reason, logging)
        return when (reason) {
            AgentRefusal.NotSetUp -> AppFunctionNotSupportedException(message)
            AgentRefusal.NothingToUndo -> AppFunctionElementNotFoundException(message)
            else -> AppFunctionInvalidArgumentException(message)
        }
    }
}

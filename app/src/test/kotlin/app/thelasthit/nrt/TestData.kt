package app.thelasthit.nrt

import app.thelasthit.nrt.TestApp.Companion.london
import app.thelasthit.nrt.data.repo.LogRequest
import app.thelasthit.nrt.domain.creature.Creature
import kotlinx.coroutines.runBlocking

const val GUM_2 = "nicorette-gum-uk-2mg"
const val GUM_4 = "nicorette-gum-uk-4mg"
const val QUICKMIST = "nicorette-quickmist-uk-1mg"
const val PATCH_14 = "niquitin-clear-patch-uk-14mg"

/** Set up as if onboarding is done: tags, the mockups' four quick-log products and Gary. */
fun TestApp.setUp() = runBlocking {
    graph.tags.ensureBuiltIns()
    listOf(GUM_2, GUM_4, QUICKMIST, PATCH_14).forEach { graph.products.addFromCatalogue(it) }
    graph.settings.setDemon("Gary", Creature.DRAGON, "forest")
    graph.settings.completeOnboarding()
}

/** The week in the Today mockup: 14, 13, 12 and 14 mg Monday to Thursday, and 8 mg so far today. */
fun TestApp.seedWeek() = runBlocking {
    fun log(product: String, at: String, quantity: Int = 1, vararg tags: String) {
        runBlocking { graph.entries.log(LogRequest(product, quantity, at = london(at), tagKeys = tags.toSet())) }
    }
    log(PATCH_14, "2026-10-19T07:30")
    log(GUM_4, "2026-10-20T08:00", 2)
    log(GUM_4, "2026-10-20T13:00")
    log(QUICKMIST, "2026-10-20T19:00")
    log(GUM_4, "2026-10-21T08:15", 3)
    log(PATCH_14, "2026-10-22T07:20")
    log(GUM_4, "2026-10-23T08:10", 1, "coffee_tea")
    log(GUM_2, "2026-10-23T10:55", 1, "work_break")
    log(GUM_2, "2026-10-23T13:40", 1, "after_meal")
}

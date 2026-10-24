package app.thelasthit.nrt.ui.settings

import android.content.res.Resources
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.repo.StoredCounts
import app.thelasthit.nrt.domain.programme.ProgrammeStatus

/**
 * What "Replace everything" deletes, in words: the doses and products, the current programme
 * and its demon, earlier programmes, badges, gear and breathing sessions, and the tag changes.
 * Things there are none of are left out, apart from the doses and products.
 */
internal fun Resources.replaceBody(counts: StoredCounts, madeOn: String): String {
    val parts = buildList {
        add(getQuantityString(R.plurals.restore_doses, counts.entries, counts.entries))
        add(getQuantityString(R.plurals.restore_products, counts.products, counts.products))
        when (counts.currentProgramme) {
            ProgrammeStatus.BASELINE -> add(getString(R.string.plan_replace_baseline))
            ProgrammeStatus.ACTIVE, ProgrammeStatus.OVERTIME -> add(getString(R.string.plan_replace_plan))
            ProgrammeStatus.COMPLETE -> add(getString(R.string.plan_replace_done))
            ProgrammeStatus.ABANDONED, null -> Unit
        }
        counts.demon?.let { demon ->
            val name = demon.demon.name ?: getString(R.string.demon_default_name)
            add(getString(R.string.plan_replace_demon, name, demon.balance.hp, demon.balance.maxHp))
        }
        val earlier = counts.programmes - if (counts.currentProgramme != null) 1 else 0
        if (earlier > 0) add(getQuantityString(R.plurals.plan_replace_earlier, earlier, earlier))
        if (counts.achievements > 0) add(getQuantityString(R.plurals.plan_replace_badges, counts.achievements, counts.achievements))
        if (counts.gear > 0) add(getQuantityString(R.plurals.plan_replace_gear, counts.gear, counts.gear))
        if (counts.breathingSessions > 0) {
            add(getQuantityString(R.plurals.plan_replace_breathing, counts.breathingSessions, counts.breathingSessions))
        }
        add(getString(R.string.plan_replace_tags))
    }
    val list = getString(R.string.plan_list_and, parts.dropLast(1).joinToString(", "), parts.last())
    return getString(R.string.plan_replace_body, list, madeOn)
}

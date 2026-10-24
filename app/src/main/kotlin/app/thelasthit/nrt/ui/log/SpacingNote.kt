package app.thelasthit.nrt.ui.log

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.programme.GapNote
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.Duration

/**
 * The log sheet's spacing note (PLAN §6.3), in the label-limit note's calm box: "Your gap goal
 * is 1 hour 30 minutes. The last dose was 55 minutes ago." It only says so, and never stops the
 * dose being logged. [isNow] when the dose is being logged as now, so the dose before it was
 * "ago" rather than at a time.
 */
@Composable
internal fun SpacingNote(note: GapNote, isNow: Boolean, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    val resources = LocalResources.current
    val goal = resources.durationWords(Duration.ofMinutes(note.goalMinutes.toLong()))
    val apart = resources.durationWords(note.apart)
    val at = rememberTimeFormatter().formatAt(note.other.occurredAt, note.other.zone)
    val text = when {
        note.otherCameFirst && isNow -> resources.getString(R.string.goals_gap_note_now, goal, apart)
        note.otherCameFirst -> resources.getString(R.string.goals_gap_note_after, goal, apart, at)
        else -> resources.getString(R.string.goals_gap_note_before, goal, apart, at)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            // It comes in when a time is picked, so it's said then.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.text2)
        Text(text, style = LastHit.type.label, color = colors.text, modifier = Modifier.weight(1f).padding(top = 1.dp))
    }
}

/** "1 hour 30 minutes", "55 minutes", "2 hours", or "under a minute", for a sentence. */
private fun Resources.durationWords(duration: Duration): String {
    val minutes = duration.toMinutes()
    if (minutes < 1) return getString(R.string.goals_under_a_minute)
    val hours = (minutes / 60).toInt()
    val rest = (minutes % 60).toInt()
    val hourWords = getQuantityString(R.plurals.spoken_hours, hours, hours)
    val minuteWords = getQuantityString(R.plurals.spoken_minutes, rest, rest)
    return when {
        hours == 0 -> minuteWords
        rest == 0 -> hourWords
        else -> getString(R.string.goals_hours_minutes, hourWords, minuteWords)
    }
}

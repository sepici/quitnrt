package app.thelasthit.nrt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.dose.Dose
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.ui.absorbed.BesideTheAmount
import app.thelasthit.nrt.ui.absorbed.RowEstimate
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.plural
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.theme.LastHit
import java.time.format.TextStyle
import java.util.Locale

/**
 * One logged dose: the product (with the amount when it's more than one), the time with its
 * tags, and the mg. A dose after midnight that counts toward the evening before names its day:
 * "01:20 Friday". With [absorbed], the estimated absorbed amount sits under the mg, in the
 * secondary colour, while that setting is on. The name and time keep the lines they have
 * without it: where it would crowd them, it takes a line of its own.
 */
@Composable
fun EntryRow(
    entry: Entry,
    name: String,
    tags: Map<String, TagEntity>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    absorbed: Dose? = null,
) {
    val clock = rememberTimeFormatter().formatAt(entry.occurredAt, entry.zone)
    val time = if (entry.countsTowardEveningBefore) {
        stringResource(R.string.entry_time_next_day, clock, entry.calendarDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK))
    } else {
        clock
    }
    val title = if (entry.quantity > 1) {
        stringResource(R.string.entry_title_quantity, name, pluralStringResource(entry.unit.plural, entry.quantity, entry.quantity))
    } else {
        name
    }
    val tagNames = entry.tagKeys.map { key -> tags[key]?.let { tagText(it) } ?: tagText(key) }.sorted()
    val detail = (listOf(time) + tagNames).joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(entry.form.icon, dose = true)
        val details = @Composable { columnModifier: Modifier ->
            Column(columnModifier) {
                Text(
                    title,
                    style = LastHit.type.title.copy(fontWeight = FontWeight.Bold),
                    color = LastHit.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = LastHit.colors.text2)
            }
        }
        val amount = @Composable {
            Text(
                stringResource(R.string.dose_mg, entry.total.mgText()),
                style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.015).em),
                color = LastHit.colors.text,
                maxLines = 1,
            )
        }
        if (absorbed == null) {
            details(Modifier.weight(1f))
            amount()
        } else {
            BesideTheAmount(
                gap = 14.dp,
                start = { details(Modifier) },
                amount = amount,
                estimate = { RowEstimate(absorbed) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

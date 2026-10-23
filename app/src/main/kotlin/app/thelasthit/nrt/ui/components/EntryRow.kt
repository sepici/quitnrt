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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.db.TagEntity
import app.thelasthit.nrt.domain.log.Entry
import app.thelasthit.nrt.ui.format.formatAt
import app.thelasthit.nrt.ui.format.icon
import app.thelasthit.nrt.ui.format.mgText
import app.thelasthit.nrt.ui.format.rememberTimeFormatter
import app.thelasthit.nrt.ui.format.tagText
import app.thelasthit.nrt.ui.theme.LastHit

/** One logged dose: the product, the time with its tags, and the mg. */
@Composable
fun EntryRow(
    entry: Entry,
    name: String,
    tags: Map<String, TagEntity>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val time = rememberTimeFormatter().formatAt(entry.occurredAt, entry.zone)
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
        IconTile(entry.form.icon)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold),
                color = LastHit.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = LastHit.colors.text2)
        }
        Text(
            stringResource(R.string.dose_mg, entry.total.mgText()),
            style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.015).em),
            color = LastHit.colors.text,
            maxLines = 1,
        )
    }
}

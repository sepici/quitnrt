package app.thelasthit.nrt.ui.plan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.components.PrimaryButton
import app.thelasthit.nrt.ui.theme.LastHit

// The Plan tab's own parts, from plan.css. Each page puts them together.

/** The tab's top bar: "Plan", with where the programme is beside it. */
@Composable
internal fun PlanTopBar(date: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.nav_plan),
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
                color = LastHit.colors.text,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            if (date != null) {
                Text(
                    date,
                    style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = LastHit.colors.text2,
                    modifier = Modifier.padding(start = 10.dp).alignByBaseline(),
                )
            }
        }
    }
}

/**
 * A page under the tab: the back bar with its title, then the page. [footer] sits at the end of
 * the page, pushed to the bottom of the screen when the page is short. [bar] stays fixed below
 * it all, for a page whose action is always in reach.
 */
@Composable
internal fun PlanPage(
    title: String,
    onBack: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    bar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LastHit.colors
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.plan_back), tint = colors.text)
            }
            Text(
                title,
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val screen = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.heightIn(min = screen), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(content = content)
                    if (footer != null) {
                        Column(
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            content = footer,
                        )
                    }
                }
            }
        }
        if (bar != null) {
            HorizontalDivider(color = colors.line)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp), content = bar)
        }
    }
}

/** The line under a page's back bar that says what it's for. */
@Composable
internal fun PageIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        color = LastHit.colors.text2,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
    )
}

/** A section's heading, at 24 sp. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
        color = LastHit.colors.text,
        modifier = modifier.semantics { heading() },
    )
}

/** A small bold line over a figure: "Your last 7 days". */
@Composable
internal fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LastHit.type.label.copy(fontWeight = FontWeight.Bold), color = LastHit.colors.text2, modifier = modifier)
}

/** A big number with what it counts beside it: "35 mg on a usual day". */
@Composable
internal fun Fig(number: String, label: String, modifier: Modifier = Modifier, description: String = "$number $label") {
    Row(
        modifier
            .padding(top = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(number, style = LastHit.type.display.copy(lineHeight = 48.sp), color = LastHit.colors.text, modifier = Modifier.alignByBaseline())
        Text(
            label,
            style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp),
            color = LastHit.colors.text2,
            modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
        )
    }
}

/** A mix said once in words, in bold: "8 pieces of 4 mg gum and 3 sprays". */
@Composable
internal fun MixLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.012).em),
        color = LastHit.colors.text,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** A quiet line of detail. */
@Composable
internal fun Meta(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, style = LastHit.type.label, color = LastHit.colors.text2, modifier = modifier)
}

@Composable
internal fun Meta(text: String, modifier: Modifier = Modifier) = Meta(AnnotatedString(text), modifier)

/** A small hint under a field. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LastHit.type.caption, color = LastHit.colors.text2, modifier = modifier)
}

/** Body text, as the plan's prose sets it. */
@Composable
internal fun Prose(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LastHit.type.body, color = LastHit.colors.text, modifier = modifier)
}

/** A calm note in the sunk tint, with a bold title and one or more paragraphs. Never green or marigold. */
@Composable
internal fun PlanNote(title: String, paragraphs: List<String>, modifier: Modifier = Modifier, @DrawableRes icon: Int = R.drawable.ic_info) {
    val colors = LastHit.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.text2, modifier = Modifier.padding(top = 1.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                Text(paragraphs.first(), style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = colors.text)
            }
            paragraphs.drop(1).forEach { Text(it, style = LastHit.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = colors.text) }
        }
    }
}

/** The outlined button for a second way: "Say what a usual day is instead". */
@Composable
internal fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.5.dp, colors.lineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (icon != null) 16.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Text(text, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
    }
}

/** The page's main action, with a small note under it. */
@Composable
internal fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PrimaryButton(onClick = onClick, enabled = enabled, modifier = modifier) { content ->
        Text(text, style = LastHit.type.title.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = content)
    }
}

@Composable
internal fun FootNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.caption,
        color = LastHit.colors.text2,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** One of the plan's rows that opens something: a tile, a title and line, and a chevron. */
@Composable
internal fun PlanRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    first: Boolean,
    onClick: () -> Unit,
) {
    val colors = LastHit.colors
    if (!first) HorizontalDivider(color = colors.line)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.iconTile), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onIconTile, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
            if (detail != null) Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        }
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = colors.text3, modifier = Modifier.padding(end = 4.dp))
    }
}

/** Rows ruled top and bottom. */
@Composable
internal fun PlanRows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = LastHit.colors.line)
        content()
        HorizontalDivider(color = LastHit.colors.line)
    }
}

/** A round button for a stepper in a list: outlined, and faint when it can't go further. */
@Composable
internal fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit, size: Dp = 48.dp) {
    val colors = LastHit.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) colors.surface else colors.bg)
            .border(1.5.dp, if (enabled) colors.lineStrong else colors.line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (enabled) colors.text else colors.disabledText, modifier = Modifier.size(20.dp))
    }
}

/**
 * Label and value on one line, ruled between: "Weeks  12, plus 1 of overtime". A label takes at
 * most a little over half the row, so at a large font it wraps rather than squeezing the value.
 */
@Composable
internal fun SummaryRows(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = maxWidth * 0.55f
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = colors.line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        label,
                        style = LastHit.type.body.copy(lineHeight = 22.sp),
                        color = colors.text2,
                        modifier = Modifier.widthIn(max = labelWidth).alignByBaseline(),
                    )
                    Text(
                        value,
                        style = LastHit.type.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.Bold, lineBreak = LineBreak.Heading),
                        color = colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                }
            }
        }
    }
}

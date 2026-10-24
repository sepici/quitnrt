package app.thelasthit.nrt.ui.advice

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.domain.advice.BreathingProgress
import app.thelasthit.nrt.ui.theme.LastHit

// Parts the advice pages and the two timers share, from advice.css and screens.css.

/** The back bar: a back arrow, an optional title, and whatever goes on the right. */
@Composable
fun AdviceBackBar(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.advice_back), tint = colors.text)
        }
        if (title != null) {
            Text(
                title,
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
                color = colors.text,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing()
    }
}

/** A scrolling page under a back bar, with room at the end. */
@Composable
fun AdvicePage(title: String?, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(LastHit.colors.bg)) {
        AdviceBackBar(title, onBack, trailing)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) { content() }
    }
}

/**
 * A timer's page: its top bar, a body that scrolls when it has to, and the buttons at the bottom,
 * as the mockups' tm-foot. A [centred] body sits in the middle of the space, as the running
 * breathing timer and the end frames do. The buttons stay in reach when the body is taller than
 * the screen, as it can be at a large font, and a rule along their top edge shows there's more.
 */
@Composable
fun TimerPage(
    top: @Composable () -> Unit,
    foot: @Composable ColumnScope.() -> Unit,
    centred: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LastHit.colors
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        top()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val height = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(
                    Modifier.fillMaxWidth().then(if (centred) Modifier.heightIn(min = height) else Modifier),
                    verticalArrangement = if (centred) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
                ) { content() }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (scroll.canScrollForward) {
                        val line = 1.dp.toPx()
                        drawLine(colors.line, Offset(0f, line / 2), Offset(size.width, line / 2), strokeWidth = line)
                    }
                }
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { foot() }
    }
}

/** A running timer's top bar: a cross that closes it, the timer's name and, on the right, the time left. */
@Composable
fun TimerTopBar(title: String, closeDescription: String, onClose: () -> Unit, trailing: String? = null, trailingDescription: String? = null) {
    val colors = LastHit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = closeDescription, tint = colors.text)
        }
        Text(
            title,
            style = LastHit.type.title.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
            color = colors.text,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        if (trailing != null) {
            Text(
                trailing,
                style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text2,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .then(if (trailingDescription != null) Modifier.semantics { contentDescription = trailingDescription } else Modifier),
            )
        }
    }
}

/** An outlined button, as the mockups' btn-line: a secondary action. */
@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.5.dp, colors.lineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        Text(text, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
    }
}

/** The text on a primary button, with its icon. */
@Composable
fun PrimaryLabel(text: String, contentColour: Color, @DrawableRes icon: Int? = null) {
    if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = contentColour, modifier = Modifier.size(24.dp))
    Text(text, style = LastHit.type.title.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold), color = contentColour)
}

/** A small grey label beside a row's title, such as "Timer". */
@Composable
fun Tagline(text: String) {
    Text(
        text,
        style = LastHit.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        color = LastHit.colors.text2,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LastHit.colors.sunk)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * A strategy's row: its title, a "Timer" label when it opens one, a line under it and a
 * chevron. Rows after the first are ruled above.
 */
@Composable
fun StrategyRow(
    title: String,
    sub: AnnotatedString?,
    first: Boolean,
    onClick: () -> Unit,
    timer: Boolean = false,
    @DrawableRes lead: Int? = null,
    startPadding: Dp = 20.dp,
    subColour: Color = LastHit.colors.text2,
) {
    val colors = LastHit.colors
    if (!first) HorizontalDivider(color = colors.line)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = startPadding, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lead != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.iconTile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(lead), contentDescription = null, tint = colors.onIconTile, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(title, style = LastHit.type.title.copy(fontWeight = FontWeight.Bold), color = colors.text)
                if (timer) Tagline(stringResource(R.string.advice_timer_tag))
            }
            if (sub != null) {
                Text(sub, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = subColour)
            }
        }
        Icon(
            painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = colors.text3,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** A grey section label over a list, as the mockups' shd. */
@Composable
fun ListHeading(text: String, top: Dp = 28.dp) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.Bold),
        color = LastHit.colors.text2,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = top, bottom = 6.dp)
            .semantics { heading() },
    )
}

/** A big section title with a line under it, as the mockups' adv-sec. */
@Composable
fun SectionTitle(title: String, body: String, top: Dp = 36.dp) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = top)) {
        Text(
            title,
            style = LastHit.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.025).em),
            color = LastHit.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = LastHit.type.label,
            color = LastHit.colors.text2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** A line under a page's title that says what it's for, as the mockups' intro. */
@Composable
fun AdviceIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        color = LastHit.colors.text2,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
    )
}

/** "From your logs:" in bold, then [rest]. */
@Composable
fun fromLogs(rest: String): AnnotatedString {
    val lead = stringResource(R.string.advice_from_logs)
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
        append(" ")
        append(rest)
    }
}

/**
 * The breathing badges' line: how many sessions, when the next badge comes, and a row of pips,
 * marigold for the sessions done, as the game layer. With no [title], the end of a session,
 * whose own title already gives the count.
 */
@Composable
fun BadgeLine(title: String?, body: String, progress: BreathingProgress, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.sunk)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        if (title != null) {
            Text(title, style = LastHit.type.title.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold), color = colors.text)
        }
        Text(body, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
        if (progress.pips > 0) Pips(progress, Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Pips(progress: BreathingProgress, modifier: Modifier = Modifier) {
    val colors = LastHit.colors
    FlowRow(
        modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(progress.pips) { index ->
            val filled = index < progress.filled
            Box(
                Modifier
                    .size(14.dp)
                    .drawBehind {
                        val edge = 1.5.dp.toPx()
                        if (filled) drawRect(colors.marigold)
                        drawRect(
                            if (filled) colors.marigoldEdge else colors.lineStrong,
                            topLeft = Offset(edge / 2, edge / 2),
                            size = Size(size.width - edge, size.height - edge),
                            style = Stroke(edge),
                        )
                    },
            )
        }
    }
}

/** A rounded card on the page, as the 4 Ds and the "From your logs" card. */
@Composable
fun AdviceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LastHit.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (colors.isDark) colors.lineStrong else colors.line, shape),
        content = content,
    )
}


/**
 * Fits a text whose [lineHeight] is less than its font's own into line boxes that tall, as CSS
 * does, with the difference taken evenly from above and below. Android keeps a line at least as
 * tall as its font, whatever the line height or LineHeightStyle, so a big number would otherwise
 * stand taller than in the mockups and push everything under it down. Only the first line's top
 * and the last line's bottom are trimmed, so lines that wrap never overlap, and baselines move
 * with the text.
 */
fun Modifier.cssLineHeight(lineHeight: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val first = placeable[FirstBaseline]
    val last = placeable[LastBaseline]
    // The font's own height for one line: from the top to the first baseline, and from the last baseline to the bottom.
    val ownLine = if (first == AlignmentLine.Unspecified || last == AlignmentLine.Unspecified) {
        placeable.height
    } else {
        first + placeable.height - last
    }
    val trim = ((ownLine - lineHeight.roundToPx()) / 2).coerceAtLeast(0)
    layout(placeable.width, placeable.height - 2 * trim) { placeable.place(0, -trim) }
}

/** An underlined link, at least 48 dp each way to tap, as the mockups' link. */
@Composable
fun AdviceLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LastHit.type.title.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
            color = LastHit.colors.link,
        )
    }
}

/** Rows ruled above and below, as the mockups' slist. */
@Composable
fun Ruled(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = LastHit.colors.line)
        content()
        HorizontalDivider(color = LastHit.colors.line)
    }
}

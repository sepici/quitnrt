package app.thelasthit.nrt.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.thelasthit.nrt.R
import app.thelasthit.nrt.ui.theme.LastHit

/** A Settings page: a back bar with the title, then a scrolling body. */
@Composable
fun SettingsPage(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(LastHit.colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = backDescription, tint = LastHit.colors.text)
            }
            Text(
                title,
                style = LastHit.type.headline.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.03).em),
                color = LastHit.colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            content()
        }
    }
}

/** The line under the back bar that says what the page is for. */
@Composable
fun Intro(text: String) {
    Text(
        text,
        style = LastHit.type.label.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        color = LastHit.colors.text2,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
    )
}

@Composable
fun SectionHeading(text: String) {
    Text(
        text,
        style = LastHit.type.label.copy(fontWeight = FontWeight.ExtraBold),
        color = LastHit.colors.text2,
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

/** A small note under a list. */
@Composable
fun FootNote(text: String) {
    Text(
        text,
        style = LastHit.type.caption,
        color = LastHit.colors.text2,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
    )
}

/** Rows ruled top and bottom, and between each other. */
@Composable
fun RowList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = LastHit.colors.line)
        content()
        HorizontalDivider(color = LastHit.colors.line)
    }
}

/**
 * One Settings row: a title and detail, with an optional icon tile, a value on the right and a
 * chevron when it opens a page. [trailing] replaces the chevron, for a link such as "Hide".
 */
@Composable
fun SettingsRow(
    title: String,
    detail: String?,
    first: Boolean,
    onClick: (() -> Unit)?,
    @DrawableRes icon: Int? = null,
    muted: Boolean = false,
    value: String? = null,
    chevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = LastHit.colors
    if (!first) HorizontalDivider(color = colors.line)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (muted) colors.sunk else colors.iconTile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (muted) colors.text3 else colors.onIconTile,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = LastHit.type.title.copy(fontWeight = FontWeight.ExtraBold),
                color = if (muted) colors.text2 else colors.text,
            )
            if (detail != null) {
                Text(detail, style = LastHit.type.label.copy(fontWeight = FontWeight.Normal), color = colors.text2)
            }
        }
        if (value != null) {
            Text(value, style = LastHit.type.label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.text2)
        }
        if (trailing != null) {
            trailing()
        } else if (chevron) {
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = null,
                tint = colors.text3,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/** An underlined link button, at least 48 dp to tap. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, description: String? = null) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LastHit.type.title.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, textDecoration = TextDecoration.Underline),
            color = LastHit.colors.link,
        )
    }
}

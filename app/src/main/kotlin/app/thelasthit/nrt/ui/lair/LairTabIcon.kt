package app.thelasthit.nrt.ui.lair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.thelasthit.nrt.R
import app.thelasthit.nrt.data.DataGraph
import app.thelasthit.nrt.ui.graphViewModel
import app.thelasthit.nrt.ui.theme.LastHit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LairTabViewModel(graph: DataGraph) : ViewModel() {
    /** Hits banked and ready to spend on the demon: 0 before a plan starts. */
    val ready: StateFlow<Int> = graph.game.demon.map { it?.balance?.banked ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/** The hits ready to spend, for the Lair tab's badge. */
@Composable
fun rememberLairHitsReady(viewModel: LairTabViewModel = graphViewModel { LairTabViewModel(it) }): Int {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    return ready
}

/**
 * The Lair tab's label for a screen reader: "Lair, 1 hit ready" while there are hits to spend.
 * The tab bar reads its labels and not its icons, so the badge is said here.
 */
@Composable
fun lairTabSpoken(ready: Int): String? = if (ready > 0) pluralStringResource(R.plurals.nav_lair_hits_ready, ready, ready) else null

/**
 * The Lair tab's icon, with a marigold badge for the [ready] hits, as lair.html has it. The badge
 * sits on the icon's top right, ringed in the page colour so it stands off the tab's pill.
 */
@Composable
fun LairTabIcon(icon: Painter, ready: Int) {
    val colors = LastHit.colors
    Box {
        Icon(icon, contentDescription = null)
        if (ready > 0) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    // As the mockup: 10 dp right of the tab's middle, and just above the pill.
                    .offset(x = 20.dp, y = (-10).dp)
                    .background(colors.bg, RoundedCornerShape(11.dp))
                    .padding(2.dp)
                    .background(colors.marigold, RoundedCornerShape(9.dp))
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ready.toString(),
                    style = LastHit.type.caption.copy(fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.onMarigold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

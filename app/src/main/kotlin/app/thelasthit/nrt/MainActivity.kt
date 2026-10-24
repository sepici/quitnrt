package app.thelasthit.nrt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.thelasthit.nrt.link.LogLinks
import app.thelasthit.nrt.reminders.NoticePoster
import app.thelasthit.nrt.reminders.NoticeTarget
import app.thelasthit.nrt.ui.AppRoot
import app.thelasthit.nrt.ui.NoticeTargetViewModel
import app.thelasthit.nrt.ui.link.LinkViewModel
import app.thelasthit.nrt.ui.theme.LastHitTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val links: LinkViewModel by viewModels { LinkViewModel.Factory }
    private val notices: NoticeTargetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recreated after a rotation or with its saved state, the activity handled its intent the first time.
        if (savedInstanceState == null) open(intent)
        setContent {
            LastHitTheme {
                AppRoot()
            }
        }
        // Badges and gear are checked while the app is on screen: as it opens, and after each
        // write. What came in while it was away is found when it comes back.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { (application as LastHitApp).graph.unlocks.follow() }
        }
    }

    /** A link or a tapped notice that arrives while the app is open, such as from a shortcut or an NFC sticker. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent?) {
        LogLinks.from(intent)?.let(links::open)
        // A weekly result opens the Plan tab and a badge the lair's shelf (AppShell).
        NoticeTarget.from(intent)?.let(notices::open)
        // A reminder's Log button: its notification has done its job.
        NoticePoster.dismissFrom(this, intent)
    }
}

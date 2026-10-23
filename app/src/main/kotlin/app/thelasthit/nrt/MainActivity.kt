package app.thelasthit.nrt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.thelasthit.nrt.link.LogLinks
import app.thelasthit.nrt.ui.AppRoot
import app.thelasthit.nrt.ui.link.LinkViewModel
import app.thelasthit.nrt.ui.theme.LastHitTheme

class MainActivity : ComponentActivity() {

    private val links: LinkViewModel by viewModels { LinkViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recreated after a rotation or with its saved state, the activity handled its link the first time.
        if (savedInstanceState == null) openLink(intent)
        setContent {
            LastHitTheme {
                AppRoot()
            }
        }
    }

    /** A link that arrives while the app is open, such as from a shortcut or an NFC sticker. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent?) {
        LogLinks.from(intent)?.let(links::open)
    }
}

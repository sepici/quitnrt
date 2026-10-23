package app.thelasthit.nrt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.thelasthit.nrt.ui.AppRoot
import app.thelasthit.nrt.ui.theme.LastHitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LastHitTheme {
                AppRoot()
            }
        }
    }
}

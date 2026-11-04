package dev.schattauer.edgechameleon.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Shows one combination of mode and screen for the macrobenchmark, set by intent extras. */
class BenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val mode = BenchmarkMode.valueOf(intent.getStringExtra(ExtraMode) ?: "Content")
        val screen = BenchmarkScreen.valueOf(intent.getStringExtra(ExtraScreen) ?: "Static")
        setContent { BenchmarkApp(mode, screen) }
    }

    companion object {
        const val ExtraMode = "mode"
        const val ExtraScreen = "screen"
    }
}

package dev.schattauer.edgechameleon.sample

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.schattauer.edgechameleon.BarIcons
import dev.schattauer.edgechameleon.EdgeChameleonTuning
import dev.schattauer.edgechameleon.ExperimentalEdgeChameleonApi
import dev.schattauer.edgechameleon.SamplingSource
import dev.schattauer.edgechameleon.edgeChameleon
import dev.schattauer.edgechameleon.edgeChameleonHint

/** What the library does in a benchmark run. */
public enum class BenchmarkMode {
    /** No modifier at all, to measure what the app costs without the library. */
    Off,
    /** The content is drawn a second time into a few pixels. */
    Content,

    /** The same as [Content], without the longer pause while nothing changes, as before 0.3.0. */
    ContentNoBackoff,
    /** The bars are copied from the frame of the window. */
    Window,
    /** Content sampling, with hints on the solid bars of [BenchmarkScreen.AppBars]. */
    Hints,
}

/** What the benchmark screen shows. */
public enum class BenchmarkScreen {
    /** One colour that never changes. */
    Static,
    /**
     * One colour, and a spinner in the middle that redraws every frame without any change at the
     * bars.
     */
    Spinner,
    /** Dark and light bands that scroll behind both bars. */
    Bands,
    /** Solid top and bottom bars over scrolling bands, like an app with app bars. */
    AppBars,
}

/** The sample content for the benchmark, one combination of [mode] and [screen] at a time. */
@OptIn(ExperimentalEdgeChameleonApi::class)
@Composable
public fun BenchmarkApp(mode: BenchmarkMode, screen: BenchmarkScreen) {
    val library =
        when (mode) {
            BenchmarkMode.Off -> Modifier
            BenchmarkMode.Window ->
                Modifier.edgeChameleon(
                    tuning = EdgeChameleonTuning(samplingSource = SamplingSource.Window)
                )
            BenchmarkMode.ContentNoBackoff ->
                Modifier.edgeChameleon(
                    tuning =
                        EdgeChameleonTuning(
                            samplingSource = SamplingSource.Content,
                            maxIdleIntervalMillis = 100,
                        )
                )
            BenchmarkMode.Content,
            BenchmarkMode.Hints ->
                Modifier.edgeChameleon(
                    tuning = EdgeChameleonTuning(samplingSource = SamplingSource.Content)
                )
        }
    Box(Modifier.fillMaxSize().then(library)) {
        when (screen) {
            BenchmarkScreen.Static -> Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F5)))
            BenchmarkScreen.Spinner -> SpinnerContent()
            BenchmarkScreen.Bands -> BandsScreen()
            BenchmarkScreen.AppBars -> AppBarsContent(hinted = mode == BenchmarkMode.Hints)
        }
    }
}

@Composable
private fun SpinnerContent() {
    val angle by
        rememberInfiniteTransition()
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
            )
    Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        Box(
            Modifier.align(Alignment.Center)
                .size(96.dp)
                .graphicsLayer { rotationZ = angle }
                .background(Color(0xFFD32F2F))
        )
    }
}

@Composable
private fun AppBarsContent(hinted: Boolean) {
    val barColor = Color(0xFF1B1B1F)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth()
                .then(if (hinted) Modifier.edgeChameleonHint(BarIcons.Light) else Modifier)
                .background(barColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
        )
        Box(Modifier.weight(1f)) { BandsScreen() }
        Box(
            Modifier.fillMaxWidth()
                .then(if (hinted) Modifier.edgeChameleonHint(BarIcons.Light) else Modifier)
                .background(barColor)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(56.dp)
        )
    }
}

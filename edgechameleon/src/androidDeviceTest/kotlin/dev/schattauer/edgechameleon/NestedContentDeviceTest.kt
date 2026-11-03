package dev.schattauer.edgechameleon

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Waits for [condition] on the test thread, without the idle synchronisation of Espresso. */
internal fun awaitCondition(timeoutMillis: Long, condition: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < end) {
        if (condition()) return true
        Thread.sleep(PollMillis)
    }
    return condition()
}

private const val PollMillis = 20L

/**
 * Screens that swap inside layers of their own, as a navigation host does. The content behind the
 * bars then changes without the node of the modifier being drawn again.
 */
@OptIn(ExperimentalEdgeChameleonApi::class)
class NestedContentDeviceTest {
    private val state = EdgeChameleonState()
    private var screen by mutableStateOf(0)
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.setContent {
                Box(Modifier.fillMaxSize().edgeChameleon(state = state)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                    ) { current ->
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer {}
                                .background(if (current % 2 == 0) Color.White else Color.Black)
                        )
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun awaitStatusIcons(icons: BarIcons) {
        val reached = awaitCondition(TimeoutMillis) { state.statusBarIcons == icons }
        assertEquals(
            "Status bar icons after screen $screen",
            icons,
            if (reached) icons else state.statusBarIcons,
        )
    }

    @Test
    fun contentThatSwapsInsideLayersIsSampled() {
        awaitStatusIcons(BarIcons.Dark)
        repeat(3) {
            screen += 1
            awaitStatusIcons(BarIcons.Light)
            screen += 1
            awaitStatusIcons(BarIcons.Dark)
        }
    }

    private companion object {
        const val TimeoutMillis = 5_000L
    }
}

/** A reader must never leave the renderer of the app waiting, however its reads end. */
class LayerReaderDeviceTest {
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun readsStillWorkAfterManyReadsWereGivenUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        var context: GraphicsContext? = null
        var density: Density? = null
        var direction: LayoutDirection? = null
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                context = LocalGraphicsContext.current
                density = LocalDensity.current
                direction = LocalLayoutDirection.current
                Box(Modifier.fillMaxSize())
            }
        }
        assertEquals(true, awaitCondition(TimeoutMillis) { context != null && density != null })

        val graphicsContext = checkNotNull(context)
        val currentDensity = checkNotNull(density)
        val currentDirection = checkNotNull(direction)
        val reader = createLayerReader()
        var failures = 0
        var slowest = 0L
        var firstFailure = ""
        scenario.onActivity {
            val layer = graphicsContext.createGraphicsLayer()
            runBlocking {
                layer.record(currentDensity, currentDirection, IntSize(Width, Height)) {
                    drawRect(Color(0xFF336699))
                }
                val pixels = IntArray(Width * Height)
                repeat(Rounds) {
                    // Gives up before the frame can arrive, as a busy renderer makes it do
                    withTimeoutOrNull(1) {
                        reader.read(layer, Width, Height, pixels, currentDensity, currentDirection)
                    }
                    // The reads of the library are at least a sample interval apart
                    Thread.sleep(SpacingMillis)
                    val start = System.currentTimeMillis()
                    val ok =
                        reader.read(layer, Width, Height, pixels, currentDensity, currentDirection)
                    slowest = maxOf(slowest, System.currentTimeMillis() - start)
                    if (!ok || pixels[0] != 0xFF336699.toInt()) {
                        if (failures == 0) {
                            firstFailure =
                                "round $it ok=$ok pixel=${pixels[0].toUInt().toString(16)}"
                        }
                        failures++
                    }
                }
            }
            reader.release()
            graphicsContext.releaseGraphicsLayer(layer)
        }

        assertEquals("$failures of $Rounds reads failed, first: $firstFailure", 0, failures)
        assertEquals("A read took $slowest ms", true, slowest < SlowMillis)
    }

    private companion object {
        const val TimeoutMillis = 5_000L
        const val Width = 16
        const val Height = 2
        const val Rounds = 40
        const val SlowMillis = 400L
        const val SpacingMillis = 50L
    }
}

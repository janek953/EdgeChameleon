package dev.schattauer.edgechameleon

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The same results as with the content of the modifier, read from the frame of the window. */
@OptIn(ExperimentalEdgeChameleonApi::class)
class WindowSourceDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = EdgeChameleonState()
    private var background by mutableStateOf(Color.White)
    private var topBand by mutableStateOf<Color?>(null)

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            Box(
                Modifier.edgeChameleon(
                        tuning = EdgeChameleonTuning(samplingSource = SamplingSource.Window),
                        navigationBar = false,
                        state = state,
                    )
                    .fillMaxSize()
                    .background(background)
            ) {
                topBand?.let { Box(Modifier.fillMaxWidth().height(200.dp).background(it)) }
            }
        }
    }

    private fun awaitStatusIcons(icons: BarIcons?) {
        rule.waitUntil(TimeoutMillis) { state.statusBarIcons == icons }
    }

    @Test
    fun whiteContentGetsDarkIconsAndDarkContentGetsLightIcons() {
        awaitStatusIcons(BarIcons.Dark)
        background = Color.Black
        awaitStatusIcons(BarIcons.Light)
        background = Color.White
        awaitStatusIcons(BarIcons.Dark)
        assertTrue("Nothing was read", state.sampleCount > 0)
    }

    @Test
    fun aDarkBandBehindTheStatusBarGetsLightIcons() {
        awaitStatusIcons(BarIcons.Dark)
        topBand = Color(0xFF101418)
        awaitStatusIcons(BarIcons.Light)
        topBand = null
        awaitStatusIcons(BarIcons.Dark)
    }

    @Test
    fun aStaticScreenCausesNoSamples() {
        awaitStatusIcons(BarIcons.Dark)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1_000)
        Thread.sleep(1_000)
        rule.waitForIdle()
        val samples = state.sampleCount
        Thread.sleep(1_000)
        assertEquals(samples, state.sampleCount)
    }

    private companion object {
        const val TimeoutMillis = 10_000L
    }
}

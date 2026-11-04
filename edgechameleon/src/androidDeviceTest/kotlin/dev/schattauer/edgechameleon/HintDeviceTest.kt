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
import org.junit.Rule
import org.junit.Test

/** Hinted bars need no pixels, so the screen behind them can change without any readback. */
@OptIn(ExperimentalEdgeChameleonApi::class)
class HintDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = EdgeChameleonState()
    private var hint by mutableStateOf<BarIcons?>(BarIcons.Light)
    private var barColor by mutableStateOf(Color.Black)
    private var barWidth by mutableStateOf(1f)
    private var content by mutableStateOf(Color.White)

    /** Starts the content with whatever the test set before. */
    private fun start() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            Box(
                Modifier.edgeChameleon(navigationBar = false, state = state)
                    .fillMaxSize()
                    .background(content)
            ) {
                Box(
                    Modifier.fillMaxWidth(barWidth)
                        .height(200.dp)
                        .edgeChameleonHint(hint)
                        .background(barColor)
                )
            }
        }
    }

    private fun awaitStatusIcons(icons: BarIcons?) {
        rule.waitUntil(TimeoutMillis) { state.statusBarIcons == icons }
    }

    /** Waits longer than a sample takes, on the clock of the test as well as on the real one. */
    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(SettleMillis)
        Thread.sleep(SettleMillis)
        rule.waitForIdle()
    }

    @Test
    fun aHintedBarIsDecidedWithoutAnyReadback() {
        start()
        awaitStatusIcons(BarIcons.Light)
        settle()
        assertEquals(0L, state.sampleCount)

        repeat(6) { step ->
            content = if (step % 2 == 0) Color.Black else Color.White
            settle()
        }
        assertEquals(0L, state.sampleCount)
        assertEquals(BarIcons.Light, state.statusBarIcons)
    }

    @Test
    fun theHintWinsOverWhatTheBarShows() {
        barColor = Color.White
        start()
        awaitStatusIcons(BarIcons.Light)
        settle()
        assertEquals(0L, state.sampleCount)
    }

    @Test
    fun removingTheHintBringsBackSampling() {
        start()
        awaitStatusIcons(BarIcons.Light)
        hint = null
        barColor = Color.White
        awaitStatusIcons(BarIcons.Dark)
        assertTrue("No sample after the hint went", state.sampleCount > 0)
    }

    @Test
    fun aHintThatComesLaterIsUsedInsteadOfSampling() {
        hint = null
        barColor = Color.White
        start()
        awaitStatusIcons(BarIcons.Dark)
        settle()
        val samples = state.sampleCount
        assertTrue("The white bar was not sampled", samples > 0)

        hint = BarIcons.Light
        awaitStatusIcons(BarIcons.Light)
        settle()
        content = Color.Black
        settle()
        assertEquals(samples, state.sampleCount)
    }

    @Test
    fun aHintThatDoesNotCoverTheBarIsNotEnough() {
        // The hint lies about a bar that is mostly black, and only covers three quarters of it
        hint = BarIcons.Dark
        barWidth = 0.75f
        start()
        awaitStatusIcons(BarIcons.Light)
        assertTrue("The bar was not sampled", state.sampleCount > 0)
    }

    @Test
    fun aHintBelongsToTheClosestModifierOnly() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        val outer = EdgeChameleonState()
        val inner = EdgeChameleonState()
        var hinted by mutableStateOf(true)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            Box(
                Modifier.edgeChameleon(navigationBar = false, state = outer)
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                Box(
                    Modifier.edgeChameleon(navigationBar = false, state = inner)
                        .fillMaxWidth()
                        .height(200.dp)
                        .then(if (hinted) Modifier.edgeChameleonHint(BarIcons.Light) else Modifier)
                )
            }
        }
        rule.waitUntil(TimeoutMillis) { outer.statusBarIcons == BarIcons.Light }
        settle()
        // The outer modifier has no hints of its own, so it reads its pixels, and the inner one
        // does not
        assertTrue("The outer modifier did not sample", outer.sampleCount > 0)
        assertEquals(0L, inner.sampleCount)

        // The hint goes, and must not be left behind in either modifier
        hinted = false
        rule.waitUntil(TimeoutMillis) { inner.sampleCount > 0 }
        settle()
        assertEquals(BarIcons.Light, outer.statusBarIcons)
    }

    private companion object {
        const val TimeoutMillis = 10_000L
        const val SettleMillis = 1_000L
    }
}

package dev.schattauer.edgechameleon

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The sampling reads from the renderer that draws the app, so it must never hold that up. */
@OptIn(ExperimentalEdgeChameleonApi::class)
class SamplingLoadDeviceTest {
    private val state = EdgeChameleonState()
    private lateinit var scenario: ActivityScenario<ComponentActivity>

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun constantSamplingNeverBlocksTheFramesOfTheApp() {
        runLoad(SamplingSource.Content)
    }

    @Test
    fun constantWindowSamplingNeverBlocksTheFramesOfTheApp() {
        runLoad(SamplingSource.Window)
    }

    private fun runLoad(source: SamplingSource) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val slowestFrameMillis = AtomicLong()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.window.addOnFrameMetricsAvailableListener(
                { _, metrics, _ ->
                    val millis = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / NanosPerMilli
                    slowestFrameMillis.accumulateAndGet(millis, ::maxOf)
                },
                Handler(Looper.getMainLooper()),
            )
            activity.setContent {
                val color by
                    rememberInfiniteTransition()
                        .animateColor(
                            initialValue = Color.Black,
                            targetValue = Color.White,
                            animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
                        )
                Box(
                    Modifier.fillMaxSize()
                        .edgeChameleon(
                            tuning =
                                EdgeChameleonTuning(
                                    maxSamplesPerSecond = 60,
                                    samplingSource = source,
                                ),
                            state = state,
                        )
                        .background(color)
                )
            }
        }

        Thread.sleep(RunMillis)

        val samples = state.sampleCount
        val slowest = slowestFrameMillis.get()
        assertTrue("Only $samples samples in ${RunMillis / 1000} s", samples > MinSamples)
        assertTrue("A frame took $slowest ms", slowest < SlowFrameMillis)
    }

    private companion object {
        const val RunMillis = 6_000L
        const val MinSamples = 100
        const val SlowFrameMillis = 1_000L
        const val NanosPerMilli = 1_000_000L
    }
}

package dev.schattauer.edgechameleon.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * What the library costs the frames of an app, for every way it can read the bars and for four
 * kinds of screens. Run it on a physical device, with `./gradlew
 * :benchmark:connectedBenchmarkAndroidTest`. Emulators give no usable numbers, and are refused
 * unless the `androidx.benchmark.suppressErrors=EMULATOR` argument is set for a smoke run.
 *
 * Compare the frame durations and overruns of [Mode.Off], the app without the library, with the
 * other modes. The trace sections show where the time of the library goes, and how many icon
 * decisions it made.
 */
@RunWith(Parameterized::class)
@OptIn(ExperimentalMetricApi::class)
class SamplingBenchmark(private val mode: String, private val screen: String) {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test
    fun frames() {
        rule.measureRepeated(
            packageName = Package,
            metrics =
                listOf(
                    FrameTimingMetric(),
                    TraceSectionMetric("EdgeChameleon.recordContent", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("EdgeChameleon.recordSample", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("EdgeChameleon.syncAndDraw", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("EdgeChameleon.applyIcons", TraceSectionMetric.Mode.Count),
                ),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = Iterations,
            setupBlock = { pressHome() },
        ) {
            val intent =
                Intent()
                    .setClassName(Package, "$Package.BenchmarkActivity")
                    .putExtra("mode", mode)
                    .putExtra("screen", screen)
            startActivityAndWait(intent)
            if (screen == "Bands" || screen == "AppBars") {
                // Flings up and down, so that dark and light bands pass behind both bars
                val x = device.displayWidth / 2
                val top = device.displayHeight / 4
                val bottom = device.displayHeight * 3 / 4
                repeat(Flings) {
                    device.swipe(x, bottom, x, top, FlingSteps)
                    device.swipe(x, top, x, bottom, FlingSteps)
                }
            } else {
                Thread.sleep(IdleMillis)
            }
        }
    }

    object Mode {
        const val Off = "Off"
    }

    companion object {
        init {
            // The trace processor runs on the device and is asked over localhost. A proxy on the
            // network of the device, such as a debugging proxy, would otherwise answer with a 503
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|[::1]")
        }

        private const val Package = "dev.schattauer.edgechameleon.sample"
        private const val Iterations = 5
        private const val Flings = 4
        private const val FlingSteps = 12
        private const val IdleMillis = 5_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}")
        fun parameters(): List<Array<String>> {
            val modes = listOf("Off", "Content", "ContentNoBackoff", "Window", "Hints")
            val screens = listOf("Static", "Spinner", "Bands", "AppBars")
            return modes.flatMap { mode -> screens.map { screen -> arrayOf(mode, screen) } }
        }
    }
}

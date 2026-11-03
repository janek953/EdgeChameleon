package dev.schattauer.edgechameleon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SampleThrottleTest {

    // Background coroutines only run when time is advanced explicitly, not through advanceUntilIdle
    private fun TestScope.advanceBy(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun noRequestsMeanNoSamples(): TestResult {
        return runTest {
            var samples = 0
            SampleThrottle(backgroundScope, 100) { samples++ }
            advanceBy(10_000)
            assertEquals(0, samples)
        }
    }

    @Test
    fun oneRequestSamplesOnceAndThenStops(): TestResult {
        return runTest {
            var samples = 0
            val throttle = SampleThrottle(backgroundScope, 100) { samples++ }
            throttle.request()
            advanceBy(1_000)
            assertEquals(1, samples)
            advanceBy(10_000)
            assertEquals(1, samples)
        }
    }

    @Test
    fun continuousRequestsStayWithinTheLimit(): TestResult {
        return runTest {
            var samples = 0
            val throttle = SampleThrottle(backgroundScope, 100) { samples++ }
            repeat(1000) {
                throttle.request()
                advanceBy(1)
            }
            // 1000 ms at 10 samples per second
            assertEquals(10, samples)
        }
    }

    @Test
    fun theLastChangeIsAlwaysSampled(): TestResult {
        return runTest {
            val seen = mutableListOf<Int>()
            var content = 0
            val throttle = SampleThrottle(backgroundScope, 100) { seen += content }
            content = 1
            throttle.request()
            advanceBy(50)
            // This change falls into the pause after the first sample
            content = 2
            throttle.request()
            advanceBy(1_000)
            assertEquals(listOf(1, 2), seen)
        }
    }

    @Test
    fun aRequestDuringASampleCausesAnotherSample(): TestResult {
        return runTest {
            var samples = 0
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100) {
                    samples++
                    if (samples == 1) throttle.request()
                }
            throttle.request()
            advanceBy(1_000)
            assertEquals(2, samples)
        }
    }

    @Test
    fun cancelStopsAPendingSample(): TestResult {
        return runTest {
            var samples = 0
            val throttle = SampleThrottle(backgroundScope, 100) { samples++ }
            throttle.request()
            advanceBy(5)
            throttle.cancel()
            advanceBy(1_000)
            assertEquals(0, samples)
        }
    }

    @Test
    fun aThrottleWorksAgainAfterACancel(): TestResult {
        return runTest {
            var samples = 0
            val throttle = SampleThrottle(backgroundScope, 100) { samples++ }
            throttle.request()
            advanceBy(5)
            throttle.cancel()
            throttle.request()
            advanceBy(1_000)
            assertEquals(1, samples)
        }
    }
}

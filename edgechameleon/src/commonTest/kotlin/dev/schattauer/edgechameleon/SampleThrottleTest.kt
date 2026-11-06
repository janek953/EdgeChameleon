package dev.schattauer.edgechameleon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SampleThrottleTest {

    // Background coroutines only run when time is advanced explicitly, not through advanceUntilIdle
    private fun TestScope.advanceBy(millis: Long) {
        advanceTimeBy(millis.milliseconds)
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

    @Test
    fun unchangedSamplesDoubleThePauseUpToTheLimit(): TestResult {
        return runTest {
            val times = mutableListOf<Long>()
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 400) {
                    times += currentTime
                    throttle.reportResult(changed = false)
                }
            repeat(3_000) {
                throttle.request()
                advanceBy(1)
            }
            val pauses = times.zipWithNext { first, second -> second - first }
            // The pause after a sample, with the wait for the frame, is the doubled interval
            assertEquals(listOf(200L, 400L), pauses.take(2))
            assertEquals(true, pauses.drop(2).all { it == 400L })
        }
    }

    @Test
    fun aChangeBringsBackTheFastRate(): TestResult {
        return runTest {
            val times = mutableListOf<Long>()
            var changed = false
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 400) {
                    times += currentTime
                    throttle.reportResult(changed)
                }
            repeat(1_500) {
                throttle.request()
                advanceBy(1)
            }
            val slow = times.size
            changed = true
            repeat(1_000) {
                throttle.request()
                advanceBy(1)
            }
            // The change is seen after the long pause, and the samples after it are fast again
            val after = times.drop(slow).zipWithNext { first, second -> second - first }
            assertEquals(true, after.drop(1).all { it == 100L })
        }
    }

    @Test
    fun resetBringsBackTheFastRate(): TestResult {
        return runTest {
            val times = mutableListOf<Long>()
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 400) {
                    times += currentTime
                    throttle.reportResult(changed = false)
                }
            repeat(1_500) {
                throttle.request()
                advanceBy(1)
            }
            throttle.reset()
            val before = times.size
            repeat(1_000) {
                throttle.request()
                advanceBy(1)
            }
            val pauses = times.drop(before - 1).zipWithNext { first, second -> second - first }
            // The pause after the first sample that follows the reset starts from the short
            // interval again, so it is 200 ms and not 400 ms
            assertEquals(200L, pauses[1])
        }
    }

    @Test
    fun resetEndsALongPauseWithinOneInterval(): TestResult {
        return runTest {
            val times = mutableListOf<Long>()
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 400) {
                    times += currentTime
                    throttle.reportResult(changed = false)
                }
            repeat(1_500) {
                throttle.request()
                advanceBy(1)
            }
            val before = times.size
            val resetAt = currentTime
            throttle.reset()
            advanceBy(1_000)
            // Without the reset, the long pause would still go on for 300 ms
            val wait = times[before] - resetAt
            assertTrue(wait <= 100L + 16L, "The sample came after $wait ms")
        }
    }

    @Test
    fun theLastChangeIsStillSampledAfterTheLongPause(): TestResult {
        return runTest {
            val seen = mutableListOf<Int>()
            var content = 0
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 400) {
                    seen += content
                    throttle.reportResult(changed = false)
                }
            repeat(3) {
                throttle.request()
                advanceBy(500)
            }
            content = 7
            throttle.request()
            advanceBy(1_000)
            assertEquals(7, seen.last())
        }
    }

    @Test
    fun aLimitBelowTheIntervalTurnsTheBackOffOff(): TestResult {
        return runTest {
            var samples = 0
            lateinit var throttle: SampleThrottle
            throttle =
                SampleThrottle(backgroundScope, 100, 50) {
                    samples++
                    throttle.reportResult(changed = false)
                }
            repeat(1000) {
                throttle.request()
                advanceBy(1)
            }
            assertEquals(10, samples)
        }
    }
}

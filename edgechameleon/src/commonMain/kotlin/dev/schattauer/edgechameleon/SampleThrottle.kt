package dev.schattauer.edgechameleon

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [sample] after requests, at most once per [intervalMillis]. No coroutine stays alive without
 * requests, so a static screen costs nothing. A request during a sample or the pause after it
 * causes one more sample, so the last change is always seen. Must be used from a single thread.
 *
 * While the samples keep reporting no change through [reportResult], the pause doubles after each
 * of them, up to [maxIdleIntervalMillis]. This is for content that redraws without changing what is
 * behind the bars, such as a spinner in the middle of the screen. The first change is then seen
 * after at most that pause, and brings the pause back to [intervalMillis]. A
 * [maxIdleIntervalMillis] that is not larger than [intervalMillis] turns the back-off off.
 */
internal class SampleThrottle(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    maxIdleIntervalMillis: Long = intervalMillis,
    private val sample: suspend () -> Unit,
) {
    private val maxIntervalMillis = maxOf(intervalMillis, maxIdleIntervalMillis)
    private var currentIntervalMillis = intervalMillis
    private var dirty = false
    private var job: Job? = null

    fun request() {
        dirty = true
        if (job?.isActive != true) {
            job = scope.launch { run() }
        }
    }

    /**
     * Tells the throttle whether the sample that is running saw a change, to slow down or speed up
     * the next one.
     */
    fun reportResult(changed: Boolean) {
        currentIntervalMillis =
            if (changed) intervalMillis else minOf(currentIntervalMillis * 2, maxIntervalMillis)
    }

    /**
     * Brings the pause back to [intervalMillis], for the next change to be seen quickly. A long
     * pause that has begun already ends within one [intervalMillis].
     */
    fun reset() {
        currentIntervalMillis = intervalMillis
    }

    /** Stops a sample that is waiting or running, and drops requests that were not served yet. */
    fun cancel() {
        dirty = false
        job?.cancel()
        job = null
        currentIntervalMillis = intervalMillis
    }

    private suspend fun run() {
        val settleMillis = minOf(SettleMillis, intervalMillis)
        while (dirty) {
            // The signal arrives before the frame is drawn, so give that frame time to finish
            delay(settleMillis.milliseconds)
            dirty = false
            sample()
            // In steps of the normal pause, so that a reset does not wait for a long pause to end
            var waited = settleMillis
            while (true) {
                val remaining = currentIntervalMillis - waited
                if (remaining <= 0L) break
                val step = minOf(intervalMillis, remaining)
                delay(step.milliseconds)
                waited += step
            }
        }
    }

    private companion object {
        const val SettleMillis = 16L
    }
}

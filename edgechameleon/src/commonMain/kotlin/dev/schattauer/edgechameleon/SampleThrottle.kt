package dev.schattauer.edgechameleon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [sample] after requests, at most once per [intervalMillis]. No coroutine stays alive without
 * requests, so a static screen costs nothing. A request during a sample or the pause after it
 * causes one more sample, so the last change is always seen. Must be used from a single thread.
 */
internal class SampleThrottle(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    private val sample: suspend () -> Unit,
) {
    private var dirty = false
    private var job: Job? = null

    fun request() {
        dirty = true
        if (job?.isActive != true) {
            job = scope.launch { run() }
        }
    }

    /** Stops a sample that is waiting or running, and drops requests that were not served yet. */
    fun cancel() {
        dirty = false
        job?.cancel()
        job = null
    }

    private suspend fun run() {
        val settleMillis = minOf(SettleMillis, intervalMillis)
        while (dirty) {
            // The signal arrives before the frame is drawn, so give that frame time to finish
            delay(settleMillis)
            dirty = false
            sample()
            delay(intervalMillis - settleMillis)
        }
    }

    private companion object {
        const val SettleMillis = 16L
    }
}

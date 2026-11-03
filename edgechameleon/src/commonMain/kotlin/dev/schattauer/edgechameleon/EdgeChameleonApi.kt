package dev.schattauer.edgechameleon

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** The colour of the system bar icons. Platforms only offer these two. */
public enum class BarIcons {
    Light,
    Dark,
}

/** Marks API that may still change, because it exposes how the library works internally. */
@RequiresOptIn(message = "This EdgeChameleon API may change without notice.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class ExperimentalEdgeChameleonApi

/**
 * Fine tuning for [edgeChameleon]. The defaults fit most apps.
 *
 * @param maxSamplesPerSecond upper limit for pixel readbacks, and so also for the icon reaction
 *   time.
 * @param cellCount number of cells each bar is reduced to.
 * @param contrastThreshold relative luminance below which light icons have the better contrast.
 * @param hysteresis factor that the current result moves the threshold by, to prevent flicker.
 * @param iconZoneWeight vote weight of the areas where icons usually sit: the outer quarters of the
 *   status bar, and the buttons of the navigation bar.
 */
@ExperimentalEdgeChameleonApi
@Immutable
public class EdgeChameleonTuning(
    public val maxSamplesPerSecond: Int = 10,
    public val cellCount: Int = 16,
    public val contrastThreshold: Float = 0.179f,
    public val hysteresis: Float = 1.25f,
    public val iconZoneWeight: Float = 3f,
) {
    init {
        require(maxSamplesPerSecond in 1..60) { "maxSamplesPerSecond must be in 1..60" }
        require(cellCount in 4..64) { "cellCount must be in 4..64" }
        require(contrastThreshold > 0f && contrastThreshold < 1f) {
            "contrastThreshold must be in (0, 1)"
        }
        require(hysteresis >= 1f) { "hysteresis must be at least 1" }
        require(iconZoneWeight > 0f) { "iconZoneWeight must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        return other is EdgeChameleonTuning &&
            maxSamplesPerSecond == other.maxSamplesPerSecond &&
            cellCount == other.cellCount &&
            contrastThreshold == other.contrastThreshold &&
            hysteresis == other.hysteresis &&
            iconZoneWeight == other.iconZoneWeight
    }

    override fun hashCode(): Int {
        var result = maxSamplesPerSecond
        result = 31 * result + cellCount
        result = 31 * result + contrastThreshold.hashCode()
        result = 31 * result + hysteresis.hashCode()
        result = 31 * result + iconZoneWeight.hashCode()
        return result
    }
}

/** Observable result of [edgeChameleon], for example to match app UI to the current icons. */
@Stable
public class EdgeChameleonState {
    /** The current status bar icons, or null if the library has not set them. */
    public var statusBarIcons: BarIcons? by mutableStateOf(null)
        internal set

    /** The current navigation bar icons, or null if the library has not set them. */
    public var navigationBarIcons: BarIcons? by mutableStateOf(null)
        internal set

    /**
     * Set to true to fill [statusBarCells] and [navigationBarCells]. Off by default, as it
     * allocates. Setting it to false empties both lists.
     */
    @ExperimentalEdgeChameleonApi
    public var collectCells: Boolean = false
        set(value) {
            field = value
            if (!value) {
                statusBarCells = emptyList()
                navigationBarCells = emptyList()
            }
        }

    /** The sampled status bar cells while [collectCells] is on. */
    @ExperimentalEdgeChameleonApi
    public var statusBarCells: List<Color> by mutableStateOf(emptyList())
        internal set

    /** The sampled navigation bar cells while [collectCells] is on. */
    @ExperimentalEdgeChameleonApi
    public var navigationBarCells: List<Color> by mutableStateOf(emptyList())
        internal set

    /**
     * Number of pixel readbacks so far. Not observable on purpose, so showing it causes no redraw.
     */
    @ExperimentalEdgeChameleonApi
    public var sampleCount: Long = 0L
        internal set
}

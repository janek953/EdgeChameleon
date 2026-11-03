package dev.schattauer.edgechameleon

import kotlin.math.pow

/**
 * Turns a row of downscaled cells into a tint decision. Every cell votes for the icon colour with
 * the better WCAG contrast against it, weighted by its zone, and the larger total weight wins.
 *
 * The previous result shifts the switch point by [EdgeChameleonTuning.hysteresis], so content close
 * to the threshold does not make the icons flicker. Transparent cells abstain, and ties keep the
 * previous result.
 */
internal object BarDecider {

    /** Cells with less opacity than this show the window background, which is not sampled. */
    private const val MinimumAlpha = 16

    private val srgbToLinear =
        FloatArray(256) { value ->
            val channel = value / 255.0
            (if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4))
                .toFloat()
        }

    fun luminance(argb: Int): Float {
        return 0.2126f * srgbToLinear[argb shr 16 and 0xFF] +
            0.7152f * srgbToLinear[argb shr 8 and 0xFF] +
            0.0722f * srgbToLinear[argb and 0xFF]
    }

    /**
     * Decides from the [count] cells that start at [start] in [pixels] and lie [stride] entries
     * apart. Returns [previous] when no cell has a vote.
     */
    fun decide(
        pixels: IntArray,
        start: Int,
        stride: Int,
        count: Int,
        weights: FloatArray,
        previous: BarIcons?,
        contrastThreshold: Float,
        hysteresis: Float,
    ): BarIcons? {
        val threshold =
            when (previous) {
                BarIcons.Light -> contrastThreshold * hysteresis
                BarIcons.Dark -> contrastThreshold / hysteresis
                null -> contrastThreshold
            }
        var lightVotes = 0f
        var darkVotes = 0f
        for (cell in 0 until count) {
            val argb = pixels[start + cell * stride]
            if (argb ushr 24 < MinimumAlpha) continue
            if (luminance(argb) < threshold) {
                lightVotes += weights[cell]
            } else {
                darkVotes += weights[cell]
            }
        }
        return when {
            lightVotes > darkVotes -> BarIcons.Light
            darkVotes > lightVotes -> BarIcons.Dark
            else -> previous
        }
    }

    /** Clock and system icons usually sit at the sides, so the outer quarters count more. */
    fun statusBarWeights(count: Int, iconZoneWeight: Float): FloatArray {
        val edgeCells = count / 4
        return FloatArray(count) { cell ->
            if (cell < edgeCells || cell >= count - edgeCells) iconZoneWeight else 1f
        }
    }

    /**
     * Unlike the status bar, the whole width of the navigation bar decides. Where the buttons sit
     * depends on the device, so no part of the bar counts more.
     */
    fun navigationBarWeights(count: Int): FloatArray {
        return FloatArray(count) { 1f }
    }
}

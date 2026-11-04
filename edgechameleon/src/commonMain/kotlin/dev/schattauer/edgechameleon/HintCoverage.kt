package dev.schattauer.edgechameleon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** The icons that the content in [bounds], in window coordinates, asks for. */
internal data class BarHint(val bounds: Rect, val icons: BarIcons)

/** Finds out whether hints decide a whole bar, so that its pixels do not have to be read. */
internal object HintCoverage {

    /**
     * Fills the first [cells] entries of [out] with the icons that [hints] give the cells of
     * [region], and returns whether every cell got some. The cells lie in a column if [isVertical],
     * and in a row if not. This is given and not worked out from [region], as the part of a bar
     * that a small modifier covers can be taller than wide, although the bar is not. A cell belongs
     * to a hint when its centre lies inside the bounds of the hint. Where hints overlap, the last
     * one wins, as that is the one that is usually drawn on top.
     */
    fun fill(
        out: Array<BarIcons?>,
        region: Rect,
        isVertical: Boolean,
        cells: Int,
        hints: List<BarHint>,
    ): Boolean {
        var covered = true
        for (cell in 0 until cells) {
            val fraction = (cell + 0.5f) / cells
            val centre =
                if (isVertical) {
                    Offset(region.center.x, region.top + region.height * fraction)
                } else {
                    Offset(region.left + region.width * fraction, region.center.y)
                }
            var icons: BarIcons? = null
            for (index in hints.indices.reversed()) {
                if (hints[index].bounds.contains(centre)) {
                    icons = hints[index].icons
                    break
                }
            }
            out[cell] = icons
            if (icons == null) covered = false
        }
        return covered
    }
}

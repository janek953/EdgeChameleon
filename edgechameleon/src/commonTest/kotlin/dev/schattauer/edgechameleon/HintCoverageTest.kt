package dev.schattauer.edgechameleon

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HintCoverageTest {
    private val statusBar = Rect(0f, 0f, 160f, 20f)
    private val sideBar = Rect(0f, 0f, 20f, 160f)

    /** The cells of the bar lie in a row. */
    private fun fill(region: Rect, vararg hints: BarHint): Pair<Boolean, Array<BarIcons?>> {
        return fill(region, isVertical = false, *hints)
    }

    /** The cells of the bar lie in a column. */
    private fun fillVertical(region: Rect, vararg hints: BarHint): Pair<Boolean, Array<BarIcons?>> {
        return fill(region, isVertical = true, *hints)
    }

    private fun fill(
        region: Rect,
        isVertical: Boolean,
        vararg hints: BarHint,
    ): Pair<Boolean, Array<BarIcons?>> {
        val out = arrayOfNulls<BarIcons>(4)
        return HintCoverage.fill(out, region, isVertical, 4, hints.toList()) to out
    }

    @Test
    fun aHintOverTheWholeBarCoversIt() {
        val (covered, icons) = fill(statusBar, BarHint(Rect(0f, 0f, 160f, 56f), BarIcons.Light))
        assertTrue(covered)
        assertContentEquals(Array<BarIcons?>(4) { BarIcons.Light }, icons)
    }

    @Test
    fun aBarWithoutHintsIsNotCovered() {
        assertFalse(fill(statusBar).first)
    }

    @Test
    fun aHintThatLeavesACellOutDoesNotCoverTheBar() {
        val (covered, icons) = fill(statusBar, BarHint(Rect(0f, 0f, 110f, 56f), BarIcons.Dark))
        assertFalse(covered)
        assertEquals(listOf(BarIcons.Dark, BarIcons.Dark, BarIcons.Dark, null), icons.toList())
    }

    @Test
    fun aHintBelowTheBarDoesNotCoverIt() {
        assertFalse(fill(statusBar, BarHint(Rect(0f, 20f, 160f, 80f), BarIcons.Light)).first)
    }

    @Test
    fun hintsNextToEachOtherCoverTheBarTogether() {
        val (covered, icons) =
            fill(
                statusBar,
                BarHint(Rect(0f, 0f, 80f, 56f), BarIcons.Light),
                BarHint(Rect(80f, 0f, 160f, 56f), BarIcons.Dark),
            )
        assertTrue(covered)
        assertEquals(
            listOf(BarIcons.Light, BarIcons.Light, BarIcons.Dark, BarIcons.Dark),
            icons.toList(),
        )
    }

    @Test
    fun theLastOfOverlappingHintsWins() {
        val (covered, icons) =
            fill(
                statusBar,
                BarHint(Rect(0f, 0f, 160f, 56f), BarIcons.Light),
                BarHint(Rect(40f, 0f, 120f, 56f), BarIcons.Dark),
            )
        assertTrue(covered)
        assertEquals(
            listOf(BarIcons.Light, BarIcons.Dark, BarIcons.Dark, BarIcons.Light),
            icons.toList(),
        )
    }

    @Test
    fun aSideBarIsCoveredAlongItsHeight() {
        val (covered, icons) = fillVertical(sideBar, BarHint(Rect(0f, 0f, 56f, 80f), BarIcons.Dark))
        assertFalse(covered)
        assertEquals(listOf(BarIcons.Dark, BarIcons.Dark, null, null), icons.toList())
        assertTrue(fillVertical(sideBar, BarHint(Rect(0f, 0f, 56f, 160f), BarIcons.Dark)).first)
    }

    @Test
    fun theDirectionOfTheBarDecidesNotTheShapeOfTheRegion() {
        // What a small modifier leaves of a status bar can be taller than wide
        val narrowStatusBar = Rect(0f, 0f, 20f, 24f)
        val hint = BarHint(Rect(0f, 0f, 10f, 56f), BarIcons.Light)
        val (covered, icons) = fill(narrowStatusBar, hint)
        assertFalse(covered)
        assertEquals(listOf(BarIcons.Light, BarIcons.Light, null, null), icons.toList())
    }

    @Test
    fun theVotesOfHintedCellsFollowTheirWeights() {
        val weights = floatArrayOf(3f, 1f, 1f, 3f)
        val icons = arrayOf<BarIcons?>(BarIcons.Light, BarIcons.Dark, BarIcons.Dark, BarIcons.Dark)
        // 3 for light against 5 for dark
        assertEquals(BarIcons.Dark, BarDecider.decideFromIcons(icons, 4, weights, null))
        val lightEdges =
            arrayOf<BarIcons?>(BarIcons.Light, BarIcons.Dark, BarIcons.Dark, BarIcons.Light)
        assertEquals(BarIcons.Light, BarDecider.decideFromIcons(lightEdges, 4, weights, null))
    }

    @Test
    fun aTieOfHintedCellsKeepsThePreviousIcons() {
        val weights = FloatArray(4) { 1f }
        val icons = arrayOf<BarIcons?>(BarIcons.Light, BarIcons.Light, BarIcons.Dark, BarIcons.Dark)
        assertEquals(BarIcons.Light, BarDecider.decideFromIcons(icons, 4, weights, BarIcons.Light))
        assertEquals(BarIcons.Dark, BarDecider.decideFromIcons(icons, 4, weights, BarIcons.Dark))
        assertEquals(BarIcons.Dark, BarDecider.decideFromIcons(icons, 4, weights, null))
    }
}

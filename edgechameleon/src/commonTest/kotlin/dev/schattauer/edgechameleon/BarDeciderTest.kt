package dev.schattauer.edgechameleon

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarDeciderTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val transparent = 0x00000000

    private fun decide(
        cells: IntArray,
        previous: BarIcons? = null,
        weights: FloatArray = FloatArray(cells.size) { 1f },
    ): BarIcons? {
        return BarDecider.decide(
            pixels = cells,
            start = 0,
            stride = 1,
            count = cells.size,
            weights = weights,
            previous = previous,
            contrastThreshold = 0.179f,
            hysteresis = 1.25f,
        )
    }

    private fun gray(value: Int): Int {
        return (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }

    @Test
    fun luminanceMatchesWcagEndpoints() {
        assertEquals(1f, BarDecider.luminance(white), 0.001f)
        assertEquals(0f, BarDecider.luminance(black), 0.001f)
        assertEquals(0.2159f, BarDecider.luminance(gray(128)), 0.001f)
    }

    @Test
    fun whiteBarGetsDarkIconsAndBlackBarGetsLightIcons() {
        assertEquals(BarIcons.Dark, decide(IntArray(16) { white }))
        assertEquals(BarIcons.Light, decide(IntArray(16) { black }))
    }

    @Test
    fun smallBrightPatchDoesNotFlipADarkBar() {
        val cells = IntArray(16) { if (it == 7) white else black }
        assertEquals(BarIcons.Light, decide(cells))
    }

    @Test
    fun edgeZonesOutvoteTheMiddle() {
        val weights = BarDecider.statusBarWeights(16, iconZoneWeight = 3f)
        // The 8 edge cells are dark and the 8 middle cells are white
        val cells = IntArray(16) { if (it < 4 || it >= 12) black else white }
        assertEquals(BarIcons.Light, decide(cells, weights = weights))
        assertEquals(24f + 8f, weights.sum())
    }

    @Test
    fun statusBarWeightsAreSymmetric() {
        val weights = BarDecider.statusBarWeights(16, iconZoneWeight = 3f)
        assertContentEquals(weights, weights.reversedArray())
        assertTrue(weights[0] > weights[8])
    }

    @Test
    fun theWholeWidthOfTheNavigationBarCountsTheSame() {
        val weights = BarDecider.navigationBarWeights(16)
        assertContentEquals(FloatArray(16) { 1f }, weights)
    }

    @Test
    fun darkEdgesAndAWhiteMiddleTieInTheNavigationBar() {
        val weights = BarDecider.navigationBarWeights(16)
        val cells = IntArray(16) { if (it < 4 || it >= 12) black else white }
        // Half of the bar needs light icons and half needs dark icons, so the previous result stays
        assertNull(decide(cells, weights = weights))
        assertEquals(BarIcons.Dark, decide(cells, previous = BarIcons.Dark, weights = weights))
        assertEquals(BarIcons.Light, decide(cells, previous = BarIcons.Light, weights = weights))
    }

    @Test
    fun aWhiteMiddleOfTheNavigationBarWinsOverNarrowerDarkEdges() {
        val weights = BarDecider.navigationBarWeights(16)
        val cells = IntArray(16) { if (it < 3 || it >= 13) black else white }
        assertEquals(BarIcons.Dark, decide(cells, weights = weights))
    }

    @Test
    fun hysteresisKeepsTheCurrentIconsNearTheThreshold() {
        // Luminance 0.2 is above the threshold 0.179, but within its hysteresis band
        val cells = IntArray(16) { gray(124) }
        assertEquals(BarIcons.Dark, decide(cells, previous = null))
        assertEquals(BarIcons.Light, decide(cells, previous = BarIcons.Light))
        assertEquals(BarIcons.Dark, decide(cells, previous = BarIcons.Dark))
    }

    @Test
    fun clearlyDifferentContentOvercomesHysteresis() {
        assertEquals(BarIcons.Dark, decide(IntArray(16) { white }, previous = BarIcons.Light))
        assertEquals(BarIcons.Light, decide(IntArray(16) { black }, previous = BarIcons.Dark))
    }

    @Test
    fun transparentCellsAbstain() {
        assertNull(decide(IntArray(16) { transparent }))
        assertEquals(BarIcons.Dark, decide(IntArray(16) { transparent }, previous = BarIcons.Dark))
        val cells = IntArray(16) { if (it == 3) white else transparent }
        assertEquals(BarIcons.Dark, decide(cells))
    }

    @Test
    fun tieKeepsThePreviousResult() {
        val cells = IntArray(16) { if (it < 8) black else white }
        assertEquals(BarIcons.Light, decide(cells, previous = BarIcons.Light))
        assertEquals(BarIcons.Dark, decide(cells, previous = BarIcons.Dark))
    }

    @Test
    fun readsColumnsThroughStride() {
        // A 2 wide image, where only the first column is black
        val pixels = intArrayOf(black, white, black, white, black, white, black, white)
        val result =
            BarDecider.decide(
                pixels = pixels,
                start = 0,
                stride = 2,
                count = 4,
                weights = FloatArray(4) { 1f },
                previous = null,
                contrastThreshold = 0.179f,
                hysteresis = 1.25f,
            )
        assertEquals(BarIcons.Light, result)
    }
}

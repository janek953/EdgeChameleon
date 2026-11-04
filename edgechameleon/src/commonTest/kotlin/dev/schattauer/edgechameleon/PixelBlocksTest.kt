package dev.schattauer.edgechameleon

import kotlin.test.Test
import kotlin.test.assertEquals

class PixelBlocksTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun aRowIsAveragedCellByCell() {
        val cells = 3
        val block = PixelBlocks.Block
        val source = IntArray(cells * block * block)
        // The second cell is white, the others black
        for (y in 0 until block) {
            for (x in 0 until cells * block) {
                source[y * cells * block + x] = if (x / block == 1) white else black
            }
        }
        val out = IntArray(cells)
        PixelBlocks.average(source, cells, isVertical = false, out, start = 0, stride = 1)
        assertEquals(listOf(black, white, black), out.toList())
    }

    @Test
    fun aColumnIsAveragedCellByCell() {
        val cells = 2
        val block = PixelBlocks.Block
        val source = IntArray(block * cells * block)
        for (y in 0 until cells * block) {
            for (x in 0 until block) {
                source[y * block + x] = if (y / block == 0) white else black
            }
        }
        val out = IntArray(1 + cells * 3)
        PixelBlocks.average(source, cells, isVertical = true, out, start = 1, stride = 3)
        assertEquals(white, out[1])
        assertEquals(black, out[4])
    }

    @Test
    fun aMixedCellGetsTheMeanOfItsPixels() {
        val block = PixelBlocks.Block
        val source = IntArray(block * block) { if (it % 2 == 0) white else black }
        val out = IntArray(1)
        PixelBlocks.average(source, 1, isVertical = false, out, start = 0, stride = 1)
        // Half of the pixels are white, so every channel is 127.5 and rounds up
        assertEquals(0xFF808080.toInt(), out[0])
    }
}

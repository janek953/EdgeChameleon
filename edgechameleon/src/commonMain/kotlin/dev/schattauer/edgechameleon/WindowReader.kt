package dev.schattauer.edgechameleon

import androidx.compose.ui.geometry.Rect

/**
 * Reads the colours behind the bars from what the window shows, instead of drawing the content a
 * second time. The window has drawn its frame already, so this costs the app no extra drawing, but
 * unlike the content of the modifier it also shows whatever else the window draws there.
 */
internal interface WindowReader {
    /**
     * Fills [pixels] with ARGB values in the layout of the sample layer: the [cells] cells of the
     * [status] bar in the first row, and the [cells] cells of the [navigation] bar below, in the
     * second row, or in the first column from the second row on if [isSideNavigation]. A null bar
     * is left untouched. The regions are in window coordinates. Returns false if no pixels could be
     * read, and throws if the window can not be read at all.
     */
    suspend fun read(
        status: Rect?,
        navigation: Rect?,
        isSideNavigation: Boolean,
        cells: Int,
        pixels: IntArray,
    ): Boolean

    fun release()
}

/** Reduces a copy of a bar that is [Block] times larger than the cells to one colour per cell. */
internal object PixelBlocks {
    /** Pixels along the bar that one cell is made of, and across it. */
    const val Block = 4

    /**
     * Averages each cell of [source], which is [Block] pixels across and [cells] * [Block] long,
     * and lies in a row if not [isVertical], and writes them to [out] from [start], [stride]
     * entries apart.
     */
    fun average(
        source: IntArray,
        cells: Int,
        isVertical: Boolean,
        out: IntArray,
        start: Int,
        stride: Int,
    ) {
        val width = if (isVertical) Block else cells * Block
        for (cell in 0 until cells) {
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            for (along in 0 until Block) {
                for (across in 0 until Block) {
                    val x = if (isVertical) across else cell * Block + along
                    val y = if (isVertical) cell * Block + along else across
                    val argb = source[y * width + x]
                    alpha += argb ushr 24
                    red += argb shr 16 and 0xFF
                    green += argb shr 8 and 0xFF
                    blue += argb and 0xFF
                }
            }
            val count = Block * Block
            val half = count / 2
            out[start + cell * stride] =
                ((alpha + half) / count shl 24) or
                    ((red + half) / count shl 16) or
                    ((green + half) / count shl 8) or
                    ((blue + half) / count)
        }
    }
}

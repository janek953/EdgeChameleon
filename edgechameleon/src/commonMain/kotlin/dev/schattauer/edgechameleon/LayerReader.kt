package dev.schattauer.edgechameleon

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Reads the pixels of a small layer. */
internal interface LayerReader {
    /** Fills [pixels] with ARGB values row by row, and returns false if no pixels could be read. */
    suspend fun read(
        layer: GraphicsLayer,
        width: Int,
        height: Int,
        pixels: IntArray,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Boolean

    fun release()
}

/** Works on every platform, but renders on the calling thread and allocates a bitmap per read. */
internal object BitmapLayerReader : LayerReader {
    override suspend fun read(
        layer: GraphicsLayer,
        width: Int,
        height: Int,
        pixels: IntArray,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Boolean {
        layer.toImageBitmap().readPixels(pixels, width = width, height = height, stride = width)
        return true
    }

    override fun release() {}
}

internal expect fun createLayerReader(): LayerReader

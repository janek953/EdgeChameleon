package dev.schattauer.edgechameleon

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Rect
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Copies the bar areas of the last frame of the window into a bitmap of a few pixels, with
 * [PixelCopy]. The copy is made by the renderer from the buffer that the window drew already, so
 * nothing is drawn twice, and the calling thread only waits for the result without blocking.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class PixelCopyWindowReader(private val window: Window) : WindowReader {
    private val thread = HandlerThread("EdgeChameleonWindow").apply { start() }
    private val handler = Handler(thread.looper)

    /** One bitmap for the bars that lie in a row and one for those in a column, as they differ. */
    private var rowBitmap: Bitmap? = null
    private var columnBitmap: Bitmap? = null
    private var staging = IntArray(0)

    /** Copies in a row that failed for a reason that may pass, such as a surface that is gone. */
    private var failedCopies = 0

    override suspend fun read(
        status: Rect?,
        navigation: Rect?,
        isSideNavigation: Boolean,
        cells: Int,
        pixels: IntArray,
    ): Boolean {
        if (status != null && !readBar(status, false, cells, pixels, 0, 1)) return false
        if (navigation != null) {
            val stride = if (isSideNavigation) cells else 1
            if (!readBar(navigation, isSideNavigation, cells, pixels, cells, stride)) return false
        }
        return true
    }

    private suspend fun readBar(
        region: Rect,
        isVertical: Boolean,
        cells: Int,
        pixels: IntArray,
        start: Int,
        stride: Int,
    ): Boolean {
        val width = if (isVertical) PixelBlocks.Block else cells * PixelBlocks.Block
        val height = if (isVertical) cells * PixelBlocks.Block else PixelBlocks.Block
        val destination = bitmapFor(isVertical, width, height)
        val source = region.toWindowRect()
        if (source.isEmpty) return false

        val result =
            withTimeoutOrNull(TimeoutMillis) {
                suspendCancellableCoroutine<Int> { continuation ->
                    PixelCopy.request(
                        window,
                        source,
                        destination,
                        { code -> continuation.resume(code) },
                        handler,
                    )
                }
            }
        if (result == null) {
            // The copy can still write into the bitmap, so the next read gets another one
            if (isVertical) columnBitmap = null else rowBitmap = null
            return false
        }
        when (result) {
            PixelCopy.SUCCESS -> failedCopies = 0
            // The window has no frame yet, or the surface is busy: try again later
            PixelCopy.ERROR_SOURCE_NO_DATA,
            PixelCopy.ERROR_TIMEOUT -> return false
            // The surface can be gone for a moment, for example while the window is resized. When
            // it stays gone, the window can not be copied, for example because it is secure
            PixelCopy.ERROR_SOURCE_INVALID,
            PixelCopy.ERROR_UNKNOWN -> {
                if (++failedCopies >= MaxFailedCopies) {
                    throw IllegalStateException("PixelCopy failed with $result")
                }
                return false
            }
            else -> throw IllegalStateException("PixelCopy failed with $result")
        }

        if (staging.size < width * height) staging = IntArray(width * height)
        destination.getPixels(staging, 0, width, 0, 0, width, height)
        PixelBlocks.average(staging, cells, isVertical, pixels, start, stride)
        return true
    }

    private fun bitmapFor(isVertical: Boolean, width: Int, height: Int): Bitmap {
        val cached = if (isVertical) columnBitmap else rowBitmap
        if (cached != null && cached.width == width && cached.height == height) return cached
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (isVertical) columnBitmap = created else rowBitmap = created
        return created
    }

    override fun release() {
        thread.quitSafely()
        rowBitmap = null
        columnBitmap = null
    }

    /** Whole pixels that include the region, kept inside the window. */
    private fun Rect.toWindowRect(): AndroidRect {
        val decor = window.decorView
        return AndroidRect(
            floor(left).toInt().coerceIn(0, decor.width),
            floor(top).toInt().coerceIn(0, decor.height),
            ceil(right).toInt().coerceIn(0, decor.width),
            ceil(bottom).toInt().coerceIn(0, decor.height),
        )
    }

    private companion object {
        const val TimeoutMillis = 500L
        const val MaxFailedCopies = 3
    }
}

package dev.schattauer.edgechameleon

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

internal actual fun createLayerReader(): LayerReader {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AsyncLayerReader()
    } else {
        BitmapLayerReader
    }
}

/**
 * The GPU writes colours with the alpha already multiplied in, while the rest of the library, and
 * [BitmapLayerReader], work with straight colours.
 */
private fun unpremultiply(channel: Int, alpha: Int): Int {
    if (alpha == 0 || alpha == 255) return channel
    return minOf(255, (channel * 255 + alpha / 2) / alpha)
}

/**
 * Renders into a small CPU readable surface on the render thread, and reads the result on a
 * background thread. The main thread only records and hands over the frame, and never waits for the
 * GPU. Buffers are reused, so no bitmap is allocated per read.
 *
 * Every frame is taken out of the surface the moment it arrives, also when the read that asked for
 * it has given up. A frame that nobody takes would stay queued, and once the few buffers of the
 * surface are all queued, the render thread, which the whole app draws on, blocks until its dequeue
 * times out.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private class AsyncLayerReader : LayerReader {
    private val thread = HandlerThread("EdgeChameleon").apply { start() }
    private val handler = Handler(thread.looper)
    private val drawScope = CanvasDrawScope()
    private val root = RenderNode("EdgeChameleon")
    private val renderer =
        HardwareRenderer().apply {
            isOpaque = false
            setContentRoot(root)
        }
    private var readerWidth = 0
    private var readerHeight = 0

    /** Guards [reader], [latest] and [frames], which the reading thread and the callers share. */
    private val lock = Any()
    private var reader: ImageReader? = null

    /** The newest frame that arrived, and the number of frames that did. */
    private var latest = IntArray(0)
    private var frames = 0L

    /** Wakes a read that waits for a frame. Frames that nobody waits for leave it set. */
    private val arrived = Channel<Unit>(Channel.CONFLATED)

    override suspend fun read(
        layer: GraphicsLayer,
        width: Int,
        height: Int,
        pixels: IntArray,
        density: Density,
        layoutDirection: LayoutDirection,
    ): Boolean {
        readerFor(width, height)
        val framesBefore = synchronized(lock) { frames }
        root.setPosition(0, 0, width, height)
        val canvas = root.beginRecording(width, height)
        try {
            drawScope.draw(
                density,
                layoutDirection,
                Canvas(canvas),
                Size(width.toFloat(), height.toFloat()),
            ) {
                drawLayer(layer)
            }
        } finally {
            root.endRecording()
        }
        val result = renderer.createRenderRequest().setWaitForPresent(false).syncAndDraw()
        // No image will come, so there is no reason to wait for the timeout
        if ((result and NoFrame) != 0) return false

        // A frame that is not drawn never arrives, so this must not wait forever
        val received =
            withTimeoutOrNull(TimeoutMillis) {
                while (synchronized(lock) { frames } == framesBefore) arrived.receive()
                true
            } ?: false
        if (!received) return false
        synchronized(lock) {
            val count = width * height
            if (latest.size < count) return false
            latest.copyInto(pixels, endIndex = count)
        }
        return true
    }

    private fun readerFor(width: Int, height: Int) {
        if (reader != null && readerWidth == width && readerHeight == height) return
        val created =
            ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                MaxImages,
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN,
            )
        created.setOnImageAvailableListener({ source -> takeFrame(source) }, handler)
        val previous: ImageReader?
        synchronized(lock) {
            previous = reader
            reader = created
            latest = IntArray(width * height)
        }
        readerWidth = width
        readerHeight = height
        renderer.setSurface(created.surface)
        previous?.close()
    }

    /** Runs on the reading thread for every frame, and always gives the buffer back. */
    private fun takeFrame(source: ImageReader) {
        val image =
            try {
                source.acquireLatestImage()
            } catch (_: IllegalStateException) {
                // The reader was closed while this was waiting to run
                null
            } ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            synchronized(lock) {
                if (source !== reader) return
                val width = image.width
                val height = image.height
                if (latest.size < width * height) return
                for (row in 0 until height) {
                    val rowStart = row * plane.rowStride
                    for (column in 0 until width) {
                        val offset = rowStart + column * plane.pixelStride
                        val r = buffer.get(offset).toInt() and 0xFF
                        val g = buffer.get(offset + 1).toInt() and 0xFF
                        val b = buffer.get(offset + 2).toInt() and 0xFF
                        val a = buffer.get(offset + 3).toInt() and 0xFF
                        latest[row * width + column] =
                            (a shl 24) or
                                (unpremultiply(r, a) shl 16) or
                                (unpremultiply(g, a) shl 8) or
                                unpremultiply(b, a)
                    }
                }
                frames++
            }
            arrived.trySend(Unit)
        } finally {
            image.close()
        }
    }

    override fun release() {
        renderer.destroy()
        root.discardDisplayList()
        val reader =
            synchronized(lock) {
                val current = reader
                this.reader = null
                current
            }
        // On the reading thread, so a frame in progress finishes before its reader closes
        handler.post {
            reader?.setOnImageAvailableListener(null, null)
            reader?.close()
            thread.quitSafely()
        }
    }

    private companion object {
        const val MaxImages = 2
        const val TimeoutMillis = 500L

        /** Results of a sync after which no frame is drawn. */
        const val NoFrame =
            HardwareRenderer.SYNC_FRAME_DROPPED or
                HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
                HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND
    }
}

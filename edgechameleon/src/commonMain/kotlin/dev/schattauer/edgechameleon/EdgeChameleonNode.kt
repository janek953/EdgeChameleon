package dev.schattauer.edgechameleon

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CancellationException

/**
 * Records the content into a layer, and draws that layer to the window. For a sample, a second
 * layer of very few pixels draws just the bar areas of the first one, scaled down, so only those
 * few pixels have to be read back.
 */
@OptIn(ExperimentalEdgeChameleonApi::class)
internal class EdgeChameleonNode(
    private var enabled: Boolean,
    private var forcedIcons: BarIcons?,
    private var statusBar: Boolean,
    private var navigationBar: Boolean,
    private var state: EdgeChameleonState?,
    private var tuning: EdgeChameleonTuning,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    override val shouldAutoInvalidate: Boolean = false

    private var bars: PlatformBars? = null
    private var barsAttached = false
    private var graphicsContext: GraphicsContext? = null
    private var contentLayer: GraphicsLayer? = null
    private var sampleLayer: GraphicsLayer? = null
    private var reader: LayerReader? = null
    private var throttle: SampleThrottle? = null

    private var settingsVersion = 0

    /**
     * Reads in a row that gave no pixels, so that a reader that never works is not asked forever.
     */
    private var failedReads = 0

    /** Set when no reader works on this device, and the bars are left to the app instead. */
    private var samplingFailed = false

    private var pixels = IntArray(0)
    private var statusWeights = FloatArray(0)
    private var navigationWeights = FloatArray(0)

    private var statusIcons: BarIcons? = null
    private var navigationIcons: BarIcons? = null

    private val isSampling: Boolean
        get() =
            enabled &&
                forcedIcons == null &&
                (statusBar || navigationBar) &&
                !samplingFailed &&
                bars?.isSupported == true

    override fun onAttach() {
        graphicsContext = currentValueOf(LocalGraphicsContext)
        bars = createPlatformBars(this)
        samplingFailed = false
        rebuildForTuning()
        syncMode()
    }

    override fun onDetach() {
        throttle?.cancel()
        detachBars()
        releaseLayers()
        throttle = null
        bars = null
        graphicsContext = null
    }

    fun update(
        enabled: Boolean,
        forcedIcons: BarIcons?,
        statusBar: Boolean,
        navigationBar: Boolean,
        state: EdgeChameleonState?,
        tuning: EdgeChameleonTuning,
    ) {
        settingsVersion++
        val tuningChanged = this.tuning != tuning
        val statusRemoved = this.statusBar && !statusBar
        val navigationRemoved = this.navigationBar && !navigationBar
        val previousState = this.state
        this.enabled = enabled
        this.forcedIcons = forcedIcons
        this.statusBar = statusBar
        this.navigationBar = navigationBar
        this.state = state
        this.tuning = tuning
        if (previousState !== state) {
            previousState?.statusBarIcons = null
            previousState?.navigationBarIcons = null
            state?.statusBarIcons = statusIcons
            state?.navigationBarIcons = navigationIcons
        }
        // Before the node is attached, onAttach builds everything for the current tuning
        if (tuningChanged && isAttached) rebuildForTuning()
        if (statusRemoved || navigationRemoved) {
            // Hands bars that are no longer included back to the app, and keeps the other bar
            releaseBars(statusRemoved, navigationRemoved)
        }
        syncMode()
        invalidateDraw()
    }

    private fun rebuildForTuning() {
        statusWeights = BarDecider.statusBarWeights(tuning.cellCount, tuning.iconZoneWeight)
        navigationWeights = BarDecider.navigationBarWeights(tuning.cellCount)
        // A sample of the old tuning must not run next to the one of the new tuning
        throttle?.cancel()
        throttle = SampleThrottle(coroutineScope, 1000L / tuning.maxSamplesPerSecond) { sample() }
    }

    private fun syncMode() {
        val bars = bars ?: return
        if (!bars.isSupported) return
        if (!enabled || (!statusBar && !navigationBar)) {
            detachBars()
            releaseLayers()
            return
        }
        val forced = forcedIcons
        if (forced == null && samplingFailed) return
        if (!barsAttached) {
            bars.attach { throttle?.takeIf { isSampling }?.request() }
            barsAttached = true
        }
        if (forced != null) {
            releaseLayers()
            applyIcons(
                status = if (statusBar) forced else null,
                navigation = if (navigationBar) forced else null,
            )
        } else {
            throttle?.request()
        }
    }

    private fun detachBars() {
        if (barsAttached) {
            bars?.detach()
            barsAttached = false
        }
        statusIcons = null
        navigationIcons = null
        state?.statusBarIcons = null
        state?.navigationBarIcons = null
    }

    private fun releaseBars(statusBar: Boolean, navigationBar: Boolean) {
        if (barsAttached) bars?.restore(statusBar, navigationBar)
        if (statusBar) {
            statusIcons = null
            state?.statusBarIcons = null
        }
        if (navigationBar) {
            navigationIcons = null
            state?.navigationBarIcons = null
        }
    }

    private fun releaseLayers() {
        val context = graphicsContext ?: return
        contentLayer?.let(context::releaseGraphicsLayer)
        sampleLayer?.let(context::releaseGraphicsLayer)
        contentLayer = null
        sampleLayer = null
        reader?.release()
        reader = null
    }

    /** Gives up on sampling, and hands the bars back to the app. */
    private fun stopSampling() {
        samplingFailed = true
        detachBars()
        releaseLayers()
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val context = graphicsContext
        if (!isSampling || context == null) {
            drawContent()
            return
        }
        val layer = contentLayer ?: context.createGraphicsLayer().also { contentLayer = it }
        // The DrawScope member, as only that redirects this scope's drawContent() into the layer
        layer.record(size.toIntSize()) { this@draw.drawContent() }
        drawLayer(layer)
    }

    private suspend fun sample() {
        val bars = bars ?: return
        val content = contentLayer ?: return
        val context = graphicsContext ?: return
        if (!isAttached || !isSampling || !bars.hasFocus) return
        val version = settingsVersion

        // Taken now and not when the node was placed, as an ancestor can move it without placing
        // it again
        val coordinates = requireLayoutCoordinates()
        if (!coordinates.isAttached) return
        val origin = coordinates.positionInWindow()
        val bounds = Rect(origin, coordinates.size.toSize())
        val regions = bars.regions()
        val statusRegion = regions.statusBar.takeIf { statusBar }?.intersectOrNull(bounds)
        val navigationRegion =
            regions.navigationBar.takeIf { navigationBar }?.intersectOrNull(bounds)
        if (statusRegion == null && navigationRegion == null) return

        // The status bar uses the first row. A side navigation bar uses the first column below it,
        // and any other navigation bar the second row.
        val cells = tuning.cellCount
        val isSideNavigation =
            navigationRegion != null && navigationRegion.height > navigationRegion.width
        val width = cells
        val height = if (isSideNavigation) cells + 1 else 2
        val layer = sampleLayer ?: context.createGraphicsLayer().also { sampleLayer = it }
        layer.record(requireDensity(), requireLayoutDirection(), IntSize(width, height)) {
            if (statusRegion != null) {
                drawRegion(content, origin, statusRegion, Rect(0f, 0f, cells.toFloat(), 1f))
            }
            if (navigationRegion != null) {
                val target =
                    if (isSideNavigation) {
                        Rect(0f, 1f, 1f, cells + 1f)
                    } else {
                        Rect(0f, 1f, cells.toFloat(), 2f)
                    }
                drawRegion(content, origin, navigationRegion, target)
            }
        }

        val size = width * height
        if (pixels.size < size) pixels = IntArray(size)
        val hasPixels = readPixels(layer, width, height)
        // The node can be detached while the read is suspended, and a result for old settings must
        // not be applied. An update has already requested a new sample if one is needed.
        if (!isAttached || this.bars !== bars || version != settingsVersion) return
        if (!hasPixels) {
            // A frame that was dropped or late must not leave the icons wrong, so ask again, but
            // not forever, as a reader that never works would then keep the renderer busy
            if (isSampling && ++failedReads <= MaxFailedReads) throttle?.request()
            return
        }
        failedReads = 0
        state?.let { it.sampleCount++ }

        val newStatus = statusRegion?.let {
            BarDecider.decide(
                pixels,
                0,
                1,
                cells,
                statusWeights,
                statusIcons,
                tuning.contrastThreshold,
                tuning.hysteresis,
            )
        }
        val navigationStride = if (isSideNavigation) width else 1
        val newNavigation = navigationRegion?.let {
            BarDecider.decide(
                pixels,
                width,
                navigationStride,
                cells,
                navigationWeights,
                navigationIcons,
                tuning.contrastThreshold,
                tuning.hysteresis,
            )
        }
        applyIcons(newStatus, newNavigation)

        val state = state
        if (state != null && state.collectCells) {
            state.statusBarCells =
                if (statusRegion != null) List(cells) { Color(pixels[it]) } else emptyList()
            state.navigationBarCells =
                if (navigationRegion != null) {
                    List(cells) { Color(pixels[width + it * navigationStride]) }
                } else {
                    emptyList()
                }
        }
    }

    /**
     * Reads the sample layer into [pixels]. A reader that throws is replaced by the one that works
     * on every platform, and when that fails too, sampling stops. The app must never crash because
     * of the icon colour.
     */
    private suspend fun readPixels(layer: GraphicsLayer, width: Int, height: Int): Boolean {
        val density = requireDensity()
        val layoutDirection = requireLayoutDirection()
        try {
            val current = reader ?: createLayerReader().also { reader = it }
            return current.read(layer, width, height, pixels, density, layoutDirection)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (reader === BitmapLayerReader) {
                stopSampling()
            } else {
                reader?.release()
                reader = BitmapLayerReader
                throttle?.request()
            }
            return false
        }
    }

    private fun DrawScope.drawRegion(
        content: GraphicsLayer,
        origin: Offset,
        region: Rect,
        target: Rect,
    ) {
        clipRect(target.left, target.top, target.right, target.bottom) {
            translate(target.left, target.top) {
                scale(
                    target.width / region.width,
                    target.height / region.height,
                    pivot = Offset.Zero,
                ) {
                    // Regions are in window coordinates and the content layer starts at the node
                    translate(origin.x - region.left, origin.y - region.top) { drawLayer(content) }
                }
            }
        }
    }

    private fun applyIcons(status: BarIcons?, navigation: BarIcons?) {
        val statusChange = status?.takeIf { it != statusIcons }
        val navigationChange = navigation?.takeIf { it != navigationIcons }
        if (statusChange == null && navigationChange == null) return
        bars?.setIcons(statusChange, navigationChange)
        if (statusChange != null) {
            statusIcons = statusChange
            state?.statusBarIcons = statusChange
        }
        if (navigationChange != null) {
            navigationIcons = navigationChange
            state?.navigationBarIcons = navigationChange
        }
    }

    private fun Rect.intersectOrNull(other: Rect): Rect? {
        return if (overlaps(other)) intersect(other) else null
    }

    private companion object {
        const val MaxFailedReads = 3
    }
}

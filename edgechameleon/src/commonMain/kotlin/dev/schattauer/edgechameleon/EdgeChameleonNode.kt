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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.node.traverseDescendants
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
) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode,
    TraversableNode,
    PointerInputModifierNode {

    override val traverseKey: Any = EdgeChameleonKey

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

    private var windowReader: WindowReader? = null

    /** Set when the window can not be read, and the content is drawn a second time instead. */
    private var windowFailed = false

    private val usesWindow: Boolean
        get() = tuning.samplingSource == SamplingSource.Window && !windowFailed

    private var pixels = IntArray(0)
    private var statusWeights = FloatArray(0)
    private var navigationWeights = FloatArray(0)

    /**
     * What the last readback saw, to tell a sample that changed nothing from one that did. NaN
     * marks a cell that was not seen yet.
     */
    private var statusLuminances = FloatArray(0)
    private var navigationLuminances = FloatArray(0)
    private var lastStatusRegion: Rect? = null
    private var lastNavigationRegion: Rect? = null

    private var statusIcons: BarIcons? = null
    private var navigationIcons: BarIcons? = null

    private val hints = ArrayList<EdgeChameleonHintNode>()
    private val hintBuffer = ArrayList<BarHint>()

    /**
     * The hints of the previous sample, to tell hints that moved or changed from those that did
     * not.
     */
    private val lastHints = ArrayList<BarHint>()
    private var hintIcons = arrayOfNulls<BarIcons>(0)

    /**
     * Set while hints decide every included bar. The content is then not recorded into a layer, so
     * the modifier costs next to nothing.
     */
    private var coveredByHints = false

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
        windowFailed = false
        rebuildForTuning()
        // Hints that were there first did not find this node when they were attached. A hint under
        // another EdgeChameleonNode further down belongs to that one alone.
        traverseDescendants(EdgeChameleonHintKey) { node ->
            val hint = node as? EdgeChameleonHintNode
            if (hint != null && hint.nearestOwner() === this) addHint(hint)
            TraversableNode.Companion.TraverseDescendantsAction.ContinueTraversal
        }
        syncMode()
    }

    override fun onDetach() {
        throttle?.cancel()
        detachBars()
        releaseLayers()
        throttle = null
        bars = null
        graphicsContext = null
        coveredByHints = false
        hints.forEach { it.releaseOwner(this) }
        hints.clear()
        hintBuffer.clear()
        lastHints.clear()
    }

    fun addHint(hint: EdgeChameleonHintNode) {
        if (hint in hints) return
        hints += hint
        hint.setOwner(this)
        hintsChanged()
    }

    fun removeHint(hint: EdgeChameleonHintNode) {
        if (hints.remove(hint)) hintsChanged()
    }

    /** A hint came, went or changed, so the bars are looked at again at the full rate. */
    fun hintsChanged() {
        if (!isAttached || !isSampling) return
        throttle?.reset()
        throttle?.request()
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass == PointerEventPass.Initial && isSampling) throttle?.reset()
    }

    override fun onCancelPointerInput() {}

    override fun sharePointerInputWithSiblings(): Boolean {
        return true
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
        val sourceChanged = this.tuning.samplingSource != tuning.samplingSource
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
        if (sourceChanged && isAttached) {
            // A new source gets a new chance, and what only the old one needed is given back
            windowFailed = false
            releaseLayers()
        }
        if (statusRemoved || navigationRemoved) {
            // Hands bars that are no longer included back to the app, and keeps the other bar
            releaseBars(statusRemoved, navigationRemoved)
        }
        forgetLastSample()
        syncMode()
        invalidateDraw()
    }

    private fun rebuildForTuning() {
        statusWeights = BarDecider.statusBarWeights(tuning.cellCount, tuning.iconZoneWeight)
        navigationWeights = BarDecider.navigationBarWeights(tuning.cellCount)
        statusLuminances = FloatArray(tuning.cellCount)
        navigationLuminances = FloatArray(tuning.cellCount)
        hintIcons = arrayOfNulls(tuning.cellCount)
        forgetLastSample()
        // A sample of the old tuning must not run next to the one of the new tuning
        throttle?.cancel()
        throttle =
            SampleThrottle(
                coroutineScope,
                1000L / tuning.maxSamplesPerSecond,
                tuning.maxIdleIntervalMillis.toLong(),
            ) {
                sample()
            }
    }

    /** The next sample counts as a change, and so brings back the fast sampling rate. */
    private fun forgetLastSample() {
        statusLuminances.fill(Float.NaN)
        navigationLuminances.fill(Float.NaN)
        lastStatusRegion = null
        lastNavigationRegion = null
        throttle?.reset()
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
        releaseContentLayers()
        reader?.release()
        reader = null
        windowReader?.release()
        windowReader = null
    }

    /** Gives the layers back and keeps the readers, as they are costly to build again. */
    private fun releaseContentLayers() {
        val context = graphicsContext ?: return
        contentLayer?.let(context::releaseGraphicsLayer)
        sampleLayer?.let(context::releaseGraphicsLayer)
        contentLayer = null
        sampleLayer = null
    }

    /** Gives up on reading the window, and draws the content a second time instead. */
    private fun fallBackToContent() {
        windowFailed = true
        windowReader?.release()
        windowReader = null
        invalidateDraw()
        throttle?.request()
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
        if (!isSampling || coveredByHints || usesWindow || context == null) {
            drawContent()
            return
        }
        val layer = contentLayer ?: context.createGraphicsLayer().also { contentLayer = it }
        // The DrawScope member, as only that redirects this scope's drawContent() into the layer
        traceSection("EdgeChameleon.recordContent") {
            layer.record(size.toIntSize()) { this@draw.drawContent() }
        }
        drawLayer(layer)
    }

    private suspend fun sample() {
        val bars = bars ?: return
        val context = graphicsContext ?: return
        if (!isAttached || !isSampling) return
        if (!bars.hasFocus) {
            // The content can change while another window has the focus, so look again at the
            // full rate once it returns
            forgetLastSample()
            return
        }
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
        // and any other navigation bar the second row. The shape of the whole bar tells which, as
        // the part under a small modifier can be taller than wide on any bar.
        val navigationBounds = regions.navigationBar
        val isSideNavigation =
            navigationRegion != null &&
                navigationBounds != null &&
                navigationBounds.height > navigationBounds.width
        val cells = tuning.cellCount
        val hintsMoved = collectHints()
        val statusHinted = statusRegion?.let {
            decideFromHints(it, isVertical = false, statusWeights, statusIcons)
        }
        val navigationHinted = navigationRegion?.let {
            decideFromHints(it, isSideNavigation, navigationWeights, navigationIcons)
        }
        val statusNeedsPixels = statusRegion != null && statusHinted == null
        val navigationNeedsPixels = navigationRegion != null && navigationHinted == null
        if (!statusNeedsPixels && !navigationNeedsPixels) {
            // Hints decide everything, so nothing is recorded or read
            if (!coveredByHints) {
                coveredByHints = true
                releaseContentLayers()
                invalidateDraw()
            }
            // Nothing is measured, so what counts as a change is a hint that moved or came, or
            // icons that are not the ones that are shown. A screen that keeps redrawing behind
            // steady hints can then be looked at less often.
            val changed =
                hintsMoved ||
                    statusRegion != lastStatusRegion ||
                    navigationRegion != lastNavigationRegion ||
                    (statusHinted != null && statusHinted != statusIcons) ||
                    (navigationHinted != null && navigationHinted != navigationIcons)
            lastStatusRegion = statusRegion
            lastNavigationRegion = navigationRegion
            throttle?.reportResult(changed)
            applyIcons(statusHinted, navigationHinted)
            state?.let {
                it.statusBarCells = emptyList()
                it.navigationBarCells = emptyList()
            }
            return
        }
        if (coveredByHints) {
            // The content was not recorded while hints covered the bars, so let it be drawn once
            // before it is read. The pause is short, as nothing was measured while it grew.
            coveredByHints = false
            invalidateDraw()
            throttle?.reset()
            throttle?.request()
            return
        }

        val width = cells
        val height = if (isSideNavigation) cells + 1 else 2
        val size = width * height
        if (pixels.size < size) pixels = IntArray(size)
        val hasPixels =
            if (usesWindow) {
                readWindow(
                    statusRegion.takeIf { statusNeedsPixels },
                    navigationRegion.takeIf { navigationNeedsPixels },
                    isSideNavigation,
                    cells,
                )
            } else {
                val content = contentLayer ?: return
                val layer = sampleLayer ?: context.createGraphicsLayer().also { sampleLayer = it }
                recordSample(
                    layer,
                    content,
                    origin,
                    statusRegion.takeIf { statusNeedsPixels },
                    navigationRegion.takeIf { navigationNeedsPixels },
                    isSideNavigation,
                    cells,
                    width,
                    height,
                )
                readPixels(layer, width, height)
            }
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

        val navigationStride = if (isSideNavigation) width else 1
        var changed = statusRegion != lastStatusRegion || navigationRegion != lastNavigationRegion
        lastStatusRegion = statusRegion
        lastNavigationRegion = navigationRegion
        if (statusNeedsPixels) {
            changed =
                BarDecider.updateLuminances(
                    pixels,
                    0,
                    1,
                    cells,
                    statusLuminances,
                    BarDecider.ChangeEpsilon,
                ) || changed
        }
        if (navigationNeedsPixels) {
            changed =
                BarDecider.updateLuminances(
                    pixels,
                    width,
                    navigationStride,
                    cells,
                    navigationLuminances,
                    BarDecider.ChangeEpsilon,
                ) || changed
        }
        throttle?.reportResult(changed)

        val newStatus =
            statusHinted
                ?: if (statusNeedsPixels) {
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
                } else {
                    null
                }
        val newNavigation =
            navigationHinted
                ?: if (navigationNeedsPixels) {
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
                } else {
                    null
                }
        traceSection("EdgeChameleon.applyIcons") { applyIcons(newStatus, newNavigation) }

        val state = state
        if (state != null && state.collectCells) {
            state.statusBarCells =
                if (statusNeedsPixels) List(cells) { Color(pixels[it]) } else emptyList()
            state.navigationBarCells =
                if (navigationNeedsPixels) {
                    List(cells) { Color(pixels[width + it * navigationStride]) }
                } else {
                    emptyList()
                }
        }
    }

    private fun recordSample(
        layer: GraphicsLayer,
        content: GraphicsLayer,
        origin: Offset,
        statusRegion: Rect?,
        navigationRegion: Rect?,
        isSideNavigation: Boolean,
        cells: Int,
        width: Int,
        height: Int,
    ) {
        traceSection("EdgeChameleon.recordSample") {
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
        }
    }

    /**
     * Reads the bars from the window into [pixels]. When the window can not be read, the content is
     * drawn a second time instead, as the app must never lose its icons because of it.
     */
    private suspend fun readWindow(
        statusRegion: Rect?,
        navigationRegion: Rect?,
        isSideNavigation: Boolean,
        cells: Int,
    ): Boolean {
        try {
            val current =
                windowReader
                    ?: bars?.createWindowReader()?.also { windowReader = it }
                    ?: run {
                        fallBackToContent()
                        return false
                    }
            return current.read(statusRegion, navigationRegion, isSideNavigation, cells, pixels)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fallBackToContent()
            return false
        }
    }

    /**
     * Collects where the hints are now into [hintBuffer], and returns whether they differ from the
     * ones of the previous sample.
     */
    private fun collectHints(): Boolean {
        hintBuffer.clear()
        for (hint in hints) hint.current()?.let(hintBuffer::add)
        val moved = hintBuffer != lastHints
        if (moved) {
            lastHints.clear()
            lastHints.addAll(hintBuffer)
        }
        return moved
    }

    /**
     * The icons that the hints of [collectHints] give [region], or null when they do not cover all
     * of it. The cells of the bar lie in a column if [isVertical].
     */
    private fun decideFromHints(
        region: Rect,
        isVertical: Boolean,
        weights: FloatArray,
        previous: BarIcons?,
    ): BarIcons? {
        if (hintBuffer.isEmpty()) return null
        val cells = tuning.cellCount
        if (!HintCoverage.fill(hintIcons, region, isVertical, cells, hintBuffer)) return null
        return BarDecider.decideFromIcons(hintIcons, cells, weights, previous)
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

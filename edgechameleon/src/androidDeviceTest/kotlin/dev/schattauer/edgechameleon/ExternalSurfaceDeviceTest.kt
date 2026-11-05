package dev.schattauer.edgechameleon

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A [TextureView] and a [SurfaceView] behind the status bar. Their frames are painted by the views
 * themselves, never by Compose, as a video player or a camera preview does.
 */
@OptIn(ExperimentalEdgeChameleonApi::class)
@RunWith(Parameterized::class)
class ExternalSurfaceDeviceTest(private val source: SamplingSource) {
    private val state = EdgeChameleonState().apply { collectCells = true }
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private var painter: Painter? = null

    private fun launch(hint: BarIcons? = null, createView: (Context) -> View) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            activity.setContent {
                Box(
                    Modifier.edgeChameleon(
                            tuning = EdgeChameleonTuning(samplingSource = source),
                            navigationBar = false,
                            state = state,
                        )
                        .fillMaxSize()
                        .background(Color.White)
                ) {
                    // Covers the whole status bar
                    AndroidView(
                        factory = { context ->
                            createView(context).also { painter = it as Painter }
                        },
                        modifier =
                            Modifier.fillMaxWidth().height(BandHeight).edgeChameleonHint(hint),
                    )
                }
            }
        }
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun paint(color: Color) {
        scenario.onActivity { painter?.paint(color.toArgb()) }
    }

    private fun describe(): String {
        val cells = state.statusBarCells.joinToString { it.toArgb().toUInt().toString(16) }
        return "source=$source, samples=${state.sampleCount}, cells=[$cells]"
    }

    /** The sampled cells that have a vote, as the bar decider counts them. */
    private fun opaqueStatusBarCells(): Int {
        return state.statusBarCells.count {
            BarDecider.cellLuminance(it.toArgb()) != BarDecider.Abstains
        }
    }

    private fun awaitStatusIcons(icons: BarIcons) {
        val reached = awaitCondition(TimeoutMillis) { state.statusBarIcons == icons }
        assertEquals(
            "Status bar icons, ${describe()}",
            icons,
            if (reached) icons else state.statusBarIcons,
        )
    }

    @Test
    fun aTextureViewBehindTheStatusBarDecidesTheIcons() {
        launch { PaintedTexture(it) }
        paint(Color.White)
        awaitStatusIcons(BarIcons.Dark)
        paint(Color.Black)
        awaitStatusIcons(BarIcons.Light)
        paint(Color.White)
        awaitStatusIcons(BarIcons.Dark)
        paint(Color.Black)
        awaitStatusIcons(BarIcons.Light)
    }

    /**
     * A SurfaceView draws on a surface of its own, below the window, and leaves a transparent hole
     * in the window. Neither source sees the frames of that surface, so the cells have no vote and
     * the icons stay with the app. A hint or forced icons are the way to set them there.
     */
    @Test
    fun aSurfaceViewBehindTheStatusBarIsNotSampled() {
        launch { PaintedSurface(it) }
        paint(Color.Black)
        val sampled = awaitCondition(TimeoutMillis) { state.statusBarCells.isNotEmpty() }
        assertEquals("Nothing was sampled, ${describe()}", true, sampled)
        assertEquals("Cells ${describe()}", 0, opaqueStatusBarCells())
        assertEquals("Icons, ${describe()}", null, state.statusBarIcons)

        // New frames of the surface do not draw the window, and would not be seen if they did
        paint(Color.White)
        paint(Color.Black)
        Thread.sleep(SettleMillis)
        assertEquals("Cells ${describe()}", 0, opaqueStatusBarCells())
        assertEquals("Icons, ${describe()}", null, state.statusBarIcons)
    }

    /**
     * The way to set the icons over a SurfaceView: a hint on it covers the bar, so nothing is read.
     */
    @Test
    fun aHintOnASurfaceViewSetsTheIconsWithoutReadingAnything() {
        launch(hint = BarIcons.Light) { PaintedSurface(it) }
        paint(Color.Black)
        awaitStatusIcons(BarIcons.Light)
        Thread.sleep(SettleMillis)
        assertEquals("Samples, ${describe()}", 0, state.sampleCount)
    }

    private interface Painter {
        fun paint(argb: Int)
    }

    /** Posts a frame of one colour to the surface texture, and again once the texture exists. */
    private class PaintedTexture(context: Context) :
        TextureView(context), TextureView.SurfaceTextureListener, Painter {
        private var color = Color.White.toArgb()

        init {
            surfaceTextureListener = this
        }

        override fun paint(argb: Int) {
            color = argb
            if (isAvailable) drawFrame()
        }

        private fun drawFrame() {
            val canvas = lockCanvas() ?: return
            try {
                canvas.drawColor(color)
            } finally {
                unlockCanvasAndPost(canvas)
            }
        }

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            drawFrame()
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            drawFrame()
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /** Posts a frame of one colour to the surface, and again once the surface exists. */
    private class PaintedSurface(context: Context) :
        SurfaceView(context), SurfaceHolder.Callback, Painter {
        private var color = Color.White.toArgb()

        init {
            holder.addCallback(this)
        }

        override fun paint(argb: Int) {
            color = argb
            if (holder.surface.isValid) drawFrame()
        }

        private fun drawFrame() {
            val canvas = holder.lockCanvas() ?: return
            try {
                canvas.drawColor(color)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            drawFrame()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    companion object {
        private const val TimeoutMillis = 5_000L
        private const val SettleMillis = 1_000L
        private val BandHeight = 200.dp

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun sources(): List<SamplingSource> {
            return listOf(SamplingSource.Content, SamplingSource.Window)
        }
    }
}

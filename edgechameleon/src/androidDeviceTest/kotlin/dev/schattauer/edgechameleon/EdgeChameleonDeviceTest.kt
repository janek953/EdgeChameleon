package dev.schattauer.edgechameleon

import android.os.Build
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalEdgeChameleonApi::class)
class EdgeChameleonDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = EdgeChameleonState()
    private var background by mutableStateOf(Color.White)
    private var enabled by mutableStateOf(true)
    private var forcedIcons by mutableStateOf<BarIcons?>(null)
    private var statusBar by mutableStateOf(true)
    private var navigationBar by mutableStateOf(true)
    private var activeState by mutableStateOf(state)
    private var attached by mutableStateOf(true)
    private var dialog by mutableStateOf(false)
    private var scrimBefore = false
    private var fullScreenDialog by mutableStateOf(false)
    private var dialogWindow: Window? = null
    private val dialogState = EdgeChameleonState()

    private val controller: WindowInsetsControllerCompat
        get() =
            WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)

    @Before
    fun setUp() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            // A known state that differs from what the white and dark content below lead to
            controller.isAppearanceLightStatusBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                scrimBefore = rule.activity.window.isNavigationBarContrastEnforced
            }
        }
        rule.setContent {
            val modifier =
                if (attached) {
                    Modifier.edgeChameleon(
                        enabled = enabled,
                        forcedIcons = forcedIcons,
                        statusBar = statusBar,
                        navigationBar = navigationBar,
                        state = activeState,
                    )
                } else {
                    Modifier
                }
            Box(modifier.fillMaxSize().background(background))
            if (fullScreenDialog) {
                Dialog(
                    onDismissRequest = {},
                    properties =
                        DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                        ),
                ) {
                    dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
                    Box(
                        Modifier.edgeChameleon(state = dialogState)
                            .fillMaxSize()
                            .background(Color.White)
                    )
                }
            }
            if (dialog) {
                Dialog(onDismissRequest = {}) { Box(Modifier.size(100.dp).background(Color.Gray)) }
            }
        }
    }

    private fun awaitStatusIcons(icons: BarIcons?) {
        rule.waitUntil(TimeoutMillis) { state.statusBarIcons == icons }
    }

    /**
     * Waits longer than a sample takes, for checks that nothing happens. The pause between a
     * request and its sample runs on the clock of the test, which only moves on its own while
     * something changes, so it is moved here as well.
     */
    private fun settle() {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(SettleMillis)
        Thread.sleep(SettleMillis)
        rule.waitForIdle()
    }

    private fun lightStatusBars(): Boolean {
        var result = false
        rule.runOnUiThread { result = controller.isAppearanceLightStatusBars }
        return result
    }

    private fun awaitNavigationIcons(icons: BarIcons?) {
        rule.waitUntil(TimeoutMillis) { state.navigationBarIcons == icons }
    }

    private fun lightNavigationBars(): Boolean {
        var result = false
        rule.runOnUiThread { result = controller.isAppearanceLightNavigationBars }
        return result
    }

    /** Only the buttons of three button navigation can be tapped, and so get their icons chosen. */
    private fun hasNavigationButtons(): Boolean {
        var buttons = false
        rule.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            val tappable = insets?.getInsets(WindowInsetsCompat.Type.tappableElement())
            buttons =
                tappable != null && (tappable.bottom > 0 || tappable.left > 0 || tappable.right > 0)
        }
        return buttons
    }

    private fun supported() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    }

    @Test
    fun whiteContentGetsDarkIconsAndDarkContentGetsLightIcons() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        assertEquals(true, lightStatusBars())

        background = Color.Black
        awaitStatusIcons(BarIcons.Light)
        assertEquals(false, lightStatusBars())

        background = Color.White
        awaitStatusIcons(BarIcons.Dark)
        assertEquals(true, lightStatusBars())
    }

    @Test
    fun aStaticScreenCausesNoSamples() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        settle()
        val samples = state.sampleCount
        Thread.sleep(IdleMillis)
        assertEquals(samples, state.sampleCount)
    }

    @Test
    fun samplesStayWithinTheLimitWhileContentChanges() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        settle()
        val samples = state.sampleCount
        val start = System.currentTimeMillis()
        var dark = false
        while (System.currentTimeMillis() - start < 2_000) {
            dark = !dark
            background = if (dark) Color(0xFF111111) else Color(0xFF131313)
            rule.waitForIdle()
        }
        val elapsedSeconds = (System.currentTimeMillis() - start) / 1000f
        val perSecond = (state.sampleCount - samples) / elapsedSeconds
        assert(perSecond > 0f) { "No samples while the content changed" }
        assert(perSecond <= 11f) { "$perSecond samples per second" }
    }

    @Test
    fun forcedIconsWinAndStopSampling() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        forcedIcons = BarIcons.Light
        awaitStatusIcons(BarIcons.Light)
        assertEquals(false, lightStatusBars())
        settle()

        val samples = state.sampleCount
        background = Color.Gray
        settle()
        assertEquals(samples, state.sampleCount)
        assertEquals(BarIcons.Light, state.statusBarIcons)

        forcedIcons = null
        background = Color.White
        awaitStatusIcons(BarIcons.Dark)
    }

    @Test
    fun disablingRestoresTheIconsOfTheApp() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        assertEquals(true, lightStatusBars())

        enabled = false
        awaitStatusIcons(null)
        assertEquals(false, lightStatusBars())

        val samples = state.sampleCount
        background = Color.Black
        settle()
        assertEquals(samples, state.sampleCount)
    }

    @Test
    fun removingTheModifierRestoresIconsAndScrim() {
        supported()
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        var scrimDuring = true
        awaitStatusIcons(BarIcons.Dark)
        rule.runOnUiThread { scrimDuring = rule.activity.window.isNavigationBarContrastEnforced }
        assertFalse(scrimDuring)

        attached = false
        settle()
        assertEquals(false, lightStatusBars())
        var scrimAfter = !scrimBefore
        rule.runOnUiThread { scrimAfter = rule.activity.window.isNavigationBarContrastEnforced }
        assertEquals(scrimBefore, scrimAfter)
    }

    @Test
    fun excludingTheStatusBarLeavesItAlone() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        assertEquals(true, lightStatusBars())

        statusBar = false
        awaitStatusIcons(null)
        assertEquals(false, lightStatusBars())

        // White content would give dark icons again, if the status bar were still handled
        background = Color.Black
        settle()
        background = Color.White
        settle()
        assertNull(state.statusBarIcons)
        assertEquals(false, lightStatusBars())
    }

    @Test
    fun excludingTheNavigationBarKeepsTheStatusBarIcons() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        assertEquals(true, lightStatusBars())

        navigationBar = false
        rule.waitForIdle()
        // Not even for a moment, as only the navigation bar is handed back
        assertEquals(BarIcons.Dark, state.statusBarIcons)
        assertEquals(true, lightStatusBars())
        settle()
        assertEquals(BarIcons.Dark, state.statusBarIcons)
        assertEquals(true, lightStatusBars())
        assertNull(state.navigationBarIcons)
    }

    @Test
    fun theNavigationBarFollowsTheContentWhenItHasButtons() {
        supported()
        assumeTrue("Needs the navigation buttons", hasNavigationButtons())
        awaitNavigationIcons(BarIcons.Dark)
        assertEquals(true, lightNavigationBars())

        background = Color.Black
        awaitNavigationIcons(BarIcons.Light)
        assertEquals(false, lightNavigationBars())
    }

    @Test
    fun aNewStateGetsTheCurrentIcons() {
        supported()
        awaitStatusIcons(BarIcons.Dark)

        val other = EdgeChameleonState()
        activeState = other
        rule.waitForIdle()

        assertEquals(BarIcons.Dark, other.statusBarIcons)
        assertNull(state.statusBarIcons)
    }

    @Test
    fun aComposeViewBelowTheStatusBarIsNotSampledForIt() {
        supported()
        val belowState = EdgeChameleonState()
        rule.runOnUiThread {
            val activity = rule.activity
            val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            // Pushes the ComposeView down, so that its top is far below the status bar
            root.addView(View(activity), LinearLayout.LayoutParams(MATCH_PARENT, SpacerHeight))
            val composeView =
                ComposeView(activity).apply {
                    setContent {
                        Box(
                            Modifier.edgeChameleon(state = belowState)
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                }
            root.addView(composeView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            activity.setContentView(root)
        }
        settle()

        assertNull(belowState.statusBarIcons)
        if (hasNavigationButtons()) {
            // The view is sampled, just not for a bar that it does not reach
            assertEquals(BarIcons.Light, belowState.navigationBarIcons)
        }
    }

    @Test
    fun aDialogPausesSamplingAndKeepsTheIcons() {
        supported()
        awaitStatusIcons(BarIcons.Dark)
        dialog = true
        rule.waitUntil(TimeoutMillis) { !rule.activity.window.decorView.hasWindowFocus() }
        settle()

        val samples = state.sampleCount
        background = Color.Black
        settle()
        assertEquals(samples, state.sampleCount)
        assertEquals(BarIcons.Dark, state.statusBarIcons)

        dialog = false
        awaitStatusIcons(BarIcons.Light)
    }

    @Test
    fun theModifierInsideAFullScreenDialogSetsTheIconsOfThatDialog() {
        supported()
        // The activity and the dialog need opposite icons
        background = Color.Black
        awaitStatusIcons(BarIcons.Light)
        fullScreenDialog = true
        rule.waitUntil(TimeoutMillis) { dialogState.statusBarIcons == BarIcons.Dark }

        var lightDialogBars = false
        var lightActivityBars = true
        rule.runOnUiThread {
            val window = checkNotNull(dialogWindow)
            lightDialogBars =
                WindowCompat.getInsetsController(window, window.decorView)
                    .isAppearanceLightStatusBars
            lightActivityBars = controller.isAppearanceLightStatusBars
        }
        assertEquals(true, lightDialogBars)
        assertEquals(false, lightActivityBars)
    }

    private companion object {
        const val TimeoutMillis = 5_000L
        const val SettleMillis = 500L
        const val IdleMillis = 2_000L
        const val SpacerHeight = 600
    }
}

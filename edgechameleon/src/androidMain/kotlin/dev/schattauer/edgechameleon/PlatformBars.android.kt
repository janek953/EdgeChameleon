package dev.schattauer.edgechameleon

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

internal actual fun createPlatformBars(node: CompositionLocalConsumerModifierNode): PlatformBars {
    return AndroidBars(node.currentValueOf(LocalView))
}

private class AndroidBars(private val view: View) : PlatformBars {
    private val window: Window? =
        (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivityWindow()

    override val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null

    override val hasFocus: Boolean
        get() = view.hasWindowFocus()

    private var onDrawListener: ViewTreeObserver.OnDrawListener? = null
    private var onFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null

    private var originalLightStatusBars: Boolean? = null
    private var originalLightNavigationBars: Boolean? = null
    private var originalContrastEnforced: Boolean? = null

    override fun attach(onChange: () -> Unit) {
        val window = window ?: return
        // The window only draws when something changed, so this also covers animations and
        // scrolling in child layers, which do not redraw the root node
        val draw = ViewTreeObserver.OnDrawListener { onChange() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) onChange()
        }
        view.viewTreeObserver.addOnDrawListener(draw)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        onDrawListener = draw
        onFocusListener = focus

        // A contrast scrim would be drawn over the navigation bar, and the icons would no longer
        // sit on the sampled content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            originalContrastEnforced = window.isNavigationBarContrastEnforced
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun detach() {
        val window = window ?: return
        val observer = view.viewTreeObserver
        if (observer.isAlive) {
            onDrawListener?.let(observer::removeOnDrawListener)
            onFocusListener?.let(observer::removeOnWindowFocusChangeListener)
        }
        onDrawListener = null
        onFocusListener = null

        restore(statusBar = true, navigationBar = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            originalContrastEnforced?.let { window.isNavigationBarContrastEnforced = it }
            originalContrastEnforced = null
        }
    }

    override fun restore(statusBar: Boolean, navigationBar: Boolean) {
        val window = window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (statusBar) {
            originalLightStatusBars?.let { controller.isAppearanceLightStatusBars = it }
            originalLightStatusBars = null
        }
        if (navigationBar) {
            originalLightNavigationBars?.let { controller.isAppearanceLightNavigationBars = it }
            originalLightNavigationBars = null
        }
    }

    override fun regions(): BarRegions {
        val insets = ViewCompat.getRootWindowInsets(view) ?: return BarRegions(null, null)
        // The bars sit at the edges of the window, wherever this view is placed inside of it
        val width = view.rootView.width.toFloat()
        val height = view.rootView.height.toFloat()

        val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val statusRegion = if (status.top > 0) Rect(0f, 0f, width, status.top.toFloat()) else null

        // Only buttons are tappable. The gesture handle has no tappable inset and adapts on its
        // own.
        val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement())
        val navigationRegion =
            when {
                navigation.bottom > 0 && tappable.bottom > 0 ->
                    Rect(0f, height - navigation.bottom, width, height)
                navigation.left > 0 && tappable.left > 0 ->
                    Rect(0f, 0f, navigation.left.toFloat(), height)
                navigation.right > 0 && tappable.right > 0 ->
                    Rect(width - navigation.right, 0f, width, height)
                else -> null
            }
        return BarRegions(statusRegion, navigationRegion)
    }

    override fun createWindowReader(): WindowReader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return window?.let(::PixelCopyWindowReader)
    }

    override fun setIcons(statusBar: BarIcons?, navigationBar: BarIcons?) {
        val window = window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (statusBar != null) {
            if (originalLightStatusBars == null) {
                originalLightStatusBars = controller.isAppearanceLightStatusBars
            }
            // "Light" bars in the Android API means a light background, and so dark icons
            controller.isAppearanceLightStatusBars = statusBar == BarIcons.Dark
        }
        if (navigationBar != null) {
            if (originalLightNavigationBars == null) {
                originalLightNavigationBars = controller.isAppearanceLightNavigationBars
            }
            controller.isAppearanceLightNavigationBars = navigationBar == BarIcons.Dark
        }
    }
}

private tailrec fun Context.findActivityWindow(): Window? {
    return when (this) {
        is android.app.Activity -> window
        is ContextWrapper -> baseContext.findActivityWindow()
        else -> null
    }
}

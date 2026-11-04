package dev.schattauer.edgechameleon

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/** The window areas of the bars that can be tinted. A null bar is absent or adapts on its own. */
internal class BarRegions(val statusBar: Rect?, val navigationBar: Rect?)

/** The platform side of the modifier: where the bars are, and how to tint them. */
internal interface PlatformBars {
    /** False where the library does nothing, and the modifier then only draws its content. */
    val isSupported: Boolean

    /** False while something else, such as a dialog window, has the focus. */
    val hasFocus: Boolean

    /** Starts reporting possible content and focus changes to [onChange]. */
    fun attach(onChange: () -> Unit)

    /** Stops reporting and restores the settings that [attach] and [setIcons] changed. */
    fun detach()

    /**
     * Gives the icons of the chosen bars back to the app, and leaves the other bar as it is. Does
     * not stop reporting, unlike [detach].
     */
    fun restore(statusBar: Boolean, navigationBar: Boolean)

    /** The current bar areas, in window coordinates. */
    fun regions(): BarRegions

    /** Applies the icons. A null value leaves that bar untouched. */
    fun setIcons(statusBar: BarIcons?, navigationBar: BarIcons?)

    /** A reader for what the window shows, or null where there is none. */
    fun createWindowReader(): WindowReader?
}

internal expect fun createPlatformBars(node: CompositionLocalConsumerModifierNode): PlatformBars

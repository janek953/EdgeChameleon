package dev.schattauer.edgechameleon

import androidx.compose.ui.node.CompositionLocalConsumerModifierNode

/** Does nothing, so the modifier only draws its content on these targets. */
internal actual fun createPlatformBars(node: CompositionLocalConsumerModifierNode): PlatformBars {
    return NoBars
}

private object NoBars : PlatformBars {
    override val isSupported: Boolean = false
    override val hasFocus: Boolean = false

    override fun attach(onChange: () -> Unit) {}

    override fun detach() {}

    override fun restore(statusBar: Boolean, navigationBar: Boolean) {}

    override fun regions(): BarRegions {
        return BarRegions(null, null)
    }

    override fun setIcons(statusBar: BarIcons?, navigationBar: BarIcons?) {}

    override fun createWindowReader(): WindowReader? {
        return null
    }
}

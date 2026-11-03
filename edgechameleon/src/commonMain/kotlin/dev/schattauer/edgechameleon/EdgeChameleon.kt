package dev.schattauer.edgechameleon

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Picks light or dark system bar icons from the content behind the bars. Add it once, to the root
 * of the app content. It needs no other setup, apart from drawing edge-to-edge.
 *
 * Only Compose content inside this modifier is seen, not other windows such as dialogs. Sampling
 * pauses while the window has no focus, and the icons keep their tint until it returns.
 *
 * @param enabled false restores the icons the app had, and stops all work. These are the icons from
 *   before the library first changed them, so changes the app makes to the icons while the modifier
 *   is active are replaced.
 * @param forcedIcons uses these icons on the included bars and stops sampling, for example on a
 *   fullscreen video.
 * @param statusBar whether the status bar is handled.
 * @param navigationBar whether the navigation bar is handled.
 * @param state receives the current result.
 */
@OptIn(ExperimentalEdgeChameleonApi::class)
public fun Modifier.edgeChameleon(
    enabled: Boolean = true,
    forcedIcons: BarIcons? = null,
    statusBar: Boolean = true,
    navigationBar: Boolean = true,
    state: EdgeChameleonState? = null,
): Modifier {
    return edgeChameleonInternal(enabled, forcedIcons, statusBar, navigationBar, state, null)
}

@OptIn(ExperimentalEdgeChameleonApi::class)
private fun Modifier.edgeChameleonInternal(
    enabled: Boolean,
    forcedIcons: BarIcons?,
    statusBar: Boolean,
    navigationBar: Boolean,
    state: EdgeChameleonState?,
    tuning: EdgeChameleonTuning?,
): Modifier {
    return this then
        EdgeChameleonElement(enabled, forcedIcons, statusBar, navigationBar, state, tuning)
}

/** The same as the other [edgeChameleon], with [tuning] for special cases. */
@ExperimentalEdgeChameleonApi
public fun Modifier.edgeChameleon(
    tuning: EdgeChameleonTuning,
    enabled: Boolean = true,
    forcedIcons: BarIcons? = null,
    statusBar: Boolean = true,
    navigationBar: Boolean = true,
    state: EdgeChameleonState? = null,
): Modifier {
    return edgeChameleonInternal(enabled, forcedIcons, statusBar, navigationBar, state, tuning)
}

@OptIn(ExperimentalEdgeChameleonApi::class)
private data class EdgeChameleonElement(
    val enabled: Boolean,
    val forcedIcons: BarIcons?,
    val statusBar: Boolean,
    val navigationBar: Boolean,
    val state: EdgeChameleonState?,
    val tuning: EdgeChameleonTuning?,
) : ModifierNodeElement<EdgeChameleonNode>() {

    override fun create(): EdgeChameleonNode {
        return EdgeChameleonNode(
            enabled,
            forcedIcons,
            statusBar,
            navigationBar,
            state,
            tuning ?: DefaultTuning,
        )
    }

    override fun update(node: EdgeChameleonNode) {
        node.update(enabled, forcedIcons, statusBar, navigationBar, state, tuning ?: DefaultTuning)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "edgeChameleon"
        properties["enabled"] = enabled
        properties["forcedIcons"] = forcedIcons
        properties["statusBar"] = statusBar
        properties["navigationBar"] = navigationBar
    }
}

@OptIn(ExperimentalEdgeChameleonApi::class) private val DefaultTuning = EdgeChameleonTuning()

package dev.schattauer.edgechameleon

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo

/**
 * Tells [edgeChameleon] which icons the content of this element needs, so that a bar that lies
 * completely over hinted content is not sampled at all. Use it on solid content that is known to
 * sit behind a bar, such as a top app bar or a bottom bar with a fixed colour.
 *
 * A bar that is only partly covered, or not at all, is sampled as usual, so a hint can never leave
 * a bar without icons. The content must stay the topmost content behind the bar, as a hint does not
 * look at what is drawn over it. Hints that overlap should be avoided, as the last one in the
 * composition wins. Without an [edgeChameleon] above it, a hint has no effect.
 *
 * @param icons the icons the content needs, [BarIcons.Light] for dark content and [BarIcons.Dark]
 *   for light content. Null gives no hint.
 */
public fun Modifier.edgeChameleonHint(icons: BarIcons?): Modifier {
    return this then EdgeChameleonHintElement(icons)
}

private data class EdgeChameleonHintElement(val icons: BarIcons?) :
    ModifierNodeElement<EdgeChameleonHintNode>() {

    override fun create(): EdgeChameleonHintNode {
        return EdgeChameleonHintNode(icons)
    }

    override fun update(node: EdgeChameleonHintNode) {
        node.update(icons)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "edgeChameleonHint"
        properties["icons"] = icons
    }
}

/** Tells the closest [EdgeChameleonNode] above it about its icons and where it is. */
internal class EdgeChameleonHintNode(private var icons: BarIcons?) :
    Modifier.Node(), TraversableNode {

    override val traverseKey: Any = EdgeChameleonHintKey

    private var owner: EdgeChameleonNode? = null

    override fun onAttach() {
        nearestOwner()?.addHint(this)
    }

    override fun onDetach() {
        owner?.removeHint(this)
        owner = null
    }

    /** The closest [EdgeChameleonNode] above this hint, the only one that it belongs to. */
    fun nearestOwner(): EdgeChameleonNode? {
        return findNearestAncestor(EdgeChameleonKey) as? EdgeChameleonNode
    }

    fun setOwner(owner: EdgeChameleonNode) {
        this.owner = owner
    }

    /** Lets go of [from], unless another node has taken the hint over in the meantime. */
    fun releaseOwner(from: EdgeChameleonNode) {
        if (owner === from) owner = null
    }

    fun update(icons: BarIcons?) {
        if (this.icons == icons) return
        this.icons = icons
        owner?.hintsChanged()
    }

    /** Where the hint is now, or null without icons or while it has no place in the window. */
    fun current(): BarHint? {
        val icons = icons ?: return null
        if (!isAttached) return null
        val coordinates = requireLayoutCoordinates()
        if (!coordinates.isAttached) return null
        return BarHint(coordinates.boundsInWindow(), icons)
    }
}

/** The traverse key of [EdgeChameleonNode], which hints look for. */
internal object EdgeChameleonKey

/** The traverse key of [EdgeChameleonHintNode]. */
internal object EdgeChameleonHintKey

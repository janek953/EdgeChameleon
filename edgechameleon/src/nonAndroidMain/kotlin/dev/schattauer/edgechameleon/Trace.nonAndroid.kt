package dev.schattauer.edgechameleon

internal actual inline fun <T> traceSection(name: String, block: () -> T): T {
    return block()
}

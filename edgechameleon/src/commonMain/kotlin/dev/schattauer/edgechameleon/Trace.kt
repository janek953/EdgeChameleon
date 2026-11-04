package dev.schattauer.edgechameleon

/**
 * Makes [block] show up as a named section in a system trace, to see what the library costs. Only
 * for code that does not suspend, as a section must end on the thread that started it.
 */
internal expect inline fun <T> traceSection(name: String, block: () -> T): T

package dev.schattauer.edgechameleon

import android.os.Trace

internal actual inline fun <T> traceSection(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

package com.polylingy.app

import java.awt.Dimension
import java.awt.Rectangle

/**
 * The smallest window in AWT units, which are not the same on every system:
 * - on Windows AWT applies the minimum in physical pixels, ignoring the display scale (at 175 % a 900-wide minimum
 *   let the window shrink to 514), so the minimum is multiplied by the scale;
 * - on macOS AWT works in points, and the system itself draws them on a Retina screen at twice the pixels: multiplied
 *   by the scale 2.0, a 900×640 minimum became 1800×1280 points, bigger than a laptop screen, and the window could
 *   neither be narrowed nor made shorter.
 *
 * Either way the minimum is never larger than [usable], the part of the screen free of the menu bar, the Dock and the taskbar.
 */
internal fun minimumWindowSize(minWidth: Int, minHeight: Int, scale: Double, isMac: Boolean, usable: Rectangle): Dimension {
    val factor = if (isMac) 1.0 else scale
    return Dimension(
        minOf((minWidth * factor).toInt(), usable.width),
        minOf((minHeight * factor).toInt(), usable.height),
    )
}

/** A window restored larger than the free part of its screen (saved on a bigger monitor) is shrunk to fit it; null when it fits. */
internal fun fittedWindowBounds(bounds: Rectangle, usable: Rectangle): Rectangle? {
    if (bounds.width <= usable.width && bounds.height <= usable.height) return null
    val width = minOf(bounds.width, usable.width)
    val height = minOf(bounds.height, usable.height)
    val x = bounds.x.coerceIn(usable.x, usable.x + usable.width - width)
    val y = bounds.y.coerceIn(usable.y, usable.y + usable.height - height)
    return Rectangle(x, y, width, height)
}

internal fun isMacOS(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

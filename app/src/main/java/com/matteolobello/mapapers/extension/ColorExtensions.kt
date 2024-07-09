package com.matteolobello.mapapers.extension

import android.graphics.Color
import android.support.v4.graphics.ColorUtils

fun Int.manipulateColor(factor: Float): Int {
    val a = Color.alpha(this)
    val r = Math.round(Color.red(this) * factor)
    val g = Math.round(Color.green(this) * factor)
    val b = Math.round(Color.blue(this) * factor)
    return Color.argb(a,
            Math.min(r, 255),
            Math.min(g, 255),
            Math.min(b, 255))
}

fun Int.isLight(): Boolean {
    val red = Color.red(this)
    val green = Color.green(this)
    val blue = Color.blue(this)

    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(red, green, blue, hsl)
    return hsl[2] > 0.75.toFloat()
}
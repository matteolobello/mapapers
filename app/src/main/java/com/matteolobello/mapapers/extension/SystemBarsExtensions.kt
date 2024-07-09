package com.matteolobello.mapapers.extension

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.support.graphics.drawable.ArgbEvaluator
import android.support.v4.content.ContextCompat
import android.view.View
import com.matteolobello.mapapers.R

fun Activity.setDarkStatusBarIcons(on: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (on) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            window.decorView.systemUiVisibility = flags
        }
    }
}

fun Activity.setLightNavBar(on: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (on) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            setNavBarColorWithFade(ContextCompat.getColor(this, R.color.light_system_bars_color))
        } else {
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            window.decorView.systemUiVisibility = flags
        }
    }
}

@SuppressWarnings("all")
fun Activity.setNavBarColorWithFade(colorTo: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val colorFrom = window.navigationBarColor
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = 250
        colorAnimation.addUpdateListener { animator -> window.navigationBarColor = animator.animatedValue as Int }
        colorAnimation.start()
    }
}

@SuppressWarnings("all")
fun Activity.setStatusBarColorWithFade(colorTo: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        val colorFrom = window.statusBarColor
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        colorAnimation.duration = 250
        colorAnimation.addUpdateListener { animator -> window.statusBarColor = animator.animatedValue as Int }
        colorAnimation.start()
    }
}

fun Activity.getStatusBarHeight(): Int {
    val rectangle = Rect()
    val window = window
    window.decorView.getWindowVisibleDisplayFrame(rectangle)
    return rectangle.top
}
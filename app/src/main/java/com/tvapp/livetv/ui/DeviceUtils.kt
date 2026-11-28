package com.tvapp.livetv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Returns true when running on an Android TV / Leanback television device
 * (as opposed to a touchscreen phone or tablet).
 */
fun isTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
        return true
    }
    val pm = context.packageManager
    val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    return hasLeanback && !hasTouch
}

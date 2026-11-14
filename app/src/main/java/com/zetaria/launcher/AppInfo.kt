package com.zetaria.launcher

import android.content.res.Resources
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
) {
    /** Devuelve una copia independiente del icono (un Drawable no debe compartirse entre vistas). */
    fun freshIcon(res: Resources): Drawable = icon.constantState?.newDrawable(res) ?: icon
}

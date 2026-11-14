package com.zetaria.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/** Utilidades visuales compartidas del launcher. */
object Ui {

    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density

    fun alpha(color: Int, a: Int): Int = ColorUtils.setAlphaComponent(color, a)

    /** Marco con esquinas asimetricas (chaflan) estilo panel alienigena. */
    fun frame(ctx: Context, accent: Int): GradientDrawable {
        val big = dp(ctx, 18f)
        val small = dp(ctx, 4f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(big, big, small, small, big, big, small, small)
            setColor(alpha(accent, 22))
            setStroke(dp(ctx, 1f).toInt().coerceAtLeast(1), alpha(accent, 120))
        }
    }

    /** Panel oscuro translucido con borde de energia. */
    fun panel(ctx: Context, accent: Int, fillAlpha: Int = 150): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, 22f)
            setColor(android.graphics.Color.argb(fillAlpha, 5, 8, 20))
            setStroke(dp(ctx, 1f).toInt().coerceAtLeast(1), alpha(accent, 90))
        }
    }

    /** Barra redondeada (asa del cajon). */
    fun pill(ctx: Context, color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(ctx, 50f)
            setColor(color)
        }
    }
}

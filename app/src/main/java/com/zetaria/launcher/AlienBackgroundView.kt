package com.zetaria.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Fondo animado "tecnologia alienigena": nebulosas, campo de estrellas, rejilla hexagonal,
 * anillos orbitales giratorios y una linea de escaneo. Dibujado con Canvas a ~30 fps
 * para ser ligero en un Unisoc T606.
 */
class AlienBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Color principal de energia. Al cambiarlo se reconstruyen los shaders. */
    var accent: Int = 0xFF00FFC8.toInt()
        set(value) {
            field = value
            rebuild()
            invalidate()
        }

    /** Coordenada Y (px) del centro de los anillos; -1 usa 27% del alto. */
    var ringCenterY: Float = -1f
        set(value) {
            field = value
            invalidate()
        }

    private class Star(
        var x: Float,
        var y: Float,
        val r: Float,
        val speed: Float,
        val phase: Float,
        val tinted: Boolean
    )

    private val density = resources.displayMetrics.density
    private val rnd = Random(42)
    private val stars = ArrayList<Star>()

    private val nebulaPaintA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaintB = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hexPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanPaint = Paint()
    private val scanLinePaint = Paint()
    private val vignettePaint = Paint()
    private val arcRect = RectF()
    private val hsv = FloatArray(3)
    private val dash = DashPathEffect(floatArrayOf(4f * density, 14f * density), 0f)

    private var secondary = 0xFFB26BFF.toInt()
    private var nebulaRadius = 0f
    private var hexBitmap: Bitmap? = null
    private var hexTop = 0f
    private var scanHeight = 0f
    private var time = 0f
    private var lastFrame = 0L
    private var running = false

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            postOnAnimation(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrame = 0L
        postOnAnimation(frameRunnable)
    }

    fun stop() {
        running = false
        removeCallbacks(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        stars.clear()
        repeat(90) {
            stars.add(
                Star(
                    x = rnd.nextFloat() * w,
                    y = rnd.nextFloat() * h,
                    r = (0.5f + rnd.nextFloat() * 1.6f) * density,
                    speed = (4f + rnd.nextFloat() * 18f) * density,
                    phase = rnd.nextFloat() * 6.28f,
                    tinted = rnd.nextInt(5) == 0
                )
            )
        }
        rebuild()
    }

    private fun rebuild() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        Color.colorToHSV(accent, hsv)
        hsv[0] = (hsv[0] + 150f) % 360f
        secondary = Color.HSVToColor(hsv)

        nebulaRadius = max(w, h) * 0.55f
        nebulaPaintA.shader = RadialGradient(
            0f, 0f, nebulaRadius,
            intArrayOf(Ui.alpha(accent, 70), Ui.alpha(accent, 0)),
            null, Shader.TileMode.CLAMP
        )
        nebulaPaintB.shader = RadialGradient(
            0f, 0f, nebulaRadius,
            intArrayOf(Ui.alpha(secondary, 60), Ui.alpha(secondary, 0)),
            null, Shader.TileMode.CLAMP
        )

        scanHeight = 140f * density
        scanPaint.shader = LinearGradient(
            0f, 0f, 0f, scanHeight,
            intArrayOf(Ui.alpha(accent, 0), Ui.alpha(accent, 45), Ui.alpha(accent, 0)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        scanLinePaint.color = Ui.alpha(accent, 140)
        scanLinePaint.strokeWidth = 1.5f * density

        vignettePaint.shader = RadialGradient(
            w / 2f, h / 2f, hypot(w.toFloat(), h.toFloat()) / 2f,
            intArrayOf(0x00000000, 0xB8000000.toInt()),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        buildHexBitmap(w, h)
    }

    /** La rejilla hexagonal se dibuja una sola vez en un bitmap (barato por fotograma). */
    private fun buildHexBitmap(w: Int, h: Int) {
        hexBitmap?.recycle()
        hexBitmap = null
        hexTop = h * 0.55f
        val bh = (h - hexTop).toInt()
        if (bh <= 0) return

        val bmp = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            shader = LinearGradient(
                0f, 0f, 0f, bh.toFloat(),
                Ui.alpha(accent, 0), Ui.alpha(accent, 120),
                Shader.TileMode.CLAMP
            )
        }
        val s = 26f * density
        val hh = sqrt(3f) * s
        val cols = (w / (1.5f * s)).toInt() + 2
        val rows = (bh / hh).toInt() + 2
        val path = Path()
        for (c in 0 until cols) {
            for (r in 0 until rows) {
                val cx = c * 1.5f * s
                val cy = r * hh + if (c % 2 == 1) hh / 2f else 0f
                for (i in 0 until 6) {
                    val a = (PI / 3.0 * i).toFloat()
                    val px = cx + s * cos(a)
                    val py = cy + s * sin(a)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
        }
        cv.drawPath(path, p)
        hexBitmap = bmp
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (lastFrame == 0L) {
            lastFrame = now
            return
        }
        val elapsed = now - lastFrame
        if (elapsed < 30) return // ~30 fps
        lastFrame = now
        val dt = min(elapsed, 100L) / 1000f
        time += dt
        if (time > 7200f) time -= 7200f

        val h = height.toFloat()
        val w = width.toFloat()
        for (s in stars) {
            s.y -= s.speed * dt
            if (s.y < -4f) {
                s.y = h + 4f
                s.x = rnd.nextFloat() * w
            }
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(0xFF03040B.toInt())

        // Nebulosas
        c.save()
        c.translate(w * 0.25f + sin(time * 0.20f) * w * 0.10f, h * 0.22f + cos(time * 0.15f) * h * 0.04f)
        c.drawCircle(0f, 0f, nebulaRadius, nebulaPaintA)
        c.restore()

        c.save()
        c.translate(w * 0.80f + cos(time * 0.17f) * w * 0.08f, h * 0.70f + sin(time * 0.13f) * h * 0.05f)
        c.drawCircle(0f, 0f, nebulaRadius, nebulaPaintB)
        c.restore()

        // Estrellas
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(time * 1.6f + s.phase)
            starPaint.color = if (s.tinted) accent else 0xFFFFFFFF.toInt()
            starPaint.alpha = (70 + 185 * tw).toInt()
            c.drawCircle(s.x, s.y, s.r, starPaint)
        }

        // Rejilla hexagonal pulsante
        hexBitmap?.let {
            hexPaint.alpha = (150 + 80 * sin(time * 1.2f)).toInt().coerceIn(0, 255)
            c.drawBitmap(it, 0f, hexTop, hexPaint)
        }

        // Anillos orbitales
        val cx = w / 2f
        val cy = if (ringCenterY > 0f) ringCenterY else h * 0.27f
        val base = w * 0.42f
        val r1 = base
        val r2 = base * 0.84f
        val r3 = base * 0.66f

        ringPaint.pathEffect = dash
        ringPaint.strokeWidth = 1.2f * density
        ringPaint.color = Ui.alpha(accent, 90)
        c.save()
        c.rotate(time * 6f, cx, cy)
        c.drawCircle(cx, cy, r1, ringPaint)
        c.restore()
        ringPaint.pathEffect = null

        ringPaint.strokeWidth = 3f * density
        ringPaint.color = Ui.alpha(accent, 150)
        arcRect.set(cx - r2, cy - r2, cx + r2, cy + r2)
        c.save()
        c.rotate(-time * 12f, cx, cy)
        for (i in 0 until 4) {
            c.drawArc(arcRect, i * 90f + 8f, 52f, false, ringPaint)
        }
        c.restore()

        ringPaint.strokeWidth = 1.5f * density
        ringPaint.color = Ui.alpha(accent, 120)
        c.save()
        c.rotate(time * 4f, cx, cy)
        for (i in 0 until 36) {
            val len = if (i % 3 == 0) 14f * density else 7f * density
            c.drawLine(cx, cy - r3, cx, cy - r3 + len, ringPaint)
            c.rotate(10f, cx, cy)
        }
        c.restore()

        // Satelite orbitando
        val ang = time * 0.9f
        val dx = cx + r2 * cos(ang)
        val dy = cy + r2 * sin(ang)
        dotPaint.color = Ui.alpha(secondary, 70)
        c.drawCircle(dx, dy, 9f * density, dotPaint)
        dotPaint.color = secondary
        c.drawCircle(dx, dy, 3.5f * density, dotPaint)

        // Linea de escaneo
        val scanY = ((time * 0.09f) % 1f) * (h + scanHeight) - scanHeight
        c.save()
        c.translate(0f, scanY)
        c.drawRect(0f, 0f, w, scanHeight, scanPaint)
        c.drawLine(0f, scanHeight / 2f, w, scanHeight / 2f, scanLinePaint)
        c.restore()

        // Vineta
        c.drawRect(0f, 0f, w, h, vignettePaint)
    }
}

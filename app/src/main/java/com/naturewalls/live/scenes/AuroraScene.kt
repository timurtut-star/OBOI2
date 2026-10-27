package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.sin

/** Polar night: stars, aurora curtains of vertical rays, snowy mountains and a frozen lake. */
class AuroraScene : Scene {
    override val id: String = "aurora"
    override val title: String = "Северное сияние"

    private val TWO_PI = (Math.PI * 2.0).toFloat()

    private var w = 0f
    private var h = 0f
    private var lakeY = 0f
    private var t = 0f

    // parallax
    private var offTarget = 0.5f
    private var offCur = 0.5f

    // touch
    private var touchX = 0f
    private var touchBoost = 0f

    // ---- sky / stars ----
    private val skyPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starCount = 120
    private val starX = FloatArray(starCount)   // 0..1 (+margin handled in draw)
    private val starY = FloatArray(starCount)   // 0..1 of sky height
    private val starPhase = FloatArray(starCount)
    private val starSpeed = FloatArray(starCount)
    private val starSize = FloatArray(starCount)
    private val starTint = IntArray(starCount)

    // ---- curtains ----
    private val curtainCount = 4
    private val strips = 80
    private val cBase = floatArrayOf(0.30f, 0.42f, 0.36f, 0.50f)   // bottom edge y (fraction of h)
    private val cHeight = floatArrayOf(0.26f, 0.30f, 0.34f, 0.28f) // curtain height (fraction of h)
    private val cAmp = floatArrayOf(0.035f, 0.045f, 0.04f, 0.03f)
    private val cBright = floatArrayOf(0.60f, 0.85f, 0.75f, 0.65f)
    private val cPhase = floatArrayOf(0.0f, 1.7f, 3.1f, 4.6f)
    private val cSpeed = floatArrayOf(0.13f, 0.10f, 0.16f, 0.08f)
    private val cRayFreq = floatArrayOf(31f, 24f, 37f, 19f)
    private val cColors = intArrayOf(
        Color.rgb(70, 255, 150),   // green
        Color.rgb(50, 230, 210),   // teal
        Color.rgb(150, 90, 255),   // violet
        Color.rgb(90, 255, 120)    // light green
    )
    private val curtainPaints = Array(curtainCount) { Paint() }
    private val additive = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

    // ---- landscape ----
    private val farPath = Path()
    private val nearPath = Path()
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reflectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lakePaint = Paint()
    private val lakeFadePaint = Paint()
    private val auroraReflectPaint = Paint()
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reflectStrips = 40

    init {
        val r = Random(42)
        for (i in 0 until starCount) {
            starX[i] = r.nextFloat()
            starY[i] = r.nextFloat()
            starPhase[i] = r.nextFloat() * TWO_PI
            starSpeed[i] = 0.6f + r.nextFloat() * 2.4f
            starSize[i] = 0.5f + r.nextFloat() * r.nextFloat() * 1.6f
            starTint[i] = when (r.nextInt(5)) {
                0 -> Color.rgb(190, 210, 255)
                1 -> Color.rgb(255, 235, 210)
                else -> Color.WHITE
            }
        }
        for (i in 0 until curtainCount) curtainPaints[i].xfermode = additive
        reflectPaint.style = Paint.Style.FILL
        farPaint.style = Paint.Style.FILL
        nearPaint.style = Paint.Style.FILL
        nearPaint.color = Color.rgb(3, 6, 11)
        sheenPaint.style = Paint.Style.FILL
        sheenPaint.color = Color.argb(14, 170, 210, 255)
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        lakeY = h * 0.88f

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, lakeY,
            intArrayOf(
                Color.rgb(2, 4, 12),
                Color.rgb(5, 12, 30),
                Color.rgb(10, 26, 48)
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )

        // Unit-space gradients: y=0 is the top of the ray, y=1 its bottom edge.
        for (i in 0 until curtainCount) {
            val c = cColors[i]
            val rr = Color.red(c)
            val gg = Color.green(c)
            val bb = Color.blue(c)
            curtainPaints[i].shader = LinearGradient(
                0f, 0f, 0f, 1f,
                intArrayOf(
                    Color.argb(0, rr, gg, bb),
                    Color.argb(50, rr, gg, bb),
                    Color.argb(150, rr, gg, bb),
                    Color.argb(235, minOf(255, rr + 60), minOf(255, gg + 40), minOf(255, bb + 60)),
                    Color.argb(0, rr, gg, bb)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 0.93f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        // Aurora reflection in the lake: unit gradient, y=0 at lake edge fading down.
        auroraReflectPaint.shader = LinearGradient(
            0f, 0f, 0f, 1f,
            intArrayOf(Color.argb(200, 70, 255, 170), Color.argb(0, 70, 255, 170)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        farPaint.shader = LinearGradient(
            0f, h * 0.68f, 0f, lakeY,
            Color.rgb(34, 50, 78), Color.rgb(8, 14, 26),
            Shader.TileMode.CLAMP
        )
        reflectPaint.color = Color.argb(70, 20, 34, 58)

        lakePaint.shader = LinearGradient(
            0f, lakeY, 0f, h,
            Color.rgb(9, 20, 38), Color.rgb(3, 7, 14),
            Shader.TileMode.CLAMP
        )
        lakeFadePaint.shader = LinearGradient(
            0f, lakeY, 0f, h,
            Color.argb(0, 3, 7, 14), Color.argb(190, 3, 7, 14),
            Shader.TileMode.CLAMP
        )

        buildLandscape()
    }

    private fun buildLandscape() {
        val r = Random(7)
        val x0 = -w * 0.12f
        val x1 = w * 1.12f

        // far snowy ridge: sum of sines + noise, sampled
        farPath.rewind()
        farPath.moveTo(x0, lakeY)
        val n = 48
        val step = (x1 - x0) / n
        for (i in 0..n) {
            val x = x0 + step * i
            val u = x / w
            val ridge = 0.5f + 0.5f * sin(u * 7.3f + 0.8f) * 0.6f +
                0.25f * sin(u * 17.1f + 2.0f) + 0.12f * (r.nextFloat() - 0.5f)
            val y = lakeY - h * (0.05f + 0.13f * ridge.coerceIn(0f, 1.2f))
            farPath.lineTo(x, y)
        }
        farPath.lineTo(x1, lakeY)
        farPath.close()

        // near treeline: jagged spruce silhouettes
        nearPath.rewind()
        nearPath.moveTo(x0, lakeY)
        var x = x0
        while (x < x1) {
            val tw = w * (0.018f + r.nextFloat() * 0.022f)
            val th = h * (0.025f + r.nextFloat() * 0.045f)
            // spruce: little shoulders then tip
            nearPath.lineTo(x, lakeY - th * 0.15f)
            nearPath.lineTo(x + tw * 0.30f, lakeY - th * 0.35f)
            nearPath.lineTo(x + tw * 0.18f, lakeY - th * 0.35f)
            nearPath.lineTo(x + tw * 0.40f, lakeY - th * 0.65f)
            nearPath.lineTo(x + tw * 0.30f, lakeY - th * 0.65f)
            nearPath.lineTo(x + tw * 0.50f, lakeY - th)
            nearPath.lineTo(x + tw * 0.70f, lakeY - th * 0.65f)
            nearPath.lineTo(x + tw * 0.60f, lakeY - th * 0.65f)
            nearPath.lineTo(x + tw * 0.82f, lakeY - th * 0.35f)
            nearPath.lineTo(x + tw * 0.70f, lakeY - th * 0.35f)
            nearPath.lineTo(x + tw, lakeY - th * 0.15f)
            x += tw
        }
        nearPath.lineTo(x1, lakeY)
        nearPath.close()
    }

    override fun update(dt: Float) {
        t += dt
        offCur += (offTarget - offCur) * minOf(1f, dt * 4f)
        if (touchBoost > 0f) {
            touchBoost -= dt * 0.7f
            if (touchBoost < 0f) touchBoost = 0f
        }
    }

    /** Ray brightness 0..1.2 of curtain c at horizontal position u (0..1) */
    private fun ray(c: Int, u: Float, x: Float): Float {
        val a = 0.5f + 0.5f * sin(u * cRayFreq[c] + t * (0.5f + cSpeed[c]) + cPhase[c])
        val b = 0.6f + 0.4f * sin(u * cRayFreq[c] * 0.37f - t * 0.31f + cPhase[c] * 2f)
        // slow large-scale drifting envelope so parts of the curtain fade in and out
        val env = 0.55f + 0.45f * sin(u * 3.1f + t * cSpeed[c] * 1.3f + cPhase[c] * 1.9f)
        var v = (0.25f + 0.75f * a * a) * b * env * cBright[c]
        if (touchBoost > 0f) {
            val d = abs(x - touchX) / (w * 0.3f)
            if (d < 1f) v += touchBoost * (1f - d) * (1f - d) * 0.9f
        }
        return v
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        val shiftBase = (0.5f - offCur) * w * 0.08f

        // sky
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // stars
        val sShift = shiftBase * 0.2f
        val skyH = lakeY * 0.92f
        for (i in 0 until starCount) {
            val tw = 0.5f + 0.5f * sin(t * starSpeed[i] + starPhase[i])
            val a = 0.25f + 0.75f * tw * tw
            starPaint.color = starTint[i]
            starPaint.alpha = (a * 255f).toInt().coerceIn(0, 255)
            val x = (starX[i] * 1.1f - 0.05f) * w + sShift
            canvas.drawCircle(x, starY[i] * skyH, starSize[i] * (w / 1080f) * (0.8f + 0.4f * tw) + 0.3f, starPaint)
        }

        // aurora curtains
        val cShift = shiftBase * 0.5f
        val span = w * 1.2f
        val x0 = -w * 0.1f
        val sw = span / strips
        for (c in 0 until curtainCount) {
            val p = curtainPaints[c]
            val sp = cSpeed[c]
            val ph = cPhase[c]
            val baseY = h * cBase[c]
            val amp = h * cAmp[c]
            val curtH = h * cHeight[c]
            for (i in 0 until strips) {
                val x = x0 + sw * i
                val u = x / w
                val v = ray(c, u, x + cShift)
                if (v <= 0.02f) continue
                val by = baseY +
                    amp * sin(u * 5.0f + t * sp + ph) +
                    amp * 0.55f * sin(u * 11.0f - t * sp * 1.7f + ph * 1.3f) +
                    amp * 0.25f * sin(u * 23.0f + t * sp * 2.9f)
                val hh = curtH * (0.75f + 0.25f * sin(u * 13.0f + t * 0.6f + ph)) * (0.8f + 0.3f * minOf(v, 1f))
                p.alpha = (v * 255f).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(x + cShift, by - hh)
                canvas.scale(sw + 1.5f, hh)
                canvas.drawRect(0f, 0f, 1f, 1f, p)
                canvas.restore()
            }
        }

        // lake + reflections
        canvas.drawRect(0f, lakeY, w, h, lakePaint)
        canvas.save()
        canvas.clipRect(0f, lakeY, w, h)
        val rsw = span / reflectStrips
        val refH = (h - lakeY) * 0.9f
        for (i in 0 until reflectStrips) {
            val x = x0 + rsw * i
            val u = x / w
            val v = (ray(0, u, x + cShift) + ray(1, u, x + cShift) * 0.7f) * 0.22f
            if (v <= 0.01f) continue
            auroraReflectPaint.alpha = (v * 255f).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x + cShift, lakeY)
            canvas.scale(rsw + 1.5f, refH * (0.5f + 0.5f * minOf(v * 3f, 1f)))
            canvas.drawRect(0f, 0f, 1f, 1f, auroraReflectPaint)
            canvas.restore()
        }
        // mirrored landscape
        canvas.save()
        canvas.translate(shiftBase * 0.8f, lakeY)
        canvas.scale(1f, -0.9f)
        canvas.drawPath(farPath, reflectPaint)
        canvas.restore()
        canvas.save()
        canvas.translate(shiftBase * 1.2f, lakeY)
        canvas.scale(1f, -1f)
        canvas.drawPath(nearPath, reflectPaint)
        canvas.restore()
        // ice sheen lines
        for (i in 0 until 6) {
            val yy = lakeY + (h - lakeY) * (0.12f + 0.15f * i)
            val shimmer = 0.5f + 0.5f * sin(t * 0.4f + i * 1.9f)
            sheenPaint.alpha = (10f + 18f * shimmer).toInt()
            canvas.drawRect(w * (0.1f + 0.05f * i), yy, w * (0.6f + 0.06f * i), yy + 2f, sheenPaint)
        }
        canvas.drawRect(0f, lakeY, w, h, lakeFadePaint)
        canvas.restore()

        // mountains and treeline
        canvas.save()
        canvas.translate(shiftBase * 0.8f, 0f)
        canvas.drawPath(farPath, farPaint)
        canvas.restore()
        canvas.save()
        canvas.translate(shiftBase * 1.2f, 0f)
        canvas.drawPath(nearPath, nearPaint)
        canvas.restore()
    }

    override fun onOffset(xOffset: Float) {
        offTarget = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        touchX = x
        touchBoost = 1f
    }
}

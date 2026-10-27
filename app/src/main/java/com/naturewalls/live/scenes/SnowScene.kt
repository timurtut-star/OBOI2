package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sin

/** Winter dusk: gradient sky, snowy pine silhouettes on layered hills, 3 layers of soft snowflakes. */
class SnowScene : Scene {
    override val id: String = "snow"
    override val title: String = "Снегопад"

    private companion object {
        const val LAYERS = 3
        const val N0 = 100
        const val N1 = 70
        const val N2 = 40
        const val TOTAL = N0 + N1 + N2
        const val TWO_PI = 6.2831855f
    }

    private var w = 0f
    private var h = 0f

    // flakes (sorted by layer: 0 far, 1 mid, 2 near)
    private val fx = FloatArray(TOTAL)
    private val fy = FloatArray(TOTAL)
    private val fr = FloatArray(TOTAL)      // radius in px
    private val fv = FloatArray(TOTAL)      // fall speed px/s
    private val fph = FloatArray(TOTAL)     // sway phase
    private val fsw = FloatArray(TOTAL)     // sway amplitude px
    private val fsp = FloatArray(TOTAL)     // sway speed rad/s
    private val fal = FloatArray(TOTAL)     // alpha 0..1
    private val layerStart = intArrayOf(0, N0, N0 + N1, TOTAL)
    private val layerParallax = floatArrayOf(0.03f, 0.07f, 0.14f)
    private val layerWind = floatArrayOf(0.35f, 0.7f, 1.2f)

    private var rnd = 12345L
    private var time = 0f
    private var offTarget = 0.5f
    private var offSmooth = 0.5f
    private var wind = 0f   // px/s current extra horizontal wind

    private val skyPaint = Paint()
    private val glowPaint = Paint()
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val hillPaths = Array(LAYERS) { Path() }
    private val capPaths = Array(LAYERS) { Path() }
    private val hillColors = intArrayOf(
        Color.rgb(78, 84, 128),
        Color.rgb(52, 58, 98),
        Color.rgb(30, 34, 66)
    )
    private val capColors = intArrayOf(
        Color.argb(90, 190, 200, 235),
        Color.argb(120, 200, 210, 240),
        Color.argb(160, 215, 225, 248)
    )
    private val hillParallax = floatArrayOf(0.04f, 0.09f, 0.16f)

    private fun rand(): Float {
        rnd = rnd * 6364136223846793005L + 1442695040888963407L
        return ((rnd ushr 40).toInt() and 0xFFFFFF) / 16777216f
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.rgb(24, 26, 62),
                Color.rgb(62, 58, 112),
                Color.rgb(112, 108, 160),
                Color.rgb(170, 168, 205)
            ),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = LinearGradient(
            0f, h * 0.55f, 0f, h * 0.85f,
            Color.argb(0, 255, 190, 200), Color.argb(50, 255, 190, 200),
            Shader.TileMode.CLAMP
        )

        buildHills()
        initFlakes()
    }

    private fun ridgeY(layer: Int, x: Float): Float {
        val base = when (layer) {
            0 -> 0.70f
            1 -> 0.78f
            else -> 0.88f
        } * h
        val amp = when (layer) {
            0 -> 0.035f
            1 -> 0.045f
            else -> 0.04f
        } * h
        val k = when (layer) {
            0 -> 1.3f
            1 -> 1.9f
            else -> 1.1f
        }
        val u = x / w
        val ph = layer * 1.7f
        return base + amp * sin(u * TWO_PI * k * 0.5f + ph) + amp * 0.4f * sin(u * TWO_PI * k * 1.3f + ph * 2f)
    }

    private fun addPine(path: Path, cap: Path, cx: Float, baseY: Float, ph: Float, pw: Float) {
        // three stacked tiers
        val tiers = 3
        val tierH = ph / (tiers * 0.7f + 0.3f)
        var top = baseY - ph
        // trunk-less silhouette: stacked triangles, widths grow downwards
        for (i in 0 until tiers) {
            val ty = top + tierH * 0.7f * i
            val by = ty + tierH
            val hw = pw * 0.5f * (0.45f + 0.275f * i)
            path.moveTo(cx, ty)
            path.lineTo(cx + hw, by)
            path.lineTo(cx - hw, by)
            path.close()
            // snow on the upper part of the tier
            val sy = ty + tierH * 0.55f
            val sw = hw * 0.55f
            cap.moveTo(cx, ty)
            cap.lineTo(cx + sw, sy)
            cap.lineTo(cx - sw, sy)
            cap.close()
        }
        // short trunk
        path.addRect(cx - pw * 0.04f, baseY - 1f, cx + pw * 0.04f, baseY + ph * 0.06f, Path.Direction.CW)
        top = 0f
    }

    private fun buildHills() {
        val x0 = -0.25f * w
        val x1 = 1.25f * w
        val step = w / 30f
        for (l in 0 until LAYERS) {
            val p = hillPaths[l]
            val c = capPaths[l]
            p.reset()
            c.reset()
            var x = x0
            p.moveTo(x0, h + 2f)
            p.lineTo(x0, ridgeY(l, x0))
            while (x <= x1) {
                p.lineTo(x, ridgeY(l, x))
                x += step
            }
            p.lineTo(x1, ridgeY(l, x1))
            p.lineTo(x1, h + 2f)
            p.close()

            // pines
            val density = when (l) { 0 -> 26; 1 -> 17; else -> 9 }
            val sizeBase = when (l) { 0 -> 0.07f; 1 -> 0.11f; else -> 0.17f } * h
            var px = x0 + rand() * w * 0.03f
            val span = (x1 - x0) / density
            var i = 0
            while (px < x1) {
                val ph = sizeBase * (0.75f + rand() * 0.6f)
                val pw = ph * (0.42f + rand() * 0.12f)
                val by = ridgeY(l, px) + ph * 0.05f
                addPine(p, c, px, by, ph, pw)
                px += span * (0.6f + rand() * 0.8f)
                i++
            }
        }
    }

    private fun initFlakes() {
        for (l in 0 until LAYERS) {
            for (i in layerStart[l] until layerStart[l + 1]) {
                fx[i] = rand() * w
                fy[i] = rand() * h
                respawnParams(i, l)
            }
        }
    }

    private fun respawnParams(i: Int, l: Int) {
        val s = h / 2400f
        when (l) {
            0 -> {
                fr[i] = (1.2f + rand() * 1.0f) * s
                fv[i] = (60f + rand() * 40f) * s
                fal[i] = 0.35f + rand() * 0.2f
                fsw[i] = (8f + rand() * 8f) * s
            }
            1 -> {
                fr[i] = (2.4f + rand() * 1.6f) * s
                fv[i] = (130f + rand() * 70f) * s
                fal[i] = 0.5f + rand() * 0.25f
                fsw[i] = (14f + rand() * 12f) * s
            }
            else -> {
                fr[i] = (4.5f + rand() * 3.5f) * s
                fv[i] = (260f + rand() * 120f) * s
                fal[i] = 0.55f + rand() * 0.3f
                fsw[i] = (22f + rand() * 20f) * s
            }
        }
        fph[i] = rand() * TWO_PI
        fsp[i] = 0.6f + rand() * 1.2f
    }

    override fun update(dt: Float) {
        if (w <= 0f) return
        time += dt
        offSmooth += (offTarget - offSmooth) * minOf(1f, dt * 5f)
        // wind decays back to a gentle drift
        wind -= wind * minOf(1f, dt * 1.6f)

        for (l in 0 until LAYERS) {
            val lw = layerWind[l]
            for (i in layerStart[l] until layerStart[l + 1]) {
                fy[i] += fv[i] * dt
                fx[i] += wind * lw * dt + 6f * lw * dt
                if (fy[i] - fr[i] * 2f > h) {
                    fy[i] = -fr[i] * 2f
                    fx[i] = rand() * w
                    respawnParams(i, l)
                }
                if (fx[i] > w) fx[i] -= w
                else if (fx[i] < 0f) fx[i] += w
            }
        }
    }

    private fun drawFlakes(canvas: Canvas, l: Int) {
        val shift = (0.5f - offSmooth) * w * layerParallax[l] * 4f
        for (i in layerStart[l] until layerStart[l + 1]) {
            var dx = fx[i] + sin(time * fsp[i] + fph[i]) * fsw[i] + shift
            dx %= w
            if (dx < 0f) dx += w
            val r = fr[i]
            val a = fal[i]
            // soft halo
            flakePaint.color = Color.argb((a * 55f).toInt(), 235, 240, 255)
            canvas.drawCircle(dx, fy[i], r * 2.1f, flakePaint)
            // core
            flakePaint.color = Color.argb((a * 255f).toInt(), 245, 248, 255)
            canvas.drawCircle(dx, fy[i], r, flakePaint)
        }
    }

    private fun drawHills(canvas: Canvas, l: Int) {
        val shift = (0.5f - offSmooth) * w * hillParallax[l]
        canvas.save()
        canvas.translate(shift, 0f)
        hillPaint.color = hillColors[l]
        canvas.drawPath(hillPaths[l], hillPaint)
        snowPaint.color = capColors[l]
        canvas.drawPath(capPaths[l], snowPaint)
        canvas.restore()
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        canvas.drawRect(0f, h * 0.55f, w, h, glowPaint)

        drawFlakes(canvas, 0)
        drawHills(canvas, 0)
        drawHills(canvas, 1)
        drawFlakes(canvas, 1)
        drawHills(canvas, 2)
        drawFlakes(canvas, 2)
    }

    override fun onOffset(xOffset: Float) {
        offTarget = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        // blow flakes away from the touch side
        val dir = if (x < w * 0.5f) 1f else -1f
        val kick = 260f * (h / 2400f).coerceAtLeast(0.5f)
        wind += dir * kick
        val max = 700f
        if (abs(wind) > max) wind = if (wind > 0f) max else -max
    }
}

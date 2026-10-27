package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Sky that follows the real local time: gradient, sun/moon arc, stars, drifting clouds, hills. */
class SkyScene : Scene {
    override val id: String = "sky"
    override val title: String = "Небо и облака"

    private companion object {
        const val BANDS = 96
        const val STARS = 130
        const val LAYERS = 3
        const val CLUSTERS = 11
        const val PUFFS = 7
        const val HILLS = 3
        const val TWO_PI = (2.0 * PI).toFloat()
        const val PI_F = PI.toFloat()

        // Sky keyframes (hours) with top / bottom colors.
        val KEY_H = floatArrayOf(0f, 5f, 6f, 7.5f, 12f, 16.5f, 18.5f, 20f, 21.5f, 24f)
        val KEY_TOP = intArrayOf(
            Color.rgb(6, 10, 32), Color.rgb(6, 10, 32), Color.rgb(70, 80, 140),
            Color.rgb(90, 160, 230), Color.rgb(50, 130, 230), Color.rgb(60, 135, 225),
            Color.rgb(60, 70, 140), Color.rgb(25, 25, 70), Color.rgb(6, 10, 32), Color.rgb(6, 10, 32)
        )
        val KEY_BOT = intArrayOf(
            Color.rgb(16, 24, 56), Color.rgb(16, 24, 56), Color.rgb(250, 150, 120),
            Color.rgb(200, 225, 245), Color.rgb(150, 205, 250), Color.rgb(170, 210, 245),
            Color.rgb(255, 130, 90), Color.rgb(110, 60, 90), Color.rgb(16, 24, 56), Color.rgb(16, 24, 56)
        )

        val HILL_DAY = intArrayOf(Color.rgb(120, 176, 120), Color.rgb(84, 150, 84), Color.rgb(52, 120, 62))
        val HILL_NIGHT = intArrayOf(Color.rgb(22, 38, 50), Color.rgb(14, 28, 36), Color.rgb(8, 18, 24))
        val HILL_WARM = Color.rgb(200, 110, 90)

        val LAYER_START = intArrayOf(0, 4, 8)
        val LAYER_END = intArrayOf(4, 8, 11)
        val LAYER_SIZE = floatArrayOf(0.16f, 0.24f, 0.34f)
        val LAYER_Y_MIN = floatArrayOf(0.07f, 0.14f, 0.22f)
        val LAYER_Y_MAX = floatArrayOf(0.25f, 0.38f, 0.48f)
        val LAYER_SPEED = floatArrayOf(0.004f, 0.009f, 0.016f)
        val LAYER_ALPHA = floatArrayOf(0.45f, 0.60f, 0.75f)
        val LAYER_PARALLAX = floatArrayOf(0.03f, 0.06f, 0.10f)
        val LAYER_PUSH = floatArrayOf(0.5f, 0.8f, 1.1f)
        val LAYER_DAY_R = intArrayOf(255, 250, 240)
        val LAYER_DAY_G = intArrayOf(255, 252, 245)
        val LAYER_DAY_B = intArrayOf(255, 255, 252)
        val HILL_PARALLAX = floatArrayOf(0.04f, 0.07f, 0.11f)
    }

    private var w = 0f
    private var h = 0f

    // Time
    private val cal: Calendar = Calendar.getInstance()
    private var hour = 12f
    private var timeAcc = 10f
    private var night = 0f
    private var twilight = 0f
    private var clock = 0f

    // Parallax
    private var offTarget = 0.5f
    private var offCur = 0.5f

    // Sky
    private val bandColors = IntArray(BANDS)
    private val skyPaint = Paint()

    // Stars
    private val starX = FloatArray(STARS)
    private val starY = FloatArray(STARS)
    private val starR = FloatArray(STARS)
    private val starPh = FloatArray(STARS)
    private val starSp = FloatArray(STARS)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Sun / moon
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sunR = 1f
    private var sunGlowR = 1f
    private var moonR = 1f
    private var moonGlowR = 1f

    // Clouds
    private val cloudPaint = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val cloudColors = IntArray(3)
    private val cloudPos = floatArrayOf(0f, 0.5f, 1f)
    private val cLayer = IntArray(CLUSTERS)
    private val cX = FloatArray(CLUSTERS)
    private val cY = FloatArray(CLUSTERS)
    private val cS = FloatArray(CLUSTERS)
    private val cV = FloatArray(CLUSTERS)
    private val cPush = FloatArray(CLUSTERS)
    private val pDx = FloatArray(CLUSTERS * PUFFS)
    private val pDy = FloatArray(CLUSTERS * PUFFS)
    private val pR = FloatArray(CLUSTERS * PUFFS)

    // Hills
    private val hillPath = Array(HILLS) { Path() }
    private val hillPaint = Array(HILLS) { Paint(Paint.ANTI_ALIAS_FLAG) }

    init {
        val rnd = Random(42)
        for (i in 0 until STARS) {
            starX[i] = rnd.nextFloat()
            starY[i] = rnd.nextFloat() * 0.7f
            starR[i] = 0.6f + rnd.nextFloat() * rnd.nextFloat() * 2.2f
            starPh[i] = rnd.nextFloat() * TWO_PI
            starSp[i] = 0.8f + rnd.nextFloat() * 2.2f
        }
        for (c in 0 until CLUSTERS) {
            for (j in 0 until PUFFS) {
                val t = j / (PUFFS - 1).toFloat()
                val k = abs(t * 2f - 1f)
                val idx = c * PUFFS + j
                val r = (0.55f - 0.3f * k) * (0.85f + 0.3f * rnd.nextFloat())
                pDx[idx] = (t * 2f - 1f) + (rnd.nextFloat() - 0.5f) * 0.24f
                pDy[idx] = -(1f - k) * 0.3f + (rnd.nextFloat() - 0.5f) * 0.12f
                pR[idx] = r
            }
        }
        for (l in 0 until LAYERS) {
            for (c in LAYER_START[l] until LAYER_END[l]) cLayer[c] = l
        }
        for (p in cloudPaint) p.shader = null
        starPaint.color = Color.WHITE
        moonPaint.color = Color.rgb(235, 238, 250)
        craterPaint.color = Color.rgb(207, 212, 232)
        for (p in hillPaint) p.style = Paint.Style.FILL
        skyPaint.style = Paint.Style.FILL
        recomputeTime()
    }

    // ---------------------------------------------------------------- helpers

    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val tt = if (t < 0f) 0f else if (t > 1f) 1f else t
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun bump(x: Float, center: Float, width: Float): Float =
        max(0f, 1f - abs(x - center) / width)

    private fun nightFactor(hh: Float): Float = when {
        hh <= 5f -> 1f
        hh < 7f -> 1f - (hh - 5f) / 2f
        hh <= 18f -> 0f
        hh < 21f -> (hh - 18f) / 3f
        else -> 1f
    }

    // ---------------------------------------------------------------- time

    /** Reads the real clock (called at most once per second) and refreshes tinted colors. */
    private fun recomputeTime() {
        cal.timeInMillis = System.currentTimeMillis()
        hour = cal.get(Calendar.HOUR_OF_DAY) +
            cal.get(Calendar.MINUTE) / 60f +
            cal.get(Calendar.SECOND) / 3600f
        refreshColors()
    }

    private fun refreshColors() {
        night = nightFactor(hour)
        twilight = max(bump(hour, 6f, 1.5f), bump(hour, 18.3f, 1.6f))

        // Sky bands
        var i = 0
        while (i < KEY_H.size - 2 && hour > KEY_H[i + 1]) i++
        val span = KEY_H[i + 1] - KEY_H[i]
        val f = if (span > 0f) ((hour - KEY_H[i]) / span).coerceIn(0f, 1f) else 0f
        val top = lerpColor(KEY_TOP[i], KEY_TOP[i + 1], f)
        val bot = lerpColor(KEY_BOT[i], KEY_BOT[i + 1], f)
        for (b in 0 until BANDS) {
            val t = min(1f, (b / (BANDS - 1).toFloat()) / 0.8f)
            bandColors[b] = lerpColor(top, bot, t)
        }

        // Hills
        for (k in 0 until HILLS) {
            var c = lerpColor(HILL_DAY[k], HILL_NIGHT[k], night)
            c = lerpColor(c, HILL_WARM, twilight * 0.3f * (1f - night * 0.5f))
            hillPaint[k].color = c
        }

        // Cloud tints (rebuilt once per second; shader is a unit-radius radial gradient)
        if (w > 0f) {
            for (l in 0 until LAYERS) {
                var r = LAYER_DAY_R[l].toFloat()
                var g = LAYER_DAY_G[l].toFloat()
                var bl = LAYER_DAY_B[l].toFloat()
                r = lerpF(r, 255f, twilight); g = lerpF(g, 175f, twilight); bl = lerpF(bl, 165f, twilight)
                r = lerpF(r, 80f, night); g = lerpF(g, 92f, night); bl = lerpF(bl, 125f, night)
                val a = LAYER_ALPHA[l] * (1f - 0.3f * night)
                val ri = r.toInt(); val gi = g.toInt(); val bi = bl.toInt()
                cloudColors[0] = Color.argb((a * 255f).toInt(), ri, gi, bi)
                cloudColors[1] = Color.argb((a * 0.55f * 255f).toInt(), ri, gi, bi)
                cloudColors[2] = Color.argb(0, ri, gi, bi)
                cloudPaint[l].shader = RadialGradient(0f, 0f, 1f, cloudColors, cloudPos, Shader.TileMode.CLAMP)
            }
        }
    }

    // ---------------------------------------------------------------- Scene

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        sunR = w * 0.07f
        sunGlowR = w * 0.38f
        moonR = w * 0.055f
        moonGlowR = w * 0.22f
        sunGlowPaint.shader = RadialGradient(
            0f, 0f, sunGlowR,
            intArrayOf(Color.argb(210, 255, 225, 150), Color.argb(80, 255, 190, 110), Color.argb(0, 255, 180, 100)),
            floatArrayOf(0f, 0.22f, 1f), Shader.TileMode.CLAMP
        )
        moonGlowPaint.shader = RadialGradient(
            0f, 0f, moonGlowR,
            intArrayOf(Color.argb(130, 190, 205, 255), Color.argb(45, 170, 190, 255), Color.argb(0, 160, 180, 255)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
        )

        // Cloud layout
        val rnd = Random(1234)
        for (l in 0 until LAYERS) {
            val n = LAYER_END[l] - LAYER_START[l]
            for (c in LAYER_START[l] until LAYER_END[l]) {
                val k = c - LAYER_START[l]
                cS[c] = w * LAYER_SIZE[l] * (0.8f + 0.4f * rnd.nextFloat())
                cX[c] = ((k + rnd.nextFloat()) / n) * w * 1.3f - 0.15f * w
                cY[c] = h * (LAYER_Y_MIN[l] + rnd.nextFloat() * (LAYER_Y_MAX[l] - LAYER_Y_MIN[l]))
                cV[c] = w * LAYER_SPEED[l] * (0.8f + 0.4f * rnd.nextFloat())
                cPush[c] = 0f
            }
        }

        // Hills
        buildHill(hillPath[0], h * 0.76f, h * 0.030f, 1.3f, 2.9f, 5.1f, 0.4f)
        buildHill(hillPath[1], h * 0.83f, h * 0.035f, 1.0f, 2.3f, 4.3f, 2.1f)
        buildHill(hillPath[2], h * 0.91f, h * 0.028f, 1.7f, 3.1f, 6.2f, 4.0f)

        refreshColors()
    }

    private fun buildHill(p: Path, baseY: Float, amp: Float, f1: Float, f2: Float, f3: Float, phase: Float) {
        p.reset()
        val x0 = -0.2f * w
        val x1 = 1.2f * w
        val step = w / 60f
        p.moveTo(x0, h + 10f)
        var x = x0
        while (x <= x1) {
            val u = x / w * TWO_PI
            val y = baseY + amp * (sin(u * f1 + phase) * 0.6f + sin(u * f2 + phase * 1.7f) * 0.3f +
                sin(u * f3 + phase * 0.6f) * 0.1f)
            p.lineTo(x, y)
            x += step
        }
        p.lineTo(x1, h + 10f)
        p.close()
    }

    override fun update(dt: Float) {
        if (w <= 0f) return
        clock += dt

        // Real time: read the calendar at most once per second.
        timeAcc += dt
        hour += dt / 3600f
        if (hour >= 24f) hour -= 24f
        if (timeAcc >= 1f) {
            timeAcc = 0f
            recomputeTime()
        }

        offCur += (offTarget - offCur) * min(1f, dt * 5f)

        // Clouds
        for (c in 0 until CLUSTERS) {
            cX[c] += (cV[c] + cPush[c]) * dt
            cPush[c] -= cPush[c] * min(1f, dt * 1.5f)
            val ext = 1.9f * cS[c]
            if (cX[c] - ext > w * 1.1f) cX[c] = -ext - w * 0.05f
            else if (cX[c] + ext < -w * 0.15f) cX[c] = w + ext
        }
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        val off = offCur - 0.5f

        // Sky gradient bands
        val bh = h / BANDS
        for (b in 0 until BANDS) {
            skyPaint.color = bandColors[b]
            canvas.drawRect(0f, b * bh, w, (b + 1) * bh + 1f, skyPaint)
        }

        // Stars
        if (night > 0.02f) {
            val sh = -off * w * 0.015f
            for (i in 0 until STARS) {
                val tw = 0.65f + 0.35f * sin(clock * starSp[i] + starPh[i])
                val a = (night * tw * 255f).toInt()
                // fade stars near the horizon glow
                starPaint.alpha = if (a < 0) 0 else if (a > 255) 255 else a
                canvas.drawCircle(starX[i] * w + sh, starY[i] * h, starR[i], starPaint)
            }
        }

        // Sun & moon
        val cx = w * 0.5f - off * w * 0.02f
        val horizonY = h * 0.78f
        val rx = w * 0.42f
        val ry = h * 0.58f

        val sunP = (hour - 6f) / 12f
        if (sunP > -0.12f && sunP < 1.12f) {
            val ang = PI_F * sunP
            val sx = cx - cos(ang) * rx
            val sy = horizonY - sin(ang) * ry
            val e = max(0f, sin(ang))
            sunCorePaint.color = lerpColor(Color.rgb(255, 150, 60), Color.rgb(255, 246, 205), min(1f, e * 2.5f))
            canvas.save()
            canvas.translate(sx, sy)
            canvas.drawCircle(0f, 0f, sunGlowR, sunGlowPaint)
            canvas.drawCircle(0f, 0f, sunR, sunCorePaint)
            canvas.restore()
        }

        val moonP = ((hour - 18f) + 24f) % 24f / 12f
        if (moonP > -0.12f && moonP < 1.12f) {
            val ma = night * 2f
            val alpha = (if (ma > 1f) 1f else ma) * 255f
            if (alpha > 1f) {
                val ang = PI_F * moonP
                val mx = cx - cos(ang) * rx
                val my = horizonY - sin(ang) * ry
                val ai = alpha.toInt()
                moonGlowPaint.alpha = ai
                moonPaint.alpha = ai
                craterPaint.alpha = ai
                canvas.save()
                canvas.translate(mx, my)
                canvas.drawCircle(0f, 0f, moonGlowR, moonGlowPaint)
                canvas.drawCircle(0f, 0f, moonR, moonPaint)
                canvas.drawCircle(-moonR * 0.3f, -moonR * 0.25f, moonR * 0.2f, craterPaint)
                canvas.drawCircle(moonR * 0.35f, moonR * 0.1f, moonR * 0.15f, craterPaint)
                canvas.drawCircle(-moonR * 0.05f, moonR * 0.45f, moonR * 0.12f, craterPaint)
                canvas.restore()
            }
        }

        // Clouds
        for (l in 0 until LAYERS) {
            val paint = cloudPaint[l]
            if (paint.shader == null) continue
            val shift = -off * w * LAYER_PARALLAX[l] * 2f
            for (c in LAYER_START[l] until LAYER_END[l]) {
                val s = cS[c]
                val bx = cX[c] + shift
                if (bx + 1.9f * s < 0f || bx - 1.9f * s > w) continue
                val by = cY[c]
                for (j in 0 until PUFFS) {
                    val idx = c * PUFFS + j
                    val r = pR[idx] * s * 1.6f
                    canvas.save()
                    canvas.translate(bx + pDx[idx] * s, by + pDy[idx] * s)
                    canvas.scale(r, r)
                    canvas.drawCircle(0f, 0f, 1f, paint)
                    canvas.restore()
                }
            }
        }

        // Hills
        for (k in 0 until HILLS) {
            val shift = -off * w * HILL_PARALLAX[k] * 2f
            canvas.save()
            canvas.translate(shift, 0f)
            canvas.drawPath(hillPath[k], hillPaint[k])
            canvas.restore()
        }
    }

    override fun onOffset(xOffset: Float) {
        offTarget = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        if (w <= 0f) return
        val reach = w * 0.5f
        val reach2 = reach * reach
        for (c in 0 until CLUSTERS) {
            val dx = cX[c] - x
            val dy = cY[c] - y
            val d2 = dx * dx + dy * dy
            if (d2 < reach2) {
                val k = 1f - sqrt(d2) / reach
                val dir = if (dx >= 0f) 1f else -1f
                val lim = w * 0.3f
                val np = cPush[c] + dir * k * w * 0.12f * LAYER_PUSH[cLayer[c]]
                cPush[c] = if (np > lim) lim else if (np < -lim) -lim else np
            }
        }
    }

    override fun onVisibilityChanged(visible: Boolean) {
        if (visible) timeAcc = 1f // re-read the clock on next update
    }
}

package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.sin

/** Night/dusk rain: layered streaks, ripples on wet ground, rare lightning, touch gusts. */
class RainScene : Scene {
    override val id: String = "rain"
    override val title: String = "Дождь"

    private companion object {
        const val FAR = 100
        const val MID = 90
        const val NEAR = 60
        const val N = FAR + MID + NEAR
        const val MAX_RIPPLES = 28
    }

    private val rnd = Random(7L)

    private var w = 0f
    private var h = 0f
    private var groundY = 0f

    // Drops
    private val dx = FloatArray(N)
    private val dy = FloatArray(N)
    private val dSpeed = FloatArray(N)
    private val dLen = FloatArray(N)
    private val dLand = FloatArray(N)

    // Per layer params (0 far, 1 mid, 2 near)
    private val layerStart = intArrayOf(0, FAR, FAR + MID, N)
    private val layerSlant = floatArrayOf(0.10f, 0.16f, 0.24f)
    private val layerParallax = floatArrayOf(0.4f, 0.8f, 1.5f)
    private val layerWind = floatArrayOf(0.35f, 0.7f, 1.0f)
    private val layerSpeedMin = floatArrayOf(0.50f, 0.75f, 1.05f)   // fractions of height per sec
    private val layerSpeedMax = floatArrayOf(0.65f, 0.95f, 1.30f)
    private val layerLen = floatArrayOf(0.016f, 0.028f, 0.048f)     // fractions of height
    private val layerRippleChance = floatArrayOf(0.12f, 0.25f, 0.40f)
    private val layerPaints = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val layerPts = arrayOf(FloatArray(FAR * 4), FloatArray(MID * 4), FloatArray(NEAR * 4))

    // Ripples
    private val rx = FloatArray(MAX_RIPPLES)
    private val ry = FloatArray(MAX_RIPPLES)
    private val rAge = FloatArray(MAX_RIPPLES)
    private val rDur = FloatArray(MAX_RIPPLES)
    private val rMax = FloatArray(MAX_RIPPLES)
    private val rActive = BooleanArray(MAX_RIPPLES)
    private var rNext = 0
    private val rect = RectF()
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // Background
    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A1019.toInt() }
    private val hillPath = Path()
    private val glowPaintA = Paint()
    private val glowPaintB = Paint()
    private val flashPaint = Paint()
    private var glowRadius = 1f

    // State
    private var offset = 0.5f
    private var gust = 0f
    private var gustDir = 1f
    private var flash = 0f
    private var boltTimer = 10f
    private var afterTimer = 0f
    private var time = 0f

    init {
        val c = intArrayOf(0xFFB4C8E6.toInt(), 0xFFB4C8E6.toInt(), 0xFFC8D8F0.toInt())
        val a = intArrayOf(70, 105, 150)
        for (i in 0 until 3) {
            layerPaints[i].apply {
                color = c[i]
                alpha = a[i]
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        }
        boltTimer = 8f + rnd.nextFloat() * 12f
    }

    override fun onSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        w = width.toFloat()
        h = height.toFloat()
        groundY = h * 0.84f

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, groundY,
            intArrayOf(0xFF070C16.toInt(), 0xFF111B2B.toInt(), 0xFF22324A.toInt(), 0xFF3B4D63.toInt()),
            floatArrayOf(0f, 0.45f, 0.82f, 1f),
            Shader.TileMode.CLAMP
        )
        groundPaint.shader = LinearGradient(
            0f, groundY, 0f, h,
            intArrayOf(0xFF1A2536.toInt(), 0xFF0E1522.toInt(), 0xFF05080F.toInt()),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        flashPaint.shader = LinearGradient(
            0f, 0f, 0f, groundY,
            intArrayOf(0xFFDCE8FF.toInt(), 0xFFB0C4E8.toInt(), 0x00B0C4E8),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        glowRadius = w * 0.55f
        glowPaintA.shader = RadialGradient(
            0f, 0f, glowRadius, 0x486FA8D8, 0x006FA8D8, Shader.TileMode.CLAMP
        )
        glowPaintB.shader = RadialGradient(
            0f, 0f, glowRadius, 0x38FFB070, 0x00FFB070, Shader.TileMode.CLAMP
        )

        // Distant hills silhouette along the horizon
        hillPath.reset()
        hillPath.moveTo(0f, groundY)
        val steps = 24
        for (i in 0..steps) {
            val fx = i.toFloat() / steps
            val x = fx * w
            val hh = h * (0.018f + 0.012f * sin(fx * 9.0f + 1.0f) + 0.008f * sin(fx * 23.0f))
            hillPath.lineTo(x, groundY - hh)
        }
        hillPath.lineTo(w, groundY)
        hillPath.close()

        val m = w * 0.3f
        for (l in 0 until 3) {
            for (i in layerStart[l] until layerStart[l + 1]) {
                respawn(i, l, true, m)
            }
        }
        for (i in 0 until MAX_RIPPLES) rActive[i] = false
    }

    private fun respawn(i: Int, l: Int, initial: Boolean, m: Float) {
        dx[i] = -m + rnd.nextFloat() * (w + 2f * m)
        dLen[i] = h * layerLen[l] * (0.8f + rnd.nextFloat() * 0.4f)
        dSpeed[i] = h * (layerSpeedMin[l] + rnd.nextFloat() * (layerSpeedMax[l] - layerSpeedMin[l]))
        val t = rnd.nextFloat()
        val gh = h - groundY
        dLand[i] = when (l) {
            0 -> groundY + gh * (0.02f + 0.30f * t)
            1 -> groundY + gh * (0.20f + 0.40f * t)
            else -> groundY + gh * (0.45f + 0.52f * t)
        }
        dy[i] = if (initial) rnd.nextFloat() * dLand[i] else -rnd.nextFloat() * h * 0.15f - dLen[i]
    }

    private fun spawnRipple(x: Float, y: Float) {
        val idx = rNext
        rNext = (rNext + 1) % MAX_RIPPLES
        val gh = h - groundY
        val depth = ((y - groundY) / gh).coerceIn(0f, 1f)
        rx[idx] = x
        ry[idx] = y
        rAge[idx] = 0f
        rDur[idx] = 0.6f + rnd.nextFloat() * 0.5f
        rMax[idx] = w * (0.010f + 0.040f * depth) * (0.8f + rnd.nextFloat() * 0.5f)
        rActive[idx] = true
    }

    override fun update(dt: Float) {
        if (w <= 0f) return
        time += dt

        // Gust decay
        if (gust > 0f) {
            gust -= dt / 1.6f
            if (gust < 0f) gust = 0f
        }

        // Lightning
        boltTimer -= dt
        if (boltTimer <= 0f) {
            flash = 0.8f + rnd.nextFloat() * 0.2f
            afterTimer = 0.10f + rnd.nextFloat() * 0.08f
            boltTimer = 10f + rnd.nextFloat() * 25f
        }
        if (afterTimer > 0f) {
            afterTimer -= dt
            if (afterTimer <= 0f) flash = 0.65f
        }
        if (flash > 0f) {
            flash -= dt * 2.8f
            if (flash < 0f) flash = 0f
        }

        val m = w * 0.3f
        val wrapW = w + 2f * m
        val par = (offset - 0.5f) * 0.14f
        for (l in 0 until 3) {
            val slant = layerSlant[l] + par * layerParallax[l] + gustDir * gust * 0.7f * layerWind[l]
            val chance = layerRippleChance[l]
            for (i in layerStart[l] until layerStart[l + 1]) {
                val v = dSpeed[i] * dt
                dy[i] += v
                dx[i] += v * slant
                if (dx[i] > w + m) dx[i] -= wrapW
                else if (dx[i] < -m) dx[i] += wrapW
                if (dy[i] >= dLand[i]) {
                    if (rnd.nextFloat() < chance) spawnRipple(dx[i], dLand[i])
                    respawn(i, l, false, m)
                }
            }
        }

        for (i in 0 until MAX_RIPPLES) {
            if (rActive[i]) {
                rAge[i] += dt
                if (rAge[i] >= rDur[i]) rActive[i] = false
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return

        // Sky + hills + ground
        canvas.drawRect(0f, 0f, w, groundY, skyPaint)
        canvas.drawPath(hillPath, hillPaint)
        canvas.drawRect(0f, groundY, w, h, groundPaint)

        // Reflection glow on wet ground
        canvas.save()
        canvas.clipRect(0f, groundY, w, h)
        val shift = -(offset - 0.5f) * w * 0.06f
        canvas.save()
        canvas.translate(w * 0.28f + shift, groundY)
        canvas.scale(1f, 0.38f)
        canvas.drawCircle(0f, 0f, glowRadius, glowPaintA)
        canvas.restore()
        canvas.save()
        canvas.translate(w * 0.74f + shift, groundY + (h - groundY) * 0.05f)
        canvas.scale(1f, 0.30f)
        canvas.drawCircle(0f, 0f, glowRadius * 0.8f, glowPaintB)
        canvas.restore()
        canvas.restore()

        // Lightning flash (sky and a bit on ground)
        if (flash > 0.01f) {
            flashPaint.alpha = (flash * 170f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, w, groundY, flashPaint)
        }

        // Ripples
        for (i in 0 until MAX_RIPPLES) {
            if (!rActive[i]) continue
            val t = rAge[i] / rDur[i]
            val inv = 1f - t
            val r = rMax[i] * (0.25f + 0.75f * t)
            ripplePaint.strokeWidth = 1.2f + w * 0.0010f * inv
            ripplePaint.color = 0xFFB4CCE8.toInt()
            ripplePaint.alpha = (inv * inv * 150f).toInt().coerceIn(0, 255)
            rect.set(rx[i] - r, ry[i] - r * 0.30f, rx[i] + r, ry[i] + r * 0.30f)
            canvas.drawOval(rect, ripplePaint)
            if (t > 0.25f) {
                val r2 = r * 0.6f
                ripplePaint.alpha = (inv * inv * 90f).toInt().coerceIn(0, 255)
                rect.set(rx[i] - r2, ry[i] - r2 * 0.30f, rx[i] + r2, ry[i] + r2 * 0.30f)
                canvas.drawOval(rect, ripplePaint)
            }
        }

        // Rain streaks, far to near
        val par = (offset - 0.5f) * 0.14f
        for (l in 0 until 3) {
            val slant = layerSlant[l] + par * layerParallax[l] + gustDir * gust * 0.7f * layerWind[l]
            val pts = layerPts[l]
            var k = 0
            for (i in layerStart[l] until layerStart[l + 1]) {
                val x = dx[i]
                val y = dy[i]
                val len = dLen[i]
                pts[k] = x - slant * len
                pts[k + 1] = y - len
                pts[k + 2] = x
                pts[k + 3] = y
                k += 4
            }
            layerPaints[l].strokeWidth = w * (0.0012f + 0.0012f * l)
            canvas.drawLines(pts, 0, k, layerPaints[l])
        }
    }

    override fun onOffset(xOffset: Float) {
        offset = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        gustDir = if (x > w * 0.5f) -1f else 1f
        gust = 1f
    }

    override fun onVisibilityChanged(visible: Boolean) {
        // nothing to do; dt is clamped by the engine
    }
}

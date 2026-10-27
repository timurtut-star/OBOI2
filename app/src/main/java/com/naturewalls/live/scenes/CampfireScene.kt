package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Night campfire: pulsing glow, particle flames, sparks, logs, treeline, stars. */
class CampfireScene : Scene {
    override val id: String = "fire"
    override val title: String = "Костёр"

    private companion object {
        const val FLAME_N = 120
        const val SPARK_N = 70
        const val STAR_N = 40
    }

    private val rnd = Random(7)
    private var w = 0f
    private var h = 0f
    private var fx = 0f
    private var fy = 0f
    private var time = 0f
    private var sparkTimer = 0f
    private var offset = 0.5f

    // Flames
    private val fX = FloatArray(FLAME_N)
    private val fY = FloatArray(FLAME_N)
    private val fAge = FloatArray(FLAME_N)
    private val fLife = FloatArray(FLAME_N)
    private val fSize = FloatArray(FLAME_N)
    private val fPhase = FloatArray(FLAME_N)
    private val fSpeed = FloatArray(FLAME_N)

    // Sparks
    private val sX = FloatArray(SPARK_N)
    private val sY = FloatArray(SPARK_N)
    private val sVx = FloatArray(SPARK_N)
    private val sVy = FloatArray(SPARK_N)
    private val sAge = FloatArray(SPARK_N)
    private val sLife = FloatArray(SPARK_N) // <= 0 means inactive
    private val sPhase = FloatArray(SPARK_N)

    // Stars
    private val stX = FloatArray(STAR_N)
    private val stY = FloatArray(STAR_N)
    private val stR = FloatArray(STAR_N)
    private val stPhase = FloatArray(STAR_N)

    // Paints
    private val bgPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val treePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(4, 5, 7) }
    private val groundPaint = Paint().apply { color = Color.rgb(6, 5, 5) }
    private val logPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 12, 8) }
    private val logEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 28, 8) }
    private val emberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 90, 20) }
    private val flamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private val treePath = Path()
    private val logRect = RectF()
    private val emberRect = RectF()

    private var horizonY = 0f
    private var glowRadius = 1f

    private fun noise(t: Float): Float {
        // smooth pseudo noise in roughly -1..1
        return (sin(t * 1.7f) * 0.5f + sin(t * 3.9f + 1.3f) * 0.3f + sin(t * 8.3f + 4.1f) * 0.2f)
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        fx = w * 0.5f
        fy = h * 0.74f
        horizonY = h * 0.66f
        glowRadius = w * 0.95f

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.rgb(3, 3, 6), Color.rgb(10, 6, 5), Color.rgb(20, 9, 4)),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            fx, fy, glowRadius,
            intArrayOf(Color.argb(200, 255, 120, 30), Color.argb(90, 220, 70, 15), Color.argb(0, 120, 30, 5)),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        groundGlowPaint.shader = RadialGradient(
            fx, fy + h * 0.01f, w * 0.45f,
            intArrayOf(Color.argb(160, 255, 140, 40), Color.argb(0, 255, 100, 20)),
            null,
            Shader.TileMode.CLAMP
        )

        buildTreeline()

        for (i in 0 until STAR_N) {
            stX[i] = rnd.nextFloat() * w
            stY[i] = rnd.nextFloat() * horizonY * 0.85f
            stR[i] = w * (0.0007f + rnd.nextFloat() * 0.0013f)
            stPhase[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until FLAME_N) {
            respawnFlame(i)
            fAge[i] = rnd.nextFloat() * fLife[i]
        }
        for (i in 0 until SPARK_N) sLife[i] = 0f
    }

    private fun buildTreeline() {
        treePath.reset()
        treePath.moveTo(0f, h)
        treePath.lineTo(0f, horizonY)
        val tr = Random(3)
        var x = 0f
        while (x < w) {
            val tw = w * (0.04f + tr.nextFloat() * 0.06f)
            val th = h * (0.025f + tr.nextFloat() * 0.06f)
            // pine-like spike
            treePath.lineTo(x + tw * 0.5f, horizonY - th)
            treePath.lineTo(x + tw, horizonY + h * 0.004f)
            x += tw
        }
        treePath.lineTo(w, horizonY)
        treePath.lineTo(w, h)
        treePath.close()
    }

    private fun respawnFlame(i: Int) {
        val baseW = w * 0.07f
        fX[i] = fx + (rnd.nextFloat() - 0.5f) * baseW * 2f
        fY[i] = fy - h * 0.005f + (rnd.nextFloat() - 0.5f) * h * 0.008f
        fAge[i] = 0f
        fLife[i] = 0.7f + rnd.nextFloat() * 0.7f
        fSize[i] = w * (0.035f + rnd.nextFloat() * 0.035f)
        fPhase[i] = rnd.nextFloat() * 6.28f
        fSpeed[i] = h * (0.10f + rnd.nextFloat() * 0.07f)
    }

    private fun spawnSpark(i: Int, burst: Boolean) {
        sX[i] = fx + (rnd.nextFloat() - 0.5f) * w * 0.08f
        sY[i] = fy - h * 0.03f
        val spread = if (burst) 0.25f else 0.08f
        sVx[i] = (rnd.nextFloat() - 0.5f) * w * spread
        sVy[i] = -h * (if (burst) 0.12f + rnd.nextFloat() * 0.18f else 0.07f + rnd.nextFloat() * 0.12f)
        sAge[i] = 0f
        sLife[i] = 1.4f + rnd.nextFloat() * 2.2f
        sPhase[i] = rnd.nextFloat() * 6.28f
    }

    override fun update(dt: Float) {
        if (w <= 0f) return
        time += dt

        for (i in 0 until FLAME_N) {
            fAge[i] += dt
            if (fAge[i] >= fLife[i]) {
                respawnFlame(i)
                continue
            }
            val p = fAge[i] / fLife[i]
            val turb = sin(time * 5f + fPhase[i] + fY[i] * 0.012f) * w * 0.05f * (0.3f + p)
            fX[i] += (turb + (fx - fX[i]) * 1.8f) * dt
            fY[i] -= fSpeed[i] * (1f - p * 0.35f) * dt
        }

        sparkTimer += dt
        if (sparkTimer > 0.08f) {
            sparkTimer = 0f
            for (i in 0 until SPARK_N) {
                if (sLife[i] <= 0f) {
                    spawnSpark(i, false)
                    break
                }
            }
        }
        for (i in 0 until SPARK_N) {
            if (sLife[i] <= 0f) continue
            sAge[i] += dt
            if (sAge[i] >= sLife[i]) {
                sLife[i] = 0f
                continue
            }
            sVx[i] += sin(time * 3f + sPhase[i]) * w * 0.15f * dt
            sX[i] += sVx[i] * dt
            sY[i] += sVy[i] * dt
        }
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f) return
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // stars
        for (i in 0 until STAR_N) {
            val tw = 0.5f + 0.5f * sin(time * 1.5f + stPhase[i])
            starPaint.alpha = (60 + 140 * tw).toInt()
            canvas.drawCircle(stX[i], stY[i], stR[i], starPaint)
        }

        // pulsing glow
        val n = noise(time * 2.2f)
        val flick = 0.85f + 0.15f * n
        val scale = 0.9f + 0.12f * n + 0.04f * sin(time * 9f)
        glowPaint.alpha = (255f * flick).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.scale(scale, scale, fx, fy)
        canvas.drawCircle(fx, fy, glowRadius, glowPaint)
        canvas.restore()

        // treeline silhouette (lit slightly by fire is skipped; stays dark)
        canvas.drawPath(treePath, treePaint)
        // ground
        canvas.drawRect(0f, horizonY + h * 0.005f, w, h, groundPaint)
        // ground glow under fire
        groundGlowPaint.alpha = (255f * flick).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.scale(1f, 0.35f, fx, fy)
        canvas.drawCircle(fx, fy, w * 0.45f * scale, groundGlowPaint)
        canvas.restore()

        // logs: two crossed
        val lw = w * 0.30f
        val lh = h * 0.016f
        for (k in 0..1) {
            canvas.save()
            canvas.translate(fx, fy + h * 0.004f)
            canvas.rotate(if (k == 0) -14f else 14f)
            logRect.set(-lw * 0.5f, -lh * 0.5f, lw * 0.5f, lh * 0.5f)
            canvas.drawRoundRect(logRect, lh * 0.5f, lh * 0.5f, logPaint)
            // glowing edge on top
            logRect.set(-lw * 0.45f, -lh * 0.5f, lw * 0.45f, -lh * 0.28f)
            logEdgePaint.alpha = (140f + 90f * flick).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(logRect, lh * 0.2f, lh * 0.2f, logEdgePaint)
            canvas.restore()
        }
        // embers bed
        emberRect.set(fx - w * 0.07f, fy - h * 0.004f, fx + w * 0.07f, fy + h * 0.008f)
        emberPaint.alpha = (180f + 75f * n).toInt().coerceIn(0, 255)
        canvas.drawOval(emberRect, emberPaint)

        // flames (additive)
        for (i in 0 until FLAME_N) {
            val p = fAge[i] / fLife[i]
            if (p < 0f || p >= 1f) continue
            val r: Int
            val g: Int
            val b: Int
            if (p < 0.2f) {
                val t = p / 0.2f
                r = 255
                g = (235f - 85f * t).toInt()
                b = (150f - 120f * t).toInt()
            } else if (p < 0.55f) {
                val t = (p - 0.2f) / 0.35f
                r = 255 - (30f * t).toInt()
                g = (150f - 100f * t).toInt()
                b = 30 - (15f * t).toInt()
            } else {
                val t = (p - 0.55f) / 0.45f
                r = 225 - (120f * t).toInt()
                g = 50 - (35f * t).toInt()
                b = 15 - (5f * t).toInt()
            }
            val fade = if (p < 0.1f) p / 0.1f else (1f - p)
            val size = fSize[i] * (1f - 0.55f * p)
            val a1 = (70f * fade).toInt().coerceIn(0, 255)
            flamePaint.color = Color.argb(a1, r, g, b)
            canvas.drawCircle(fX[i], fY[i], size, flamePaint)
            val a2 = (110f * fade).toInt().coerceIn(0, 255)
            flamePaint.color = Color.argb(a2, r, g, b)
            canvas.drawCircle(fX[i], fY[i], size * 0.5f, flamePaint)
        }

        // sparks
        val sr = w * 0.0035f
        for (i in 0 until SPARK_N) {
            if (sLife[i] <= 0f) continue
            val p = sAge[i] / sLife[i]
            val tw = 0.7f + 0.3f * sin(time * 20f + sPhase[i])
            val a = (255f * (1f - p) * tw).toInt().coerceIn(0, 255)
            sparkPaint.color = Color.argb(a, 255, (210f - 100f * p).toInt(), (110f - 80f * p).toInt())
            canvas.drawCircle(sX[i], sY[i], sr * (1f - 0.5f * p), sparkPaint)
        }
    }

    override fun onOffset(xOffset: Float) {
        offset = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        var added = 0
        for (i in 0 until SPARK_N) {
            if (sLife[i] <= 0f) {
                spawnSpark(i, true)
                added++
                if (added >= 25) break
            }
        }
    }
}

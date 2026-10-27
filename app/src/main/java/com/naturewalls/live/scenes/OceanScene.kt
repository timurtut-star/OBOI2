package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Sunset ocean: gradient sky, sun with glow, clouds, layered sine waves, shimmering reflection, birds. */
class OceanScene : Scene {
    override val id: String = "ocean"
    override val title: String = "Океан"

    private companion object {
        const val TWO_PI = 6.2831853
        const val LAYERS = 6
        const val SEGMENTS = 40
        const val GLINTS = 80
        const val CLOUDS = 4
        const val BIRDS = 5
        const val RIPPLES = 3
        const val RIPPLE_LIFE = 3.5f
    }

    private var w = 0f
    private var h = 0f
    private var horizon = 0f
    private var sunX = 0f
    private var sunY = 0f
    private var sunR = 0f
    private var glowR = 1f
    private var rippleK = 0.1f
    private var rippleAmp = 0f

    private var time = 0.0
    private var offsetTarget = 0.5f
    private var offsetCur = 0.5f

    // Paints
    private val skyPaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val birdPath = Path()
    private val rect = RectF()

    // Water layers
    private val baseY = FloatArray(LAYERS)
    private val amp = FloatArray(LAYERS)
    private val waveK = FloatArray(LAYERS)
    private val speed = FloatArray(LAYERS)
    private val phase = FloatArray(LAYERS)
    private val layerColor = IntArray(LAYERS)

    // Glints
    private val gDepth = FloatArray(GLINTS)
    private val gSide = FloatArray(GLINTS)
    private val gPhase = FloatArray(GLINTS)
    private val gSpeed = FloatArray(GLINTS)
    private val gLen = FloatArray(GLINTS)

    // Clouds
    private val cX = FloatArray(CLOUDS)
    private val cY = FloatArray(CLOUDS)
    private val cScale = FloatArray(CLOUDS)
    private val cSpeed = FloatArray(CLOUDS)
    private val cAlpha = IntArray(CLOUDS)

    // Birds
    private val bX = FloatArray(BIRDS)
    private val bY = FloatArray(BIRDS)
    private val bSize = FloatArray(BIRDS)
    private val bSpeed = FloatArray(BIRDS)
    private val bPhase = FloatArray(BIRDS)

    // Ripples
    private val rX = FloatArray(RIPPLES)
    private val rY = FloatArray(RIPPLES)
    private val rAge = FloatArray(RIPPLES) { 99f }
    private var rNext = 0

    init {
        val rnd = Random(42)
        for (g in 0 until GLINTS) {
            gDepth[g] = rnd.nextFloat()
            gSide[g] = rnd.nextFloat() * 2f - 1f
            gPhase[g] = rnd.nextFloat() * TWO_PI.toFloat()
            gSpeed[g] = 1.2f + rnd.nextFloat() * 2.5f
            gLen[g] = 0.5f + rnd.nextFloat()
        }
        for (c in 0 until CLOUDS) {
            cX[c] = rnd.nextFloat() * 1.3f - 0.2f
            cY[c] = 0.12f + rnd.nextFloat() * 0.62f
            cScale[c] = 0.35f + rnd.nextFloat() * 0.3f
            cSpeed[c] = 0.004f + rnd.nextFloat() * 0.006f
            cAlpha[c] = 70 + rnd.nextInt(50)
        }
        for (b in 0 until BIRDS) {
            bX[b] = rnd.nextFloat()
            bY[b] = 0.25f + rnd.nextFloat() * 0.5f
            bSize[b] = 0.6f + rnd.nextFloat() * 0.6f
            bSpeed[b] = 0.012f + rnd.nextFloat() * 0.01f
            bPhase[b] = rnd.nextFloat() * TWO_PI.toFloat()
        }
        glintPaint.color = Color.rgb(255, 228, 160)
        glintPaint.style = Paint.Style.STROKE
        glintPaint.strokeCap = Paint.Cap.ROUND
        sunPaint.color = Color.rgb(255, 244, 214)
        cloudPaint.color = Color.rgb(255, 160, 130)
        birdPaint.color = Color.argb(210, 40, 16, 40)
        birdPaint.style = Paint.Style.STROKE
        birdPaint.strokeCap = Paint.Cap.ROUND
        waterPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        horizon = h * 0.52f
        sunR = w * 0.075f
        sunX = w * 0.5f
        sunY = horizon - sunR * 0.3f
        glowR = sunR * 5.5f
        rippleK = (TWO_PI / (w * 0.09)).toFloat()
        rippleAmp = h * 0.011f

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            intArrayOf(
                Color.rgb(38, 24, 84),
                Color.rgb(110, 44, 120),
                Color.rgb(226, 86, 100),
                Color.rgb(255, 150, 70),
                Color.rgb(255, 208, 124)
            ),
            floatArrayOf(0f, 0.35f, 0.65f, 0.88f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            0f, 0f, glowR,
            intArrayOf(
                Color.argb(235, 255, 230, 170),
                Color.argb(150, 255, 170, 90),
                Color.argb(45, 255, 100, 70),
                Color.argb(0, 255, 80, 60)
            ),
            floatArrayOf(0f, 0.18f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        val wh = h - horizon
        for (i in 0 until LAYERS) {
            val t = i.toFloat() / (LAYERS - 1).toFloat()
            baseY[i] = horizon + h * 0.004f + wh * (0.88f * t * t + 0.06f * t)
            amp[i] = h * (0.0035f + 0.0095f * t)
            val wavelength = w * (0.7f - 0.4f * t)
            waveK[i] = (TWO_PI / wavelength).toFloat()
            speed[i] = 0.5f + 0.35f * i
            phase[i] = i * 1.7f
            val r = (176 + (10 - 176) * t).toInt()
            val g = (92 + (24 - 92) * t).toInt()
            val b = (112 + (52 - 112) * t).toInt()
            layerColor[i] = Color.rgb(r, g, b)
        }
        glintPaint.strokeWidth = 2f
        birdPaint.strokeWidth = maxOf(2f, h * 0.0014f)
    }

    override fun update(dt: Float) {
        time += dt.toDouble()
        offsetCur += (offsetTarget - offsetCur) * minOf(1f, dt * 5f)
        for (c in 0 until CLOUDS) {
            cX[c] += cSpeed[c] * dt
            if (cX[c] > 1.3f) cX[c] = -0.3f
        }
        for (b in 0 until BIRDS) {
            bX[b] += bSpeed[b] * dt
            if (bX[b] > 1.1f) bX[b] = -0.1f
        }
        for (r in 0 until RIPPLES) {
            if (rAge[r] < RIPPLE_LIFE) rAge[r] += dt
        }
    }

    private fun oval(canvas: Canvas, cx: Float, cy: Float, rw: Float, rh: Float) {
        rect.set(cx - rw * 0.5f, cy - rh * 0.5f, cx + rw * 0.5f, cy + rh * 0.5f)
        canvas.drawOval(rect, cloudPaint)
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return
        val shiftBase = (offsetCur - 0.5f) * w * 0.15f

        // Sky
        canvas.drawRect(0f, 0f, w, horizon + 2f, skyPaint)

        // Sun glow and disc (slight parallax)
        val sx = sunX + shiftBase * 0.1f
        canvas.save()
        canvas.translate(sx, sunY)
        canvas.drawCircle(0f, 0f, glowR, glowPaint)
        canvas.drawCircle(0f, 0f, sunR, sunPaint)
        canvas.restore()

        // Clouds
        val skyH = horizon
        for (c in 0 until CLOUDS) {
            val cw = w * cScale[c]
            val ch = cw * 0.17f
            val cx = cX[c] * w + shiftBase * 0.2f
            val cy = cY[c] * skyH * 0.8f + skyH * 0.05f
            cloudPaint.alpha = cAlpha[c]
            oval(canvas, cx, cy, cw, ch)
            oval(canvas, cx - cw * 0.2f, cy + ch * 0.15f, cw * 0.6f, ch * 0.8f)
            oval(canvas, cx + cw * 0.22f, cy - ch * 0.2f, cw * 0.5f, ch * 0.9f)
            oval(canvas, cx + cw * 0.04f, cy - ch * 0.45f, cw * 0.4f, ch * 0.9f)
        }

        // Birds
        for (b in 0 until BIRDS) {
            val s = w * 0.018f * bSize[b]
            val x = bX[b] * w + shiftBase * 0.3f
            val y = bY[b] * skyH * 0.5f + skyH * 0.12f
            val flap = sin(((time * 4.0 + bPhase[b]) % TWO_PI).toFloat())
            val tipY = y - s * (0.1f + 0.4f * flap)
            birdPath.reset()
            birdPath.moveTo(x - s, tipY)
            birdPath.quadTo(x - s * 0.45f, y - s * 0.4f, x, y)
            birdPath.quadTo(x + s * 0.45f, y - s * 0.4f, x + s, tipY)
            canvas.drawPath(birdPath, birdPaint)
        }

        // Water base fill (behind first wave band)
        waterPaint.color = layerColor[0]
        canvas.drawRect(0f, horizon, w, h, waterPaint)

        // Water bands, back to front
        val step = w / SEGMENTS
        for (i in 0 until LAYERS) {
            val t = i.toFloat() / (LAYERS - 1).toFloat()
            val sh = shiftBase * (0.15f + 0.85f * t)
            val a1 = ((time * speed[i]) % TWO_PI).toFloat()
            val a2 = ((time * speed[i] * 1.31) % TWO_PI).toFloat()
            val k = waveK[i]
            val by = baseY[i]
            val am = amp[i]
            val ph = phase[i]
            path.reset()
            for (j in 0..SEGMENTS) {
                val x = j * step
                val xs = x + sh
                var y = by + am * sin(k * xs + ph + a1) +
                    am * 0.45f * sin(k * 2.17f * xs + a2 + ph * 1.9f)
                for (r in 0 until RIPPLES) {
                    val age = rAge[r]
                    if (age < RIPPLE_LIFE) {
                        val dx = x - rX[r]
                        val dy = by - rY[r]
                        val d = sqrt(dx * dx + dy * dy)
                        val env = exp(-d / (w * 0.3f)) * exp(-age * 1.2f)
                        y += rippleAmp * sin(d * rippleK - age * 7f) * env
                    }
                }
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.lineTo(0f, h)
            path.close()
            waterPaint.color = layerColor[i]
            canvas.drawPath(path, waterPaint)
        }

        // Sun reflection glints
        val wh = h - horizon
        val rx = sx + shiftBase * 0.15f
        for (g in 0 until GLINTS) {
            val v = gDepth[g]
            val ang = ((time * gSpeed[g] + gPhase[g]) % TWO_PI).toFloat()
            val s01 = sin(ang) * 0.5f + 0.5f
            val a = s01 * s01 * (1f - abs(gSide[g])) * (1f - 0.5f * v)
            val alpha = (a * 255f).toInt()
            if (alpha <= 4) continue
            val spread = sunR * (0.5f + 3.2f * v)
            val gx = rx + gSide[g] * spread
            val gy = horizon + wh * (0.012f + 0.7f * v) + sin(ang * 0.5f) * h * 0.0008f
            val half = w * (0.008f + 0.03f * v) * gLen[g]
            glintPaint.alpha = if (alpha > 255) 255 else alpha
            glintPaint.strokeWidth = h * (0.0013f + 0.0021f * v)
            canvas.drawLine(gx - half, gy, gx + half, gy, glintPaint)
        }
    }

    override fun onOffset(xOffset: Float) {
        offsetTarget = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        rX[rNext] = x
        rY[rNext] = y
        rAge[rNext] = 0f
        rNext = (rNext + 1) % RIPPLES
    }
}

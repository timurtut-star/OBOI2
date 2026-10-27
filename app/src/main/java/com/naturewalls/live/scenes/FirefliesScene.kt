package com.naturewalls.live.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Magical night forest with fireflies, moon rays, layered trees and drifting ground mist. */
class FirefliesScene : Scene {
    override val id: String = "fireflies"
    override val title: String = "Ночной лес"

    private companion object {
        const val FLY_COUNT = 45
        const val MIST_COUNT = 7
        const val LAYERS = 3
        const val RAY_COUNT = 5
    }

    private var w = 0f
    private var h = 0f
    private var time = 0f
    private var offset = 0.5f

    // Background / moon
    private val bgPaint = Paint()
    private val moonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var moonX = 0f
    private var moonY = 0f
    private var moonR = 0f
    private val rayPaths = Array(RAY_COUNT) { Path() }

    // Trees
    private val layerPaths = Array(LAYERS) { Path() }
    private val layerPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val layerParallax = floatArrayOf(0.05f, 0.12f, 0.26f)
    private val fogPaints = Array(LAYERS) { Paint() }
    private val fogRects = Array(LAYERS) { RectF() }

    // Mist
    private var mistBmp: Bitmap? = null
    private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mistX = FloatArray(MIST_COUNT)
    private val mistY = FloatArray(MIST_COUNT)
    private val mistW = FloatArray(MIST_COUNT)
    private val mistSpeed = FloatArray(MIST_COUNT)
    private val mistAlpha = IntArray(MIST_COUNT)
    private val mistRect = RectF()

    // Fireflies
    private var haloBmp: Bitmap? = null
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloRect = RectF()
    private val baseX = FloatArray(FLY_COUNT)
    private val baseY = FloatArray(FLY_COUNT)
    private val ampX = FloatArray(FLY_COUNT)
    private val ampY = FloatArray(FLY_COUNT)
    private val f1 = FloatArray(FLY_COUNT)
    private val f2 = FloatArray(FLY_COUNT)
    private val ph1 = FloatArray(FLY_COUNT)
    private val ph2 = FloatArray(FLY_COUNT)
    private val ph3 = FloatArray(FLY_COUNT)
    private val ph4 = FloatArray(FLY_COUNT)
    private val pulseSpeed = FloatArray(FLY_COUNT)
    private val pulsePhase = FloatArray(FLY_COUNT)
    private val depth = FloatArray(FLY_COUNT)
    private val offX = FloatArray(FLY_COUNT)
    private val offY = FloatArray(FLY_COUNT)
    private val posX = FloatArray(FLY_COUNT)
    private val posY = FloatArray(FLY_COUNT)
    private val bright = FloatArray(FLY_COUNT)

    // Touch
    private var touchX = 0f
    private var touchY = 0f
    private var touchTimer = 0f

    private val rnd = Random(7L)

    init {
        for (i in 0 until FLY_COUNT) {
            baseX[i] = rnd.nextFloat()
            baseY[i] = 0.38f + rnd.nextFloat() * 0.55f
            ampX[i] = 0.03f + rnd.nextFloat() * 0.08f
            ampY[i] = 0.015f + rnd.nextFloat() * 0.04f
            f1[i] = 0.15f + rnd.nextFloat() * 0.35f
            f2[i] = 0.2f + rnd.nextFloat() * 0.4f
            ph1[i] = rnd.nextFloat() * 6.2832f
            ph2[i] = rnd.nextFloat() * 6.2832f
            ph3[i] = rnd.nextFloat() * 6.2832f
            ph4[i] = rnd.nextFloat() * 6.2832f
            pulseSpeed[i] = 0.5f + rnd.nextFloat() * 1.1f
            pulsePhase[i] = rnd.nextFloat() * 6.2832f
            depth[i] = rnd.nextFloat()
        }
        for (i in 0 until MIST_COUNT) {
            mistX[i] = -0.3f + rnd.nextFloat() * 1.4f
            mistY[i] = 0.76f + rnd.nextFloat() * 0.2f
            mistW[i] = 0.6f + rnd.nextFloat() * 0.6f
            mistSpeed[i] = 0.004f + rnd.nextFloat() * 0.01f
            mistAlpha[i] = 60 + rnd.nextInt(60)
        }
        layerPaints[0].color = Color.rgb(24, 52, 60)
        layerPaints[1].color = Color.rgb(12, 31, 37)
        layerPaints[2].color = Color.rgb(4, 12, 15)
        moonPaint.color = Color.rgb(238, 246, 238)
        moonShadePaint.color = Color.argb(40, 150, 175, 170)
        corePaint.color = Color.rgb(250, 255, 190)
        mistPaint.alpha = 90
        haloBmp = buildHalo()
        mistBmp = buildMist()
    }

    private fun buildHalo(): Bitmap {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(
                Color.argb(255, 225, 255, 110),
                Color.argb(150, 190, 245, 70),
                Color.argb(45, 140, 220, 50),
                Color.argb(0, 120, 200, 40)
            ),
            floatArrayOf(0f, 0.18f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        return bmp
    }

    private fun buildMist(): Bitmap {
        val bw = 128
        val bh = 48
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            64f, 64f, 64f,
            intArrayOf(
                Color.argb(150, 150, 190, 190),
                Color.argb(70, 120, 165, 170),
                Color.argb(0, 100, 150, 150)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.scale(1f, bh / 128f)
        c.drawCircle(64f, 64f, 64f, p)
        return bmp
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.rgb(3, 12, 24),
                Color.rgb(8, 32, 44),
                Color.rgb(10, 46, 46),
                Color.rgb(6, 30, 28)
            ),
            floatArrayOf(0f, 0.4f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )

        moonX = w * 0.72f
        moonY = h * 0.15f
        moonR = w * 0.07f
        moonGlowPaint.shader = RadialGradient(
            moonX, moonY, moonR * 6f,
            intArrayOf(
                Color.argb(150, 190, 225, 235),
                Color.argb(60, 120, 180, 200),
                Color.argb(0, 60, 110, 140)
            ),
            floatArrayOf(0.12f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        // Moon rays: translucent wedges from moon down through the trees.
        rayPaint.shader = LinearGradient(
            0f, moonY, 0f, h * 0.9f,
            Color.argb(80, 190, 225, 235),
            Color.argb(0, 150, 200, 220),
            Shader.TileMode.CLAMP
        )
        val rayRnd = Random(31L)
        for (k in 0 until RAY_COUNT) {
            val p = rayPaths[k]
            p.reset()
            val spread = (k - (RAY_COUNT - 1) / 2f)
            val sx = moonX + spread * moonR * 0.5f
            val ex = moonX + spread * w * 0.22f - w * 0.12f + rayRnd.nextFloat() * w * 0.06f
            val ew = w * (0.05f + rayRnd.nextFloat() * 0.08f)
            p.moveTo(sx - moonR * 0.15f, moonY)
            p.lineTo(sx + moonR * 0.15f, moonY)
            p.lineTo(ex + ew, h * 0.9f)
            p.lineTo(ex - ew, h * 0.9f)
            p.close()
        }

        for (i in 0 until LAYERS) {
            buildLayer(layerPaths[i], i)
            val cy = h * (0.78f + 0.05f * i)
            val fh = h * 0.2f
            fogRects[i].set(-w * 0.3f, cy - fh, w * 1.3f, cy + fh)
            val fogCol = when (i) {
                0 -> intArrayOf(Color.argb(0, 70, 120, 130), Color.argb(120, 70, 120, 130), Color.argb(0, 70, 120, 130))
                1 -> intArrayOf(Color.argb(0, 50, 100, 105), Color.argb(90, 50, 100, 105), Color.argb(0, 50, 100, 105))
                else -> intArrayOf(Color.argb(0, 40, 85, 90), Color.argb(60, 40, 85, 90), Color.argb(0, 40, 85, 90))
            }
            fogPaints[i].shader = LinearGradient(
                0f, cy - fh, 0f, cy + fh,
                fogCol, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        }
    }

    private fun buildLayer(p: Path, layer: Int) {
        p.reset()
        val r = Random(100L + layer * 17L)
        val ground = h * (0.80f + 0.05f * layer)
        val n = when (layer) {
            0 -> 15
            1 -> 10
            else -> 6
        }
        val x0 = -0.3f * w
        val span = 1.6f * w
        for (i in 0 until n) {
            val cx = x0 + span * (i + r.nextFloat() * 0.8f) / n
            val tw = w * (0.010f + 0.012f * layer) * (0.7f + r.nextFloat() * 0.6f)
            val topY = when (layer) {
                0 -> h * (0.30f + r.nextFloat() * 0.2f)
                1 -> h * (0.10f + r.nextFloat() * 0.2f)
                else -> -h * 0.05f
            }
            p.moveTo(cx - tw, ground)
            p.lineTo(cx - tw * 0.6f, topY)
            p.lineTo(cx + tw * 0.6f, topY)
            p.lineTo(cx + tw, ground)
            p.close()
            if (layer < 2) {
                val cr = w * (0.06f + 0.03f * r.nextFloat()) * (1f + 0.3f * layer)
                for (j in 0 until 5) {
                    p.addCircle(
                        cx + (r.nextFloat() - 0.5f) * cr * 2f,
                        topY + (r.nextFloat() - 0.4f) * cr * 1.2f,
                        cr * (0.6f + 0.5f * r.nextFloat()),
                        Path.Direction.CW
                    )
                }
            }
            if (layer >= 1) {
                for (b in 0 until 2) {
                    val dir = if (r.nextBoolean()) 1f else -1f
                    val by = max(topY, 0f) + (ground - max(topY, 0f)) * (0.15f + 0.4f * r.nextFloat())
                    val len = w * (0.06f + 0.08f * r.nextFloat())
                    p.moveTo(cx, by)
                    p.lineTo(cx + dir * len, by - len * 0.5f)
                    p.lineTo(cx + dir * len, by - len * 0.5f + tw * 0.5f)
                    p.lineTo(cx, by + tw * 0.9f)
                    p.close()
                }
            }
        }
        if (layer == 2) {
            for (i in 0..11) {
                p.addCircle(
                    x0 + span * i / 11f,
                    r.nextFloat() * h * 0.03f,
                    w * (0.08f + r.nextFloat() * 0.06f),
                    Path.Direction.CW
                )
            }
        }
        p.addRect(x0, ground, x0 + span, h * 1.1f, Path.Direction.CW)
    }

    override fun onOffset(xOffset: Float) {
        offset = xOffset
    }

    override fun onTouch(x: Float, y: Float) {
        touchX = x
        touchY = y
        touchTimer = 1.2f
    }

    override fun update(dt: Float) {
        if (w <= 0f || h <= 0f) return
        time += dt
        val shift = -(offset - 0.5f) * w
        val attractK = min(1f, dt * 2.5f)
        val releaseK = min(1f, dt * 1.2f)
        val touching = touchTimer > 0f
        val reach = w * 0.55f

        for (i in 0 until FLY_COUNT) {
            val t = time
            val wx = baseX[i] * w +
                ampX[i] * w * (sin(t * f1[i] + ph1[i]) + 0.5f * sin(t * f1[i] * 2.3f + ph2[i]))
            val wy = baseY[i] * h +
                ampY[i] * h * (sin(t * f2[i] + ph3[i]) + 0.5f * sin(t * f2[i] * 1.9f + ph4[i]))
            val sx = wx + shift * (0.05f + 0.15f * depth[i])

            var tx = 0f
            var ty = 0f
            var k = releaseK
            if (touching) {
                val dx = touchX - (sx + offX[i])
                val dy = touchY - (wy + offY[i])
                val d = sqrt(dx * dx + dy * dy)
                if (d < reach) {
                    val swirl = sin(t * 3f + ph1[i]) * w * 0.02f
                    tx = touchX - sx + swirl
                    ty = touchY - wy + swirl * 0.7f
                    k = attractK
                }
            }
            offX[i] += (tx - offX[i]) * k
            offY[i] += (ty - offY[i]) * k

            posX[i] = sx + offX[i]
            posY[i] = wy + offY[i]

            val v = sin(t * pulseSpeed[i] + pulsePhase[i])
            var b = (v + 0.25f) * 1.4f
            if (b < 0f) b = 0f
            if (b > 1f) b = 1f
            bright[i] = b * b * (3f - 2f * b)
        }

        if (touching) touchTimer -= dt

        for (i in 0 until MIST_COUNT) {
            mistX[i] += mistSpeed[i] * dt
            if (mistX[i] > 1.3f) mistX[i] = -0.3f - mistW[i]
        }
    }

    override fun draw(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return
        val shift = -(offset - 0.5f) * w

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Moon
        val mx = moonX + shift * 0.02f
        canvas.save()
        canvas.translate(shift * 0.02f, 0f)
        canvas.drawCircle(moonX, moonY, moonR * 6f, moonGlowPaint)
        canvas.drawCircle(moonX, moonY, moonR, moonPaint)
        canvas.drawCircle(moonX - moonR * 0.3f, moonY - moonR * 0.2f, moonR * 0.28f, moonShadePaint)
        canvas.drawCircle(moonX + moonR * 0.35f, moonY + moonR * 0.3f, moonR * 0.18f, moonShadePaint)
        for (k in 0 until RAY_COUNT) {
            val a = 0.55f + 0.45f * sin(time * 0.25f + k * 1.7f)
            rayPaint.alpha = (255f * a).toInt()
            canvas.drawPath(rayPaths[k], rayPaint)
        }
        canvas.restore()
        if (mx < -1f) return

        for (i in 0 until LAYERS) {
            canvas.save()
            canvas.translate(shift * layerParallax[i], 0f)
            canvas.drawPath(layerPaths[i], layerPaints[i])
            canvas.restore()

            canvas.save()
            canvas.translate(shift * layerParallax[i], 0f)
            canvas.drawRect(fogRects[i], fogPaints[i])
            canvas.restore()

            if (i == 1) drawFireflies(canvas)
        }

        // Ground mist in front
        val bmp = mistBmp
        if (bmp != null) {
            for (i in 0 until MIST_COUNT) {
                val mw = mistW[i] * w
                val left = mistX[i] * w + shift * 0.2f
                val cy = mistY[i] * h
                val mh = mw * 0.22f
                mistRect.set(left, cy - mh * 0.5f, left + mw, cy + mh * 0.5f)
                mistPaint.alpha = mistAlpha[i]
                canvas.drawBitmap(bmp, null, mistRect, mistPaint)
            }
        }
    }

    private fun drawFireflies(canvas: Canvas) {
        val bmp = haloBmp ?: return
        for (i in 0 until FLY_COUNT) {
            val b = bright[i]
            if (b <= 0.01f) continue
            val x = posX[i]
            val y = posY[i]
            if (x < -w * 0.1f || x > w * 1.1f) continue
            val size = w * (0.012f + 0.014f * depth[i])
            val hr = size * 4.5f
            haloRect.set(x - hr, y - hr, x + hr, y + hr)
            haloPaint.alpha = (255f * b * (0.55f + 0.45f * depth[i])).toInt()
            canvas.drawBitmap(bmp, null, haloRect, haloPaint)
            corePaint.alpha = (255f * min(1f, b * 1.2f)).toInt()
            canvas.drawCircle(x, y, size * 0.35f, corePaint)
        }
    }
}

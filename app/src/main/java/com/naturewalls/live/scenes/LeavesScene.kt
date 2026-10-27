package com.naturewalls.live.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Autumn leaf fall: golden-hour gradient, blurred trunks, flipping leaves, wind and gusts. */
class LeavesScene : Scene {
    override val id: String = "leaves"
    override val title: String = "Листопад"

    private companion object {
        const val N = 60
        const val G = 16
        const val TWO_PI = 6.2831855f
        const val RAD2DEG = 57.29578f
    }

    private var w = 1f
    private var h = 1f
    private var t = 0f
    private var offX = 0f
    private var gust = 0f
    private var gustDir = 1f

    private val rnd = java.util.Random(42L)

    // palette
    private val palette = intArrayOf(
        0xFFC0392B.toInt(), 0xFFD84315.toInt(), 0xFFE67E22.toInt(), 0xFFF39C12.toInt(),
        0xFFF1C40F.toInt(), 0xFFE8B923.toInt(), 0xFF8D5524.toInt(), 0xFFA0522D.toInt()
    )
    private val paletteBack = IntArray(palette.size)

    // leaf state
    private val lx = FloatArray(N)
    private val ly = FloatArray(N)
    private val lvx = FloatArray(N)
    private val lSize = FloatArray(N)
    private val lRot = FloatArray(N)
    private val lRotSpd = FloatArray(N)
    private val lFlip = FloatArray(N)
    private val lFlipSpd = FloatArray(N)
    private val lPhase = FloatArray(N)
    private val lSwayF = FloatArray(N)
    private val lDepth = FloatArray(N)
    private val lFall = FloatArray(N)
    private val lAlpha = IntArray(N)
    private val lCol = IntArray(N)
    private val lKx = FloatArray(N)
    private val lKy = FloatArray(N)

    // ground leaves
    private val gx = FloatArray(G)
    private val gy = FloatArray(G)
    private val gSize = FloatArray(G)
    private val gRot = FloatArray(G)
    private val gSx = FloatArray(G)
    private val gCol = IntArray(G)

    // shapes
    private val leafPath = Path()
    private val veinPath = Path()
    private val farTrunks = Path()
    private val nearTrunks = Path()
    private val farBranches = Path()
    private val nearBranches = Path()

    // paints
    private val bgPaint = Paint()
    private val glowPaint = Paint()
    private val groundPaint = Paint()
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.07f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF5A2E10.toInt()
    }
    private val trunkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trunkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    init {
        for (i in palette.indices) {
            val c = palette[i]
            paletteBack[i] = Color.rgb(
                (Color.red(c) * 0.68f).toInt(),
                (Color.green(c) * 0.68f).toInt(),
                (Color.blue(c) * 0.68f).toInt()
            )
        }
        leafPath.moveTo(0f, -1f)
        leafPath.cubicTo(0.75f, -0.55f, 0.65f, 0.35f, 0f, 1f)
        leafPath.cubicTo(-0.65f, 0.35f, -0.75f, -0.55f, 0f, -1f)
        leafPath.close()
        veinPath.moveTo(0f, -0.85f)
        veinPath.lineTo(0f, 1.3f)
    }

    override fun onSizeChanged(width: Int, height: Int) {
        w = if (width > 0) width.toFloat() else 1f
        h = if (height > 0) height.toFloat() else 1f

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0xFFFBE7A1.toInt(), 0xFFF2A94E.toInt(), 0xFF8A4A22.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            w * 0.7f, h * 0.22f, w * 0.95f,
            intArrayOf(0x88FFF3C0.toInt(), 0x00FFF3C0),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        groundPaint.shader = LinearGradient(
            0f, h * 0.86f, 0f, h,
            0x00301808, 0xCC301808.toInt(),
            Shader.TileMode.CLAMP
        )

        buildTrunks()

        for (i in 0 until N) {
            val d = (i + rnd.nextFloat()) / N
            lDepth[i] = d
            lSize[i] = w * 0.045f * (0.5f + 0.9f * d) * (0.75f + 0.5f * rnd.nextFloat())
            lAlpha[i] = (120 + 135 * d).toInt()
            lFall[i] = h * (0.04f + 0.06f * d) * (0.8f + 0.4f * rnd.nextFloat())
            lx[i] = rnd.nextFloat() * w
            ly[i] = rnd.nextFloat() * h
            lvx[i] = 0f
            lRot[i] = rnd.nextFloat() * 360f
            lRotSpd[i] = (rnd.nextFloat() - 0.5f) * 90f
            lFlip[i] = rnd.nextFloat() * TWO_PI
            lFlipSpd[i] = 1.2f + rnd.nextFloat() * 2.5f
            lPhase[i] = rnd.nextFloat() * TWO_PI
            lSwayF[i] = 0.7f + rnd.nextFloat() * 1.0f
            lCol[i] = rnd.nextInt(palette.size)
            lKx[i] = 0f
            lKy[i] = 0f
        }

        for (i in 0 until G) {
            gx[i] = w * (0.03f + 0.94f * (i + rnd.nextFloat()) / G)
            gy[i] = h * (0.945f + 0.045f * rnd.nextFloat())
            gSize[i] = w * 0.05f * (0.8f + 0.5f * rnd.nextFloat())
            gRot[i] = rnd.nextFloat() * 360f
            gSx[i] = 0.5f + 0.5f * rnd.nextFloat()
            gCol[i] = rnd.nextInt(palette.size)
        }
    }

    private fun buildTrunks() {
        farTrunks.reset(); nearTrunks.reset(); farBranches.reset(); nearBranches.reset()
        // far: thin, light
        addTrunk(farTrunks, farBranches, w * 0.14f, w * 0.05f, w * 0.17f, 1f)
        addTrunk(farTrunks, farBranches, w * 0.47f, w * 0.045f, w * 0.44f, -1f)
        addTrunk(farTrunks, farBranches, w * 0.80f, w * 0.055f, w * 0.83f, 1f)
        // near: thick, dark
        addTrunk(nearTrunks, nearBranches, w * 0.02f, w * 0.10f, w * 0.05f, 1f)
        addTrunk(nearTrunks, nearBranches, w * 0.64f, w * 0.085f, w * 0.60f, -1f)
        addTrunk(nearTrunks, nearBranches, w * 0.98f, w * 0.11f, w * 0.94f, -1f)
    }

    private fun addTrunk(trunk: Path, branches: Path, x: Float, wBot: Float, topX: Float, dir: Float) {
        val yB = h * 1.03f
        val yT = -h * 0.03f
        val wTop = wBot * 0.55f
        trunk.moveTo(x - wBot / 2f, yB)
        trunk.lineTo(topX - wTop / 2f, yT)
        trunk.lineTo(topX + wTop / 2f, yT)
        trunk.lineTo(x + wBot / 2f, yB)
        trunk.close()
        var f = 0.3f
        var d = dir
        while (f < 0.95f) {
            val sy = yB + (yT - yB) * f
            val sx = x + (topX - x) * f
            val len = w * (0.12f + 0.08f * (1f - f))
            val ex = sx + d * len
            val ey = sy - len * 0.7f
            branches.moveTo(sx, sy)
            branches.quadTo(sx + d * len * 0.4f, sy - len * 0.1f, ex, ey)
            d = -d
            f += 0.17f
        }
    }

    override fun update(dt: Float) {
        t += dt
        if (t > 10000f) t -= 10000f

        if (gust > 0f) {
            gust -= gust * dt * 0.8f + dt * 0.05f
            if (gust < 0.01f) gust = 0f
        }
        val baseWind = 0.6f * sin(t * 0.25f) + 0.4f * sin(t * 0.61f + 2f) + 0.25f
        val windPx = w * 0.07f * baseWind + w * 0.55f * gust * gustDir
        val smooth = min(1f, dt * 1.5f)
        val kDecay = min(1f, dt * 2f)

        for (i in 0 until N) {
            val d = lDepth[i]
            val targetVx = windPx * (0.6f + 0.8f * d) + sin(t * lSwayF[i] + lPhase[i]) * w * 0.05f
            lvx[i] += (targetVx - lvx[i]) * smooth
            val vy = lFall[i] * (1f + 0.25f * sin(t * lSwayF[i] * 1.3f + lPhase[i])) * (1f - 0.55f * gust)

            lx[i] += (lvx[i] + lKx[i]) * dt
            ly[i] += (vy + lKy[i]) * dt
            lKx[i] -= lKx[i] * kDecay
            lKy[i] -= lKy[i] * kDecay

            lRot[i] += (lRotSpd[i] + lvx[i] * 0.08f) * dt
            if (lRot[i] > 360f) lRot[i] -= 360f else if (lRot[i] < -360f) lRot[i] += 360f
            lFlip[i] += lFlipSpd[i] * (1f + 0.5f * gust) * dt
            if (lFlip[i] > TWO_PI) lFlip[i] -= TWO_PI

            val s = lSize[i]
            if (ly[i] > h + s * 1.5f) {
                ly[i] = -s * 1.5f - rnd.nextFloat() * h * 0.1f
                lx[i] = rnd.nextFloat() * w
                lKx[i] = 0f
                lKy[i] = 0f
            }
            val margin = s * 2f
            if (lx[i] < -margin) lx[i] += w + margin * 2f
            else if (lx[i] > w + margin) lx[i] -= w + margin * 2f
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // far trunks (soft: wide faint halo + body)
        canvas.save()
        canvas.translate(offX * 0.3f, 0f)
        trunkStroke.color = 0xFF7A4A24.toInt()
        trunkStroke.alpha = 26
        trunkStroke.strokeWidth = w * 0.035f
        canvas.drawPath(farTrunks, trunkStroke)
        trunkStroke.strokeWidth = w * 0.025f
        canvas.drawPath(farBranches, trunkStroke)
        trunkStroke.alpha = 50
        trunkStroke.strokeWidth = w * 0.012f
        canvas.drawPath(farBranches, trunkStroke)
        trunkFill.color = 0xFF7A4A24.toInt()
        trunkFill.alpha = 60
        canvas.drawPath(farTrunks, trunkFill)
        canvas.restore()

        // near trunks
        canvas.save()
        canvas.translate(offX * 0.6f, 0f)
        trunkStroke.color = 0xFF3E200E.toInt()
        trunkStroke.alpha = 34
        trunkStroke.strokeWidth = w * 0.05f
        canvas.drawPath(nearTrunks, trunkStroke)
        trunkStroke.strokeWidth = w * 0.035f
        canvas.drawPath(nearBranches, trunkStroke)
        trunkStroke.alpha = 70
        trunkStroke.strokeWidth = w * 0.02f
        canvas.drawPath(nearBranches, trunkStroke)
        trunkFill.color = 0xFF3E200E.toInt()
        trunkFill.alpha = 110
        canvas.drawPath(nearTrunks, trunkFill)
        canvas.restore()

        // ground shade + piled leaves
        canvas.drawRect(0f, h * 0.86f, w, h, groundPaint)
        for (i in 0 until G) {
            val s = gSize[i]
            canvas.save()
            canvas.translate(gx[i] + offX * 0.9f, gy[i])
            canvas.rotate(gRot[i])
            canvas.scale(s * gSx[i], s * 0.5f)
            leafPaint.color = paletteBack[gCol[i]]
            leafPaint.alpha = 235
            canvas.drawPath(leafPath, leafPaint)
            canvas.restore()
        }

        // falling leaves, far to near
        for (i in 0 until N) {
            val d = lDepth[i]
            val c = cos(lFlip[i])
            val s = lSize[i]
            canvas.save()
            canvas.translate(lx[i] + offX * (0.3f + 0.7f * d), ly[i])
            canvas.rotate(lRot[i])
            canvas.scale(s * c, s)
            leafPaint.color = if (c >= 0f) palette[lCol[i]] else paletteBack[lCol[i]]
            leafPaint.alpha = lAlpha[i]
            canvas.drawPath(leafPath, leafPaint)
            if (d > 0.35f) {
                veinPaint.alpha = (lAlpha[i] * 0.6f).toInt()
                canvas.drawPath(veinPath, veinPaint)
            }
            canvas.restore()
        }
    }

    override fun onOffset(xOffset: Float) {
        offX = (xOffset - 0.5f) * w * -0.08f
    }

    override fun onTouch(x: Float, y: Float) {
        gust = 1f
        gustDir = if (x < w * 0.5f) 1f else -1f
        val r = w * 0.3f
        for (i in 0 until N) {
            val dx = lx[i] - x
            val dy = ly[i] - y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < r) {
                val k = 1f - dist / r
                lKy[i] = -h * 0.12f * k
                lKx[i] = gustDir * w * 0.4f * k
                lFlipSpd[i] = min(5f, lFlipSpd[i] + abs(k) * 0.5f)
            }
        }
    }
}

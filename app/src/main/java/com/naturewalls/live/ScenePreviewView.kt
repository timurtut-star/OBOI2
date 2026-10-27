package com.naturewalls.live

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import com.naturewalls.live.scenes.Scene

/** Renders a [Scene] live, driven by Choreographer, capped to [fps]. Stops when detached. */
class ScenePreviewView(context: Context, val scene: Scene) : View(context) {

    /** Target frames per second (cap). */
    var fps: Int = 20
        set(value) { field = value.coerceIn(5, 60) }

    var cornerRadius: Float = 0f
        set(value) { field = value; updateClip() }

    private val clipPath = Path()
    private val rect = RectF()
    private var running = false
    private var sized = false
    private var lastFrameMs = 0L
    private var lastUpdateMs = 0L

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            if (now - lastFrameMs >= 1000L / fps - 2) {
                lastFrameMs = now
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun updateClip() {
        clipPath.reset()
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            scene.onSizeChanged(w, h)
            sized = true
        }
        updateClip()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scene.onVisibilityChanged(true)
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        scene.onVisibilityChanged(false)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) start() else if (visibility != VISIBLE) stop()
    }

    private fun start() {
        if (running) return
        running = true
        lastFrameMs = 0L
        lastUpdateMs = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }

    override fun onDraw(canvas: Canvas) {
        if (!sized) return
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastUpdateMs) / 1000f).coerceIn(0f, 0.05f)
        lastUpdateMs = now
        val save = canvas.save()
        canvas.clipPath(clipPath)
        scene.update(dt)
        scene.draw(canvas)
        canvas.restoreToCount(save)
    }
}

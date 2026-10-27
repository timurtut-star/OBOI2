package com.naturewalls.live.scenes

import android.graphics.Canvas

/** One animated nature scene. Pure Canvas drawing, no external libraries. */
interface Scene {
    val id: String          // stable key stored in preferences, e.g. "rain"
    val title: String       // human-readable Russian title for the picker

    /** Called when surface size changes (and once before first draw). Allocate size-dependent objects here. */
    fun onSizeChanged(width: Int, height: Int)

    /** Advance simulation. dt in seconds (already clamped to <= 0.05). */
    fun update(dt: Float)

    /** Draw full frame (including background). Do not allocate objects per frame. */
    fun draw(canvas: Canvas)

    /** Home-screen scroll offset 0..1 (parallax). Optional. */
    fun onOffset(xOffset: Float) {}

    /** Touch at screen coords. Optional. */
    fun onTouch(x: Float, y: Float) {}

    /** Wallpaper became visible / hidden. Optional. */
    fun onVisibilityChanged(visible: Boolean) {}
}

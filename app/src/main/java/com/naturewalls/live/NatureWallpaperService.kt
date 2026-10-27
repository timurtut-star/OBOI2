package com.naturewalls.live

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.naturewalls.live.scenes.Scene
import com.naturewalls.live.scenes.SceneRegistry

class NatureWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = NatureEngine()

    inner class NatureEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val handler = Handler(Looper.getMainLooper())
        private val prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        private var scene: Scene = SceneRegistry.create(Prefs.sceneId(this@NatureWallpaperService))
        private var width = 0
        private var height = 0
        private var visible = false
        private var lastTime = 0L
        private var frameMs = 33L
        private var parallax = true

        private val tick = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, frameMs)
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(true)
            prefs.registerOnSharedPreferenceChangeListener(this)
            applySettings()
        }

        override fun onDestroy() {
            handler.removeCallbacks(tick)
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
            if (key == Prefs.KEY_SCENE) {
                scene = SceneRegistry.create(Prefs.sceneId(this@NatureWallpaperService))
                if (width > 0) scene.onSizeChanged(width, height)
                scene.onVisibilityChanged(visible)
            }
            applySettings()
        }

        private fun applySettings() {
            val fps = Prefs.fps(this@NatureWallpaperService).coerceIn(15, 60)
            frameMs = (1000L / fps)
            parallax = Prefs.parallax(this@NatureWallpaperService)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h
            scene.onSizeChanged(w, h)
            drawFrame()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            scene.onVisibilityChanged(v)
            handler.removeCallbacks(tick)
            if (v) {
                lastTime = SystemClock.uptimeMillis()
                handler.post(tick)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(tick)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int
        ) {
            if (parallax) scene.onOffset(xOffset)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                scene.onTouch(event.x, event.y)
            }
        }

        private fun drawFrame() {
            if (width == 0) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTime) / 1000f).coerceIn(0f, 0.05f)
            lastTime = now
            val holder = surfaceHolder
            val canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try {
                scene.update(dt)
                scene.draw(canvas)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }
    }
}

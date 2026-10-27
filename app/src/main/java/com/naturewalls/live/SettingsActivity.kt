package com.naturewalls.live

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.naturewalls.live.scenes.Scene
import com.naturewalls.live.scenes.SceneRegistry

class SettingsActivity : Activity() {

    private class Card(val root: LinearLayout, val preview: ScenePreviewView, val scene: Scene)

    private val bg = Color.parseColor("#0E1217")
    private val surface = Color.parseColor("#1A212A")
    private val accent = Color.parseColor("#4CD6A0")
    private val textMain = Color.parseColor("#EAF1F7")
    private val textDim = Color.parseColor("#8A98A8")

    private val cards = ArrayList<Card>()
    private var selectedId: String = SceneRegistry.DEFAULT_ID
    private lateinit var fps30: TextView
    private lateinit var fps60: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Int): Float = v * resources.displayMetrics.density

    private fun prefs() = getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        selectedId = Prefs.sceneId(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        scroll.isFillViewport = true
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        val basePad = dp(16)
        content.setPadding(basePad, basePad, basePad, basePad)
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        scroll.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            content.setPadding(
                basePad + insets.systemWindowInsetLeft,
                basePad + insets.systemWindowInsetTop,
                basePad + insets.systemWindowInsetRight,
                basePad + insets.systemWindowInsetBottom
            )
            insets
        }

        // Title
        content.addView(TextView(this).apply {
            text = "Живые обои: Природа"
            setTextColor(textMain)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Выберите сцену"
            setTextColor(textDim)
            textSize = 14f
            setPadding(dp(4), 0, dp(4), dp(12))
        })

        // Grid of scene cards, 2 columns
        val scenes = SceneRegistry.all()
        var i = 0
        while (i < scenes.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val idx = i + j
                if (idx < scenes.size) {
                    val card = makeCard(scenes[idx])
                    cards.add(card)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(dp(5), dp(5), dp(5), dp(5))
                    row.addView(card.root, lp)
                } else {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                        setMargins(dp(5), dp(5), dp(5), dp(5))
                    })
                }
            }
            content.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            i += 2
        }
        updateSelection()

        // Settings panel
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = rounded(surface, dpf(16), 0, 0)
        panel.setPadding(dp(16), dp(12), dp(16), dp(16))
        val panelLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        panelLp.setMargins(dp(5), dp(16), dp(5), dp(0))
        content.addView(panel, panelLp)

        val sw = Switch(this)
        sw.text = "Параллакс при прокрутке"
        sw.setTextColor(textMain)
        sw.textSize = 16f
        sw.isChecked = Prefs.parallax(this)
        sw.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accent, Color.parseColor("#B0BAC5"))
        )
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.parseColor("#2E8F6D"), Color.parseColor("#3A4551"))
        )
        sw.setPadding(0, dp(8), 0, dp(8))
        sw.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(Prefs.KEY_PARALLAX, checked).apply()
        }
        panel.addView(sw, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        panel.addView(TextView(this).apply {
            text = "Частота кадров"
            setTextColor(textMain)
            textSize = 16f
            setPadding(0, dp(12), 0, dp(8))
        })
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        fps30 = segButton("30 FPS") { setFps(30) }
        fps60 = segButton("60 FPS") { setFps(60) }
        seg.addView(fps30, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        seg.addView(fps60, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        panel.addView(seg, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        updateFpsUi(Prefs.fps(this))

        // Install button
        val btn = TextView(this)
        btn.text = "Установить как обои"
        btn.setTextColor(Color.parseColor("#06251B"))
        btn.textSize = 18f
        btn.typeface = Typeface.DEFAULT_BOLD
        btn.gravity = Gravity.CENTER
        btn.background = rounded(accent, dpf(28), 0, 0)
        btn.isClickable = true
        btn.setOnClickListener { installWallpaper() }
        content.addView(btn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply {
            setMargins(dp(5), dp(20), dp(5), dp(8))
        })

        setContentView(scroll)
    }

    private fun rounded(color: Int, radius: Float, strokeW: Int, strokeColor: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radius
        d.setColor(color)
        if (strokeW > 0) d.setStroke(strokeW, strokeColor)
        return d
    }

    private fun makeCard(scene: Scene): Card {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(3), dp(3), dp(3), dp(3))

        val preview = ScenePreviewView(this, scene)
        preview.cornerRadius = dpf(13)
        preview.fps = 20
        root.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(210)))

        val title = TextView(this)
        title.text = scene.title
        title.setTextColor(textMain)
        title.textSize = 15f
        title.gravity = Gravity.CENTER
        title.setPadding(dp(4), dp(8), dp(4), dp(8))
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.isClickable = true
        root.setOnClickListener {
            selectedId = scene.id
            prefs().edit().putString(Prefs.KEY_SCENE, scene.id).apply()
            updateSelection()
        }
        return Card(root, preview, scene)
    }

    private fun updateSelection() {
        for (c in cards) {
            val sel = c.scene.id == selectedId
            c.root.background = if (sel) rounded(surface, dpf(16), dp(3), accent)
            else rounded(surface, dpf(16), dp(1), Color.parseColor("#2A333E"))
            c.preview.fps = if (sel) 30 else 20
        }
    }

    private fun segButton(label: String, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.textSize = 15f
        t.typeface = Typeface.DEFAULT_BOLD
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun setFps(v: Int) {
        prefs().edit().putInt(Prefs.KEY_FPS, v).apply()
        updateFpsUi(v)
    }

    private fun updateFpsUi(v: Int) {
        val on = rounded(accent, dpf(12), 0, 0)
        val off = rounded(Color.parseColor("#2A333E"), dpf(12), 0, 0)
        fps30.background = if (v == 30) on else off
        fps60.background = if (v == 60) on else off
        fps30.setTextColor(if (v == 30) Color.parseColor("#06251B") else textDim)
        fps60.setTextColor(if (v == 60) Color.parseColor("#06251B") else textDim)
    }

    private fun installWallpaper() {
        try {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            i.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, NatureWallpaperService::class.java)
            )
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                Toast.makeText(this, "Не удалось открыть выбор обоев", Toast.LENGTH_LONG).show()
            }
        }
    }
}

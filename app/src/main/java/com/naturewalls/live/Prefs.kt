package com.naturewalls.live

import android.content.Context
import com.naturewalls.live.scenes.SceneRegistry

object Prefs {
    const val FILE = "naturewalls"
    const val KEY_SCENE = "scene_id"
    const val KEY_FPS = "fps"          // Int: 30 or 60
    const val KEY_PARALLAX = "parallax" // Boolean

    fun sceneId(c: Context): String =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_SCENE, SceneRegistry.DEFAULT_ID)!!

    fun fps(c: Context): Int =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_FPS, 30)

    fun parallax(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_PARALLAX, true)
}

package com.naturewalls.live.scenes

object SceneRegistry {
    const val DEFAULT_ID = "rain"

    /** Factories in picker order. Each scene class lives in its own file in this package. */
    val factories: List<() -> Scene> = listOf(
        { RainScene() },
        { SnowScene() },
        { OceanScene() },
        { CampfireScene() },
        { LeavesScene() },
        { AuroraScene() },
        { FirefliesScene() },
        { SkyScene() },
    )

    fun create(id: String?): Scene {
        val all = factories.map { it() }
        return all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT_ID }
    }

    fun all(): List<Scene> = factories.map { it() }
}

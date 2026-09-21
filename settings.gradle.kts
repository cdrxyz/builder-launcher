pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Plugin Portal flakes 404 Paparazzi 1.3.5; the same plugin lives on Maven Central
    // as paparazzi-gradle-plugin (not the plugin-marker artifact).
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "app.cash.paparazzi") {
                val version = requested.version
                    ?: error("app.cash.paparazzi plugin version is required")
                useModule("app.cash.paparazzi:paparazzi-gradle-plugin:$version")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "builder-launcher"
include(":app")

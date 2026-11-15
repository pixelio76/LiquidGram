pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LiquidGram"
include(":app")
// Glasmio, vendored so its glass follows the app's blur and opacity settings everywhere.
listOf("core", "glass", "ui", "icons").forEach { m ->
    include(":glasmio-$m")
    project(":glasmio-$m").projectDir = file("glasmio/glasmio-$m")
}

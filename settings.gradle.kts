pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.5.1"
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "calendar-widgets"

// :core is plain Kotlin (no Android SDK needed), so its tests run anywhere.
include(":core")

// Set CORE_ONLY=1 to work on :core on a machine without the Android SDK.
if (System.getenv("CORE_ONLY") == null) {
    include(":app")
}

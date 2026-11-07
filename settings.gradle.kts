pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // v3.0: FreeReflection (com.github.tiann) is published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "CloneX"
include(":app")

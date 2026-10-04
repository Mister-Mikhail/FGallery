pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.dburckh"); includeGroup("com.github.dburckh.AndroidLibRaw") } }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.dburckh"); includeGroup("com.github.dburckh.AndroidLibRaw") } }
    }
}
rootProject.name = "FGallery"
include(":app")


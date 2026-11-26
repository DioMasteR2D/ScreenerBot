pluginManagement {
    repositories {
        google()
        // Use Google's Maven Central mirror first to avoid transient Maven Central HTTP 429 throttling.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Use the same mirror for transitive Android/Kotlin dependencies.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}
rootProject.name = "ScreenerBotAndroid"
include(":app")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // <--- এই লাইনটি Kotlin Compose প্লাগইন খুঁজে পেতে সাহায্য করবে
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CodeStudio"
include(":app")
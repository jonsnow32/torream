pluginManagement {
  repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
    // IronSource repository
    maven("https://android-sdk.is.com/")

  }
}

rootProject.name = "ZippyPlayer"
include(":app")

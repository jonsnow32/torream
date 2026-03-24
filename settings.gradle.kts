pluginManagement {
  repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
    gradlePluginPortal()
  }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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

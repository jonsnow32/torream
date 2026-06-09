// Script to build all release flavors
// Usage: ./gradlew -q -b build-all-releases.gradle.kts buildAllReleases

tasks.register("buildAllReleases") {
    group = "build"
    description = "Build release APKs for all product flavors"

    doLast {
        val flavors = listOf("arm64", "arm32", "x86", "x86_64", "universal")

        println("================================================")
        println("  Torream - Build All Release Flavors")
        println("================================================")
        println()

        // Clean first
        println("Cleaning previous builds...")
        exec {
            commandLine("./gradlew", "clean")
        }
        println("✓ Clean completed")
        println()

        val buildSuccess = mutableListOf<String>()
        val buildFailed = mutableListOf<String>()

        flavors.forEach { flavor ->
            println("================================================")
            println("Building ${flavor}Release...")
            println("================================================")

            try {
                exec {
                    commandLine("./gradlew", "assemble${flavor.replaceFirstChar { it.uppercase() }}Release")
                }
                println("✓ ${flavor}Release build succeeded")
                buildSuccess.add(flavor)
            } catch (e: Exception) {
                println("✗ ${flavor}Release build failed: ${e.message}")
                buildFailed.add(flavor)
            }
            println()
        }

        // Summary
        println("================================================")
        println("  Build Summary")
        println("================================================")
        println()

        if (buildSuccess.isNotEmpty()) {
            println("Successful builds (${buildSuccess.size}):")
            buildSuccess.forEach { println("  ✓ $it") }
            println()
        }

        if (buildFailed.isNotEmpty()) {
            println("Failed builds (${buildFailed.size}):")
            buildFailed.forEach { println("  ✗ $it") }
            println()
        }

        // Show APK locations
        println("================================================")
        println("  APK Locations")
        println("================================================")
        println()

        buildSuccess.forEach { flavor ->
            val apkDir = file("app/build/outputs/apk/$flavor/release")
            if (apkDir.exists()) {
                println("$flavor:")
                apkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
                    println("  ${apk.absolutePath}")
                    println("  Size: ${apk.length() / (1024 * 1024)} MB")
                }
                println()
            }
        }

        if (buildFailed.isNotEmpty()) {
            throw GradleException("Some builds failed. Please check the logs above.")
        } else {
            println("All builds completed successfully!")
        }
    }
}

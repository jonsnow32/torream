import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("kotlin-parcelize")
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
}

val abiCodes = mapOf(
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4
)

android {
  namespace = "cloud.streamless.torream"
  compileSdk = 36

  lint {
    baseline = file("lint-baseline.xml")
    abortOnError = true
    checkReleaseBuilds = false
  }

  // Limit packaged resources to these locales
  androidResources {
    localeFilters.addAll(listOf("en", "es", "zh"))
  }

  buildFeatures {
    viewBinding = true
  }


  defaultConfig {
    applicationId = "cloud.streamless.torream"
    minSdk = 23
    targetSdk = 36
    versionCode = 111
    versionName = "1.0.11"


    // Reads local.properties
    val localProperties = gradleLocalProperties(rootDir, providers)

    buildConfigField(
      "long",
      "BUILD_DATE",
      "${System.currentTimeMillis()}"
    )
    buildConfigField(
      "String",
      "TEST_ID",
      "\"" + localProperties["test.id"] + "\""
    )
    buildConfigField(
      "String",
      "OPENSUBTITLES_API_KEY",
      "\"" + (localProperties["opensubtitles.api_key"] ?: System.getenv("OPENSUBTITLES_API_KEY") ?: "") + "\""
    )
    buildConfigField(
      "String",
      "SUBDL_API_KEY",
      "\"" + (localProperties["subdl.api_key"] ?: System.getenv("SUBDL_API_KEY") ?: "") + "\""
    )

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Support for 16 KB page sizes - will be set per flavor
    ndk.debugSymbolLevel = "FULL"
  }

  packaging {
    jniLibs {
      useLegacyPackaging = false
      // Keep debug symbols for better crash reports
      keepDebugSymbols += listOf("**/*.so")
    }
  }


  buildFeatures {
    buildConfig = true
  }

  sourceSets {
    getByName("main") {
      jniLibs.srcDir("src/main/jni") // This is not necessary unless you have precompiled libraries in your project.
      jniLibs.srcDirs("src/main/libs")
    }
  }


  flavorDimensions += listOf("abi")
  productFlavors {
    create("arm64") {
      dimension = "abi"
      versionNameSuffix = "-arm64"
      ndk {
        abiFilters.clear()
        abiFilters.addAll(listOf("arm64-v8a"))
      }
      versionCode = (abiCodes["arm64-v8a"] ?: 2) + 1000
    }
    create("arm32") {
      dimension = "abi"
      versionNameSuffix = "-arm32"
      ndk {
        abiFilters.clear()
        abiFilters.addAll(listOf("armeabi-v7a"))
      }
      versionCode = (abiCodes["armeabi-v7a"] ?: 1) + 1000
    }
    create("x86") {
      dimension = "abi"
      versionNameSuffix = "-x86"
      ndk {
        abiFilters.clear()
        abiFilters.addAll(listOf("x86"))
      }
      versionCode = (abiCodes["x86"] ?: 3) + 1000
    }
    create("x86_64") {
      dimension = "abi"
      versionNameSuffix = "-x86_64"
      ndk {
        abiFilters.clear()
        abiFilters.addAll(listOf("x86_64"))
      }
      versionCode = (abiCodes["x86_64"] ?: 4) + 1000
    }
    create("universal") {
      dimension = "abi"
      ndk {
        // Include all ABIs for Play Store AAB upload
        abiFilters.clear()
        abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
      }
      // No versionCode/versionNameSuffix override — inherits base values for Play Store
    }
  }

  buildTypes {
    release {
      isDebuggable = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      // Upload native (MPV/.so) debug symbols so Crashlytics can symbolicate NDK crashes
      configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
        nativeSymbolUploadEnabled = true
      }
    }
    debug {
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "Torream.")
      isMinifyEnabled = false
      isShrinkResources = false
      ndk {
        abiFilters += listOf("arm64-v8a")
      }
      // Upload native (MPV/.so) debug symbols so Crashlytics can symbolicate NDK crashes
      configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
        nativeSymbolUploadEnabled = true
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  kotlinOptions {
    jvmTarget = "21"
  }
}

dependencies {

  // Needed for EdgeToEdge helper
  implementation("androidx.activity:activity:1.12.2")
  implementation("androidx.activity:activity-ktx:1.12.2")

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("com.google.android.material:material:1.13.0")
  implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
  implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
  implementation("androidx.navigation:navigation-fragment-ktx:2.9.6")
  implementation("androidx.navigation:navigation-ui-ktx:2.9.6")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

  // Paging 3
  implementation("androidx.paging:paging-runtime-ktx:3.3.6")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
  implementation("androidx.documentfile:documentfile:1.1.0")

  // WorkManager for background downloads
  implementation("androidx.work:work-runtime-ktx:2.10.0")
  implementation("androidx.hilt:hilt-work:1.2.0")
  ksp("androidx.hilt:hilt-compiler:1.2.0")

  // Coil - Image loading library (optimized for video thumbnails)
  implementation("io.coil-kt:coil:2.7.0")
  implementation("io.coil-kt:coil-video:2.7.0")

  // Room
  implementation("androidx.room:room-runtime:2.8.3")
  implementation("androidx.room:room-ktx:2.8.3")
  ksp("androidx.room:room-compiler:2.8.3")

  val media3Version = "1.9.0"
  // Media 3 (ExoPlayer)
  implementation("androidx.media3:media3-ui:$media3Version")
  implementation("androidx.media3:media3-cast:$media3Version")
  implementation("androidx.media3:media3-common:$media3Version")
  implementation("androidx.media3:media3-session:$media3Version")

  // Chromecast
  implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
  implementation("androidx.mediarouter:mediarouter:1.7.0")

  // Play In-App Updates (for Play Store installs)
  implementation("com.google.android.play:app-update-ktx:2.1.0")
  // Play In-App Review
  implementation("com.google.android.play:review-ktx:2.0.2")
//  implementation("androidx.media3:media3-exoplayer:$media3Version")
//  implementation("com.google.android.mediahome:video:1.0.0")
//  implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
//  implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
//  implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
//  implementation("androidx.media3:media3-extractor:$media3Version")
//  implementation("com.github.recloudstream:media-ffmpeg:1.1.0")

  // UI Stuff
  implementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0")
  implementation("me.zhanghai.android.fastscroll:library:1.3.0")

  // Downloading & Networking
  implementation("com.github.Blatzar:NiceHttp:0.4.13")

  // Encrypted storage for user-entered AI provider API keys
  // NOTE: version unverified in this sandbox (no network access to dl.google.com) — confirm the
  // latest available androidx.security:security-crypto version when building locally.
  implementation("androidx.security:security-crypto:1.1.0-alpha06")

  // Network share browsing (SMB/FTP) - pure Java/Kotlin, no native code
  implementation("com.hierynomus:smbj:0.14.0")
  implementation("commons-net:commons-net:3.11.1")

  // File handling
  implementation("com.github.LagradOst:SafeFile:0.0.8")

  // Torrent Support
  implementation("org.libtorrent4j:libtorrent4j:2.1.0-38")
  implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-38")
  implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-38")
  implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-38")
  implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-38")

  // NanoHTTPD for torrent streaming
  implementation("org.nanohttpd:nanohttpd:2.3.1")

  // Others
  implementation("org.mozilla:rhino:1.8.0")
  implementation("com.github.albfernandez:juniversalchardet:2.5.0")

  //Dependence injection
  implementation("com.google.dagger:hilt-android:2.57.2")
  ksp("com.google.dagger:hilt-android-compiler:2.57.2")

  // Testing
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

  //Ads
  implementation("com.google.android.gms:play-services-ads:24.9.0")
  implementation("com.ironsource.sdk:mediationsdk:9.2.0")
  implementation("com.applovin:applovin-sdk:13.5.1")
  implementation("com.unity3d.ads:unity-ads:4.16.5")
  implementation("com.vungle:vungle-ads:7.6.3")


  //logging
  implementation("com.jakewharton.timber:timber:5.0.1")

  // Firebase Crashlytics (JVM + native/NDK crash reporting) + Analytics
  implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
  implementation("com.google.firebase:firebase-crashlytics")
  implementation("com.google.firebase:firebase-crashlytics-ndk")
  implementation("com.google.firebase:firebase-analytics")
}

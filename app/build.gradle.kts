import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  id("org.jetbrains.kotlin.plugin.serialization")
}

val abiCodes = mapOf(
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4
)


android {
  namespace = "cloud.app.csplayer"
  compileSdk = 36

  buildFeatures {
    viewBinding = true
  }


  defaultConfig {
    applicationId = "cloud.app.csplayer"
    minSdk = 23
    targetSdk = 36
    versionCode = 117
    versionName = "1.1.7"

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

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Support for 16 KB page sizes
    ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
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


  flavorDimensions += listOf("default")
  productFlavors {
    create("default") {
      isDefault = true
    }
    create("api29") {
      targetSdk = 34
      versionNameSuffix = "-oldapi"
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
    }
    debug {
      isDebuggable = true
      applicationIdSuffix = ".debug"
      resValue("string", "app_name", "HexaPlayer-Debug")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
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
  implementation("androidx.activity:activity:1.11.0")
  implementation("androidx.activity:activity-ktx:1.11.0")

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("com.google.android.material:material:1.13.0")
  implementation("androidx.navigation:navigation-ui-ktx:2.9.5")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
  implementation("androidx.navigation:navigation-fragment-ktx:2.9.5")
  implementation("androidx.navigation:navigation-fragment-ktx:2.9.5")
  implementation("androidx.navigation:navigation-ui-ktx:2.9.5")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

  // Paging 3
  implementation("androidx.paging:paging-runtime-ktx:3.3.6")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("androidx.documentfile:documentfile:1.1.0")

  // Coil - Image loading library (optimized for video thumbnails)
  implementation("io.coil-kt:coil:2.7.0")
  implementation("io.coil-kt:coil-video:2.7.0")

  // Room
  implementation("androidx.room:room-runtime:2.8.3")
  implementation("androidx.room:room-ktx:2.8.3")
  ksp("androidx.room:room-compiler:2.8.3")

  val media3Version = "1.8.0"
  // Media 3 (ExoPlayer)
  implementation("androidx.media3:media3-ui:$media3Version")
  implementation("androidx.media3:media3-cast:$media3Version")
  implementation("androidx.media3:media3-common:$media3Version")
  implementation("androidx.media3:media3-session:$media3Version")
  implementation("androidx.media3:media3-exoplayer:$media3Version")
  implementation("com.google.android.mediahome:video:1.0.0")
  implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
  implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
  implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
//  implementation("androidx.media3:media3-extractor:$media3Version")
//  implementation("com.github.recloudstream:media-ffmpeg:1.1.0")

  // UI Stuff
  implementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0")

  // Downloading & Networking
  implementation("com.github.Blatzar:NiceHttp:0.4.13")

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
  implementation("com.google.android.gms:play-services-ads:24.7.0")
  implementation("com.ironsource.sdk:mediationsdk:9.0.0")
  implementation("com.applovin:applovin-sdk:13.5.0")
  implementation("com.unity3d.ads:unity-ads:4.16.3")
  implementation("com.vungle:vungle-ads:7.6.0")


  //logging
  implementation("com.jakewharton.timber:timber:5.0.1")
}

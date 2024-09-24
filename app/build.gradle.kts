import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
}

val abiCodes = mapOf(
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4
)


android {
  namespace = "cloud.app.csplayer"
  compileSdk = 34

  buildFeatures {
    viewBinding = true
  }


  defaultConfig {
    applicationId = "cloud.app.csplayer"
    minSdk = 21
    targetSdk = 33
    versionCode = 105
    versionName = "1.0.5"

    // Reads local.properties
    val localProperties = gradleLocalProperties(rootDir)

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
      targetSdk = 29
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
      resValue("string", "app_name", "CSPlayer-Debug")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }

  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
    freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
  }
}

dependencies {

  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.navigation:navigation-ui-ktx:2.8.1")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
  implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.6")
  implementation("androidx.navigation:navigation-fragment-ktx:2.8.1")

  val media3Version = "1.4.1"
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
  implementation("androidx.media3:media3-extractor:$media3Version")
  implementation("com.github.recloudstream:media-ffmpeg:1.1.0")

  // UI Stuff
  implementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

  // Downloading & Networking
  implementation("com.github.Blatzar:NiceHttp:0.4.11")

  // Others
  implementation("org.mozilla:rhino:1.7.15")
  implementation("com.github.albfernandez:juniversalchardet:2.5.0")
  implementation("org.conscrypt:conscrypt-android:2.5.2")

  //Dependence injection
  implementation("com.google.dagger:hilt-android:2.48.1")
  ksp("com.google.dagger:hilt-android-compiler:2.48.1")

  // Testing
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}


import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

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
    versionCode = 1
    versionName = "1.0"

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
  buildTypes {
    release {
      isDebuggable = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    debug {
      isDebuggable = true
      applicationIdSuffix = ".debug"
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
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
  implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")

  val media3_version = "1.1.1"
  // Media 3 (ExoPlayer)
  implementation("androidx.media3:media3-ui:$media3_version")
  implementation("androidx.media3:media3-cast:$media3_version")
  implementation("androidx.media3:media3-common:$media3_version")
  implementation("androidx.media3:media3-session:$media3_version")
  implementation("androidx.media3:media3-exoplayer:$media3_version")
  implementation("com.google.android.mediahome:video:1.0.0")
  implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
  implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
  implementation("androidx.media3:media3-datasource-okhttp:$media3_version")
  implementation("androidx.media3:media3-extractor:$media3_version")


  // UI Stuff
  implementation("com.github.rubensousa:previewseekbar-media3:1.1.1.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
  implementation("com.jaredrummler:colorpicker:1.1.0")

  // Downloading & Networking
  implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

  // Others
  implementation("org.mozilla:rhino:1.7.13")
  implementation("com.github.albfernandez:juniversalchardet:2.4.0")
  implementation("org.conscrypt:conscrypt-android:2.5.2")
  // Testing
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}



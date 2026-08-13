# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ================================================================================================
# GENERAL SETTINGS
# ================================================================================================

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ================================================================================================
# KOTLIN
# ================================================================================================

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ================================================================================================
# KOTLINX SERIALIZATION
# ================================================================================================

# Keep @Serializable classes
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *;
}

-keep,includedescriptorclasses class cloud.streamless.torream.model.**$$serializer { *; }
-keep,includedescriptorclasses class cloud.streamless.torream.datastore.**$$serializer { *; }
-keep,includedescriptorclasses class cloud.streamless.torream.utils.**$$serializer { *; }

-keepclassmembers class cloud.streamless.torream.model.** {
    *** Companion;
}
-keepclassmembers class cloud.streamless.torream.datastore.** {
    *** Companion;
}

-keepclasseswithmembers class cloud.streamless.torream.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class cloud.streamless.torream.datastore.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ================================================================================================
# PARCELIZE
# ================================================================================================

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ================================================================================================
# MPV LIBRARY (JNI/NATIVE)
# ================================================================================================

# Keep all the methods and fields of MPVLib since it's used via JNI
-keep class cloud.streamless.torream.ui.player.mpv.MPVLib {
    *;
}

# Keep inner static classes for constants
-keep class cloud.streamless.torream.ui.player.mpv.MPVLib$* { *; }

# Keep EventObserver and LogObserver interfaces and their methods
-keep interface cloud.streamless.torream.ui.player.mpv.MPVLib$EventObserver { *; }
-keep interface cloud.streamless.torream.ui.player.mpv.MPVLib$LogObserver { *; }

# Keep all native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ================================================================================================
# ROOM DATABASE
# ================================================================================================

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** DATABASE;
}

-keep class cloud.streamless.torream.media.entities.** { *; }
-keep class cloud.streamless.torream.media.dao.** { *; }
-keep class cloud.streamless.torream.media.db.** { *; }

# ================================================================================================
# HILT / DAGGER
# ================================================================================================

-keepclassmembers,allowobfuscation class * {
    @javax.inject.* *;
    @dagger.* *;
    <init>();
}

-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent

-keep class **_HiltModules$*
-keep class **_HiltModules_*
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# Keep only Hilt generated components
-keep class dagger.hilt.internal.** { *; }
-keep class dagger.hilt.android.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManager

-keepclasseswithmembers class * {
    @dagger.* <fields>;
}

-keepclasseswithmembers class * {
    @dagger.* <methods>;
}

# Keep Hilt ViewModels
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ================================================================================================
# MEDIA3 (EXOPLAYER)
# Note: Broad rules are required for Media3 due to reflection and dynamic loading
# ================================================================================================

# Keep Media3 essential classes
-keep class androidx.media3.common.Player { *; }
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.common.PlaybackException { *; }
-keep class androidx.media3.session.MediaSession { *; }
-keep class androidx.media3.session.MediaController { *; }
-keep class androidx.media3.ui.PlayerView { *; }

# Keep all classes in these packages (broad rules needed for reflection)
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.cast.** { *; }
-keep class androidx.media3.extractor.** { *; }

# Keep Media3 metadata and data classes
-keepclassmembers class * implements androidx.media3.common.Player {
    <methods>;
}

-dontwarn androidx.media3.**

# ================================================================================================
# COIL IMAGE LOADING
# Note: Broad rules required for Coil's reflection-based image loading
# ================================================================================================

-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**

# ================================================================================================
# NETWORKING - OKHTTP & RETROFIT
# Note: Broad rules needed for reflection and dynamic proxy
# ================================================================================================

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ================================================================================================
# TORRENT SUPPORT - LIBTORRENT4J
# Note: Broad rules required for JNI and native library interaction
# ================================================================================================

-keep class org.libtorrent4j.** { *; }
-keepclassmembers class org.libtorrent4j.** { *; }
-dontwarn org.libtorrent4j.**

# ================================================================================================
# SMBJ - transitive deps reference optional javax.el (mbassador event bus) and
# org.ietf.jgss (Kerberos auth) which don't exist on Android and aren't used
# ================================================================================================

-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**

# ================================================================================================
# JSON PARSING
# ================================================================================================

# Keep org.json classes (used by many SDKs including Google Ads)
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }
-keepnames class org.json.** { *; }

# ================================================================================================
# GOOGLE PLAY SERVICES & ADS
# Note: Broad rules required for ad SDK functionality
# ================================================================================================

# Google Ads - Keep internal utility classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-keepclassmembers class com.google.android.gms.ads.** { *; }

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep AdMob specific classes
-keep public class com.google.android.gms.ads.** {
    public *;
}

# Keep identifiers
-keep class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

# Keep names for AdMob
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
    @com.google.android.gms.common.annotation.KeepName *;
}

# ================================================================================================
# AD NETWORKS
# ================================================================================================

# IronSource
-keepclassmembers class com.ironsource.** { public *; }
-keep class com.ironsource.adapters.** { *; }
-keep class com.ironsource.mediationsdk.** { *; }
-keep class com.ironsource.sdk.** { *; }
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn com.ironsource.**

# AppLovin
-keep class com.applovin.** { *; }
-keep class com.applovin.sdk.** { *; }
-keep class com.applovin.impl.** { *; }
-keepclassmembers class com.applovin.sdk.** { *; }
-keep public class * extends com.applovin.sdk.AppLovinSdk
-dontwarn com.applovin.**

# Unity Ads
-keep class com.unity3d.** { *; }
-keepattributes SourceFile,LineNumberTable
-keepattributes JavascriptInterface
-keep class android.webkit.JavascriptInterface { *; }
-keep class com.unity3d.services.** { *; }
-keep class com.unity3d.ads.** { *; }
-keepclassmembers class com.unity3d.ads.** {
    public *;
}
-dontwarn com.unity3d.**

# Vungle
-keep class com.vungle.** { *; }
-keepclassmembers class com.vungle.** { *; }
-keep class com.vungle.ads.** { *; }
-keep class com.vungle.ads.internal.** { *; }
-dontwarn com.vungle.**
-dontwarn com.moat.**

# ================================================================================================
# TIMBER LOGGING
# ================================================================================================

-keep class timber.log.** { *; }
-dontwarn timber.log.**

# Remove debug and verbose logging in release builds
-assumenosideeffects class timber.log.Timber* {
    public static *** d(...);
    public static *** v(...);
}

# Remove Android Log statements in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# ================================================================================================
# RHINO (JavaScript Engine)
# ================================================================================================

-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**

# ================================================================================================
# DATA MODELS
# ================================================================================================

# Keep all model classes
-keep class cloud.streamless.torream.model.** { *; }
-keep class cloud.streamless.torream.datastore.** { *; }

# Keep data classes
-keepclassmembers class cloud.streamless.torream.model.** {
    *;
}

# ================================================================================================
# APPLICATION CLASSES
# ================================================================================================

# Keep application class
-keep class cloud.streamless.torream.CSApplication { *; }
-keep class cloud.streamless.torream.MainActivity { *; }
-keep class cloud.streamless.torream.ControllerActivity { *; }

# ================================================================================================
# MISC LIBRARIES
# ================================================================================================

# Keep Facebook Infer annotations
-keep class com.facebook.infer.annotation.** { *; }

# Juniversalchardet
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**

# PreviewSeekBar
-keep class com.github.rubensousa.previewseekbar.** { *; }
-dontwarn com.github.rubensousa.previewseekbar.**

# FastScroll
-keep class me.zhanghai.android.fastscroll.** { *; }

# ================================================================================================
# GENERIC ANDROID RULES
# ================================================================================================

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep Fragment constructors
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>(...);
}

# Keep ViewModel constructors for ViewModelProvider
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ================================================================================================
# OPTIMIZATION FLAGS
# ================================================================================================

# Enable optimization
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Don't warn about missing classes
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe

